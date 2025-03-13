package com.sap.cds.sdm.service;

import java.io.*;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.util.concurrent.*;

public class ReadAheadInputStream extends InputStream {
  private final BufferedInputStream originalStream;
  private final long totalSize;
  private final int chunkSize = 100 * 1024 * 1024; //  100MB Chunk Size
  private long totalBytesRead = 0;
  private boolean lastChunkLoaded = false;
  private byte[] currentBuffer;
  private long currentBufferSize = 0;
  private long position = 0;
  private MemoryMXBean memoryMXBean;

  private final ExecutorService executor =
      Executors.newCachedThreadPool(); // Thread pool to Read next chunk
  private final BlockingQueue<byte[]> chunkQueue =
      new LinkedBlockingQueue<>(3); // Next chunk is read to a queue

  public ReadAheadInputStream(InputStream inputStream, long totalSize) throws IOException {
    if (inputStream == null) {
      throw new IllegalArgumentException(" InputStream cannot be null");
    }

    this.originalStream = new BufferedInputStream(inputStream, chunkSize);
    this.totalSize = totalSize;
    this.currentBuffer = new byte[chunkSize];

    System.out.println(" Initializing ReadAheadInputStream..."); // Once per one file upload
    preloadChunks(); // preload one chunk
    loadNextChunk(); // Ensure first chunk is available
  }

  public boolean isChunkQueueEmpty() {
    return this.chunkQueue.isEmpty();
  }

  private void preloadChunks() {
    executor.submit(
        () -> {
          try {
            while (totalBytesRead < totalSize) {
              byte[] buffer = new byte[chunkSize];
              long bytesRead = 0;
              int readAttempt;

              //  Keep reading until full chunk is read until EOF
              while (bytesRead < chunkSize
                  && (readAttempt =
                          originalStream.read(buffer, (int) bytesRead, chunkSize - (int) bytesRead))
                      > 0) {
                bytesRead += readAttempt;
              }

              //  Ensure any data read is processed
              if (bytesRead > 0) {
                totalBytesRead += bytesRead;

                //  Trim buffer if last chunk is smaller
                if (bytesRead < chunkSize) {
                  byte[] trimmedBuffer = new byte[(int) bytesRead];
                  System.arraycopy(buffer, 0, trimmedBuffer, 0, (int) bytesRead);
                  buffer = trimmedBuffer;
                }

                //  Ensure last chunk is enqueued
                chunkQueue.put(buffer);
                System.out.println(" Background Loaded Chunk: " + bytesRead + " bytes");

                //  Only mark as last chunk after enqueuing the last chunk
                if (totalBytesRead >= totalSize) {
                  lastChunkLoaded = true;
                  System.out.println(" Last chunk successfully queued and marked.");
                  break;
                }
              }
            }
          } catch (Exception e) {
            System.err.println(" Error in background loading: ");
            e.printStackTrace();
          }
        });
  }

  public synchronized byte[] getLastChunkFromQueue() throws IOException {
    try {
      if (!chunkQueue.isEmpty()) {
        byte[] lastChunk = chunkQueue.poll(2, TimeUnit.SECONDS); // Wait briefly if needed
        if (lastChunk != null) {
          System.out.println(" Fetching last chunk from queue: " + lastChunk.length + " bytes");
          return lastChunk;
        }
      }
    } catch (InterruptedException e) {
      System.err.println(" Interrupted while fetching last chunk from queue");
      Thread.currentThread().interrupt();
      throw new IOException("Interrupted while fetching last chunk", e);
    }

    System.err.println("⚠️ No last chunk found in queue. Returning empty.");
    return new byte[0]; // Return empty array if queue is unexpectedly empty
  }

  public synchronized boolean isEOFReached() {
    //  True if the last chunk has been read and no bytes are left
    return lastChunkLoaded
        && chunkQueue.isEmpty()
        && position >= currentBufferSize; // && position >= currentBufferSize && totalBytesRead >=
    // totalSize;
  }

  public synchronized long getRemainingBytes() {
    long remaining = totalSize - totalBytesRead;
    System.out.println(" Remaining Bytes: " + remaining);
    return remaining > 0 ? remaining : 0;
  }

  private synchronized void loadNextChunk() throws IOException {
    try {
      if (chunkQueue.isEmpty() && lastChunkLoaded) {
        return; //  No more data, return EOF
      }

      currentBuffer = chunkQueue.take(); //  Fetch from preloaded queue
      currentBufferSize = currentBuffer.length;
      position = 0;
      System.out.println(" Loaded Chunk | Size: " + currentBufferSize);

      // forceGc(); // If the GC is slow, possibly in the busy Read Ahead chunking process it could
      // be
      // possible that the dequeued items not yet garbage collected. check the heap size to
      // do any forceful garbage collection.

      //  Ensure the last chunk is processed
      if (lastChunkLoaded && chunkQueue.isEmpty()) {
        System.out.println(" Last chunk successfully processed and uploaded.");
      }
    } catch (InterruptedException e) {
      e.printStackTrace();
      throw new IOException(" Interrupted while loading next chunk", e);
    }
  }

  @Override
  public synchronized int read() throws IOException {
    if (position >= currentBufferSize) {
      if (lastChunkLoaded) return -1; //  EOF
      loadNextChunk();
    }
    return currentBuffer[(int) position++]
        & 0xFF; // Read the byte buffer into the integer number taking only least significant byte
    // into account
  }

  @Override
  public synchronized int read(byte[] b, int off, int len) throws IOException {
    if (position >= currentBufferSize) {
      System.out.println("position = " + position + " >= currentBufferSize = " + currentBufferSize);
      if (lastChunkLoaded) return -1;
      loadNextChunk();
    }

    int bytesToRead = (int) Math.min(len, currentBufferSize - position);
    System.arraycopy(
        currentBuffer,
        (int) position,
        b,
        off,
        bytesToRead); // Read the input stream byte array into the buffer
    position += bytesToRead;

    return bytesToRead;
  }

  /*
   * Close the original input stream and shutdown thread pool
   */
  @Override
  public void close() throws IOException {
    try {
      executor.shutdown();
      if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
        System.err.println("⚠️ Forcing executor shutdown...");
        executor.shutdownNow();
      }
    } catch (InterruptedException e) {
      throw new IOException(" Error shutting down executor", e);
    }
    originalStream.close();
  }

  public synchronized void resetStream() throws IOException {
    originalStream.reset();
    totalBytesRead = 0;
    lastChunkLoaded = false;
    position = 0;
    System.out.println(" Stream Reset!");
  }

  private void forceGc() {
    if (this.memoryMXBean == null) this.memoryMXBean = ManagementFactory.getMemoryMXBean();
    MemoryUsage heapMemoryUsage = this.memoryMXBean.getHeapMemoryUsage();
    // If the heap has still 1G and above in used section, better to call forceful garbage
    // collection. Ideally shouldnt have happened.
    if (heapMemoryUsage.getUsed() >= 1073741824) {
      System.gc();
      System.out.println("Forceful garbage collection called from ReadAheadInputStream");
    }
  }
}
