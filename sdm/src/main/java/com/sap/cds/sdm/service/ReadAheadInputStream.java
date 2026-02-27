package com.sap.cds.sdm.service;

import com.sap.cds.sdm.constants.SDMConstants;
import com.sap.cds.sdm.service.exceptions.InsufficientDataException;
import java.io.*;
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
      new LinkedBlockingQueue<>(
          4); // Reduced from 50 to 4 (80MB) - balances read-ahead performance with heap constraints

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
    logger.debug("START: preloadChunks - totalSize: {}", totalSize);
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
    logger.debug("START: readChunk");
    int maxRetries = 5;
    int retryCount = 0;

    while (bytesReadAtomic.get() < CHUNK_SIZE) {
      try {
        byte[] buffer = bufferRef.get();
        int result =
            originalStream.read(
                buffer, (int) bytesReadAtomic.get(), CHUNK_SIZE - (int) bytesReadAtomic.get());

        if (result > 0) {
          bytesReadAtomic.addAndGet(result);
          retryCount = 0; // Reset retry count on successful read
        } else if (result == -1) {
          logger.info("EOF reached while reading the stream.");
          break;
        } else if (result == 0) {
          // Treat 0 bytes read as InsufficientDataException (matches original behavior)
          throw new InsufficientDataException("Read returned 0 bytes");
        }
      } catch (EOFException | InsufficientDataException e) {
        // These exceptions should be retried (matching RetryUtils.shouldRetry())
        retryCount++;
        if (retryCount >= maxRetries) {
          logger.error("Failed to read chunk after {} retries: {}", maxRetries, e.getMessage(), e);
          throw new IOException("Failed to read chunk after retries", e);
        }
        long delaySeconds =
            (long) Math.pow(2, retryCount); // Exponential backoff: 2, 4, 8, 16, 32 seconds
        logger.info(
            "Retry attempt {} failed. Retrying in {} seconds. Error: {}",
            retryCount,
            delaySeconds,
            e.getMessage());
        try {
          Thread.sleep(delaySeconds * 1000); // Convert to milliseconds
        } catch (InterruptedException ie) {
          Thread.currentThread().interrupt();
          throw new IOException("Interrupted during retry backoff", ie);
        }
      } catch (IOException e) {
        // Other IOExceptions should fail immediately (not retried in original)
        logger.error("Non-retryable IOException: {}", e.getMessage(), e);
        throw e;
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
    logger.debug("START: loadNextChunk");
    try {
      if (chunkQueue.isEmpty() && lastChunkLoaded.get()) {
        logger.debug("END: loadNextChunk - no more data");
        return; // No more data, return EOF
      }

      currentBuffer = chunkQueue.take(); // Fetch from preloaded queue
      currentBufferSize = currentBuffer.length;
      position.set(0);

      // Ensure the last chunk is processed
      if (lastChunkLoaded.get() && chunkQueue.isEmpty()) {
        logger.info(" Last chunk successfully processed and uploaded.");
      }
      logger.debug("END: loadNextChunk - loaded {} bytes", currentBufferSize);
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
    logger.debug(
        "read(byte[], off={}, len={}) called, position: {}, bufferSize: {}",
        off,
        len,
        position.get(),
        currentBufferSize);
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
    logger.debug("read(byte[]) returning {} bytes", bytesToRead);

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
    logger.debug("END: close - stream closed");
  }

  public synchronized void resetStream() throws IOException {
    originalStream.reset();
    totalBytesRead.set(0);
    lastChunkLoaded.set(false);
    position.set(0);
    logger.info(" Stream Reset!");
  }
}
