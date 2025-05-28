package com.sap.cds.sdm.service;

import com.sap.cds.sdm.constants.SDMConstants;
import com.sap.cds.sdm.service.exceptions.InsufficientDataException;
import io.reactivex.Flowable;
import java.io.*;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ReadAheadInputStream extends InputStream {
  private final BufferedInputStream originalStream;
  private final long totalSize;
  private static final int CHUNK_SIZE = SDMConstants.CHUNK_SIZE;
  private AtomicLong totalBytesRead = new AtomicLong(0);
  private AtomicBoolean lastChunkLoaded = new AtomicBoolean(false);
  private byte[] currentBuffer;
  private long currentBufferSize = 0;
  private AtomicLong position = new AtomicLong(0);
  private static final Logger logger = LoggerFactory.getLogger(ReadAheadInputStream.class);
  private final ExecutorService executor =
      Executors.newFixedThreadPool(2); // Thread pool to Read next chunk
  private final BlockingQueue<byte[]> chunkQueue =
      new LinkedBlockingQueue<>(50); // Next chunk is read to a queue

  public ReadAheadInputStream(InputStream inputStream, long totalSize) throws IOException {
    if (inputStream == null) {
      throw new IllegalArgumentException(" InputStream cannot be null");
    }

    this.originalStream = new BufferedInputStream(inputStream, CHUNK_SIZE);
    this.totalSize = totalSize;
    this.currentBuffer = new byte[CHUNK_SIZE];

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
            while (totalBytesRead.get() < totalSize) {
              AtomicReference<byte[]> bufferRef = new AtomicReference<>(new byte[CHUNK_SIZE]);
              AtomicLong bytesReadAtomic = new AtomicLong(0);

              readChunk(bufferRef, bytesReadAtomic);

              long bytesRead = bytesReadAtomic.get();
              if (bytesRead > 0) {
                totalBytesRead.addAndGet(bytesRead);

                // Trim buffer if last chunk is smaller
                if (bytesRead < CHUNK_SIZE) {
                  byte[] trimmedBuffer = new byte[(int) bytesRead];
                  System.arraycopy(bufferRef.get(), 0, trimmedBuffer, 0, (int) bytesRead);
                  bufferRef.set(trimmedBuffer);
                }

                // Ensure last chunk is enqueued
                chunkQueue.put(bufferRef.get());

                // Only mark as last chunk after enqueuing the last chunk
                if (totalBytesRead.get() >= totalSize) {
                  lastChunkLoaded.set(true);
                  logger.info("Last chunk successfully queued and marked.");
                  break;
                }
              } else {
                logger.warn("No bytes read from stream. Possible EOF.");
                break;
              }
            }
          } catch (InterruptedException e) {
            logger.error("Thread interrupted during background loading", e);
            Thread.currentThread().interrupt();
          } catch (Exception e) {
            logger.error("Unexpected exception during background loading", e);
          }
        });
  }

  private void readChunk(AtomicReference<byte[]> bufferRef, AtomicLong bytesReadAtomic)
      throws IOException {
    while (bytesReadAtomic.get() < CHUNK_SIZE) {
      try {
        List<Integer> results =
            Flowable.fromCallable(
                    () -> {
                      byte[] buffer = bufferRef.get();
                      // Read from stream and update bytesReadAtomic
                      int result =
                          originalStream.read(
                              buffer,
                              (int) bytesReadAtomic.get(),
                              CHUNK_SIZE - (int) bytesReadAtomic.get());
                      if (result > 0) {
                        bytesReadAtomic.addAndGet(result);
                      } else if (result == 0) {
                        throw new InsufficientDataException("Read returned 0 bytes");
                      }
                      return result;
                    })
                .retryWhen(RetryUtils.retryLogic(5)) // Apply retry logic with 5 attempts
                .toList()
                .blockingGet();

        if (results == null || results.isEmpty())
          throw new IOException("Failed to read chunk: results is null or empty");
        // Check if the read was successful

        int readAttempt = results.get(0);

        if (readAttempt == -1) {
          logger.info("EOF reached while reading the stream.");
          break;
        }
      } catch (Exception e) {
        logger.error("Failed to read chunk after retries: {}", e.getMessage(), e);
        throw new IOException("Failed to read chunk", e);
      }
    }
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
    logger.debug(
        "lastChunkLoaded "
            + lastChunkLoaded.get()
            + " chunkQueue.isEmpty():"
            + chunkQueue.isEmpty()
            + " position:"
            + position.get()
            + " currentBufferSize:"
            + currentBufferSize);
    // True if the last chunk has been read and no bytes are left
    return lastChunkLoaded.get() && chunkQueue.isEmpty() && position.get() >= currentBufferSize;
  }

  public synchronized long getRemainingBytes() {
    long remaining = totalSize - totalBytesRead.get();
    return remaining > 0 ? remaining : 0;
  }

  private synchronized void loadNextChunk() throws IOException {
    try {
      if (chunkQueue.isEmpty() && lastChunkLoaded.get()) {
        return; // No more data, return EOF
      }

      currentBuffer = chunkQueue.take(); // Fetch from preloaded queue
      currentBufferSize = currentBuffer.length;
      position.set(0);

      // Ensure the last chunk is processed
      if (lastChunkLoaded.get() && chunkQueue.isEmpty()) {
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
    if (position.get() >= currentBufferSize) {
      if (lastChunkLoaded.get()) return -1; // EOF
      loadNextChunk();
    }
    return currentBuffer[(int) position.getAndIncrement()]
        & 0xFF; // Read the byte buffer into the integer number taking only least significant byte
    // into account
  }

  @Override
  public synchronized int read(byte[] b, int off, int len) throws IOException {
    if (position.get() >= currentBufferSize) {
      if (lastChunkLoaded.get()) return -1;
      loadNextChunk();
    }

    int bytesToRead = (int) Math.min(len, currentBufferSize - position.get());
    System.arraycopy(
        currentBuffer,
        (int) position.get(),
        b,
        off,
        bytesToRead); // Read the input stream byte array into the buffer
    position.addAndGet(bytesToRead);

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
    totalBytesRead.set(0);
    lastChunkLoaded.set(false);
    position.set(0);
    logger.info(" Stream Reset!");
  }
}
