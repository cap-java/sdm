package com.sap.cds.sdm.service;

import com.sap.cds.sdm.constants.SDMConstants;
import com.sap.cds.sdm.constants.SDMConstants;
import java.io.*;
import java.util.Arrays;
import java.util.concurrent.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ReadAheadInputStream extends InputStream {
  private final BufferedInputStream originalStream;
  private final long totalSize;
  private final int chunkSize = SDMConstants.CHUNK_SIZE;
  private final int chunkSize = SDMConstants.CHUNK_SIZE;
  private long totalBytesRead = 0;
  private boolean lastChunkLoaded = false;
  private byte[] currentBuffer;
  private long currentBufferSize = 0;
  private long position = 0;
  private static final Logger logger = LoggerFactory.getLogger(ReadAheadInputStream.class);
  private static final Logger logger = LoggerFactory.getLogger(ReadAheadInputStream.class);
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

    logger.info(" Initializing ReadAheadInputStream..."); // Once per one file upload
    logger.info(" Initializing ReadAheadInputStream..."); // Once per one file upload
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

              // Keep reading until full chunk is read until EOF
              while (bytesRead < chunkSize
                  && (readAttempt =
                          originalStream.read(buffer, (int) bytesRead, chunkSize - (int) bytesRead))
                      > 0) {
                bytesRead += readAttempt;
              }

              // Ensure any data read is processed
              if (bytesRead > 0) {
                totalBytesRead += bytesRead;

                // Trim buffer if last chunk is smaller
                if (bytesRead < chunkSize) {
                  byte[] trimmedBuffer = new byte[(int) bytesRead];
                  System.arraycopy(buffer, 0, trimmedBuffer, 0, (int) bytesRead);
                  buffer = trimmedBuffer;
                }

                // Ensure last chunk is enqueued
                chunkQueue.put(buffer);
                logger.info(" Background Loaded Chunk: " + bytesRead + " bytes");

                // Only mark as last chunk after enqueuing the last chunk
                if (totalBytesRead >= totalSize) {
                  lastChunkLoaded = true;
                  logger.info(" Last chunk successfully queued and marked.");
                  break;
                }
              }
            }
          } catch (InterruptedException | IOException e) {
            logger.error(" Error in background loading: \n" + Arrays.toString(e.getStackTrace()));
            Thread.currentThread().interrupt(); // Re-interrupt the current thread
          }
        });
  }

  public synchronized byte[] getLastChunkFromQueue() throws IOException {
    try {
      if (!chunkQueue.isEmpty()) {
        byte[] lastChunk = chunkQueue.poll(2, TimeUnit.SECONDS); // Wait briefly if needed
        if (lastChunk != null) {
          logger.info(" Fetching last chunk from queue: " + lastChunk.length + " bytes");
          return lastChunk;
        }
      }
    } catch (InterruptedException e) {
      logger.error(" Interrupted while fetching last chunk from queue");
      Thread.currentThread().interrupt();
      throw new IOException("Interrupted while fetching last chunk", e);
    }

    logger.error("No last chunk found in queue. Returning empty.");
    return new byte[0]; // Return empty array if queue is unexpectedly empty
  }

  public synchronized boolean isEOFReached() {
    logger.info(
        "lastChunkLoaded "
            + lastChunkLoaded
            + " chunkQueue.isEmpty():"
            + chunkQueue.isEmpty()
            + " position:"
            + position
            + " currentBufferSize:"
            + currentBufferSize);
    // True if the last chunk has been read and no bytes are left
    return lastChunkLoaded && chunkQueue.isEmpty() && position >= currentBufferSize;
  }

  public synchronized long getRemainingBytes() {
    long remaining = totalSize - totalBytesRead;
    logger.info(" Remaining Bytes: " + remaining);
    return remaining > 0 ? remaining : 0;
  }

  private synchronized void loadNextChunk() throws IOException {
    try {
      if (chunkQueue.isEmpty() && lastChunkLoaded) {
        return; // No more data, return EOF
      }

      currentBuffer = chunkQueue.take(); // Fetch from preloaded queue
      currentBufferSize = currentBuffer.length;
      position = 0;
      logger.info(" Loaded Chunk | Size: " + currentBufferSize);

      // Ensure the last chunk is processed
      if (lastChunkLoaded && chunkQueue.isEmpty()) {
        logger.info(" Last chunk successfully processed and uploaded.");
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IOException(" Interrupted while loading next chunk ", e);
    }
  }

  @Override
  public synchronized int read() throws IOException {
    logger.info(
        "ReadAheadInputStream.read() called by " + Thread.currentThread().getStackTrace()[2]);
    if (position >= currentBufferSize) {
      if (lastChunkLoaded) return -1; // EOF
      loadNextChunk();
    }
    return currentBuffer[(int) position++]
        & 0xFF; // Read the byte buffer into the integer number taking only least significant byte
    // into account
  }

  @Override
  public synchronized int read(byte[] b, int off, int len) throws IOException {
    if (position >= currentBufferSize) {
      logger.info("position = " + position + " >= currentBufferSize = " + currentBufferSize);
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
    logger.info(
        "ReadAheadInputStream.close() called by " + Thread.currentThread().getStackTrace()[2]);
    try {
      executor.shutdown();
      if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
        logger.error("Forcing executor shutdown...");
        executor.shutdownNow();
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IOException(" Error shutting down executor", e);
    }
    originalStream.close();
  }

  public synchronized void resetStream() throws IOException {
    originalStream.reset();
    totalBytesRead = 0;
    lastChunkLoaded = false;
    position = 0;
    logger.info(" Stream Reset!");
  }
}
