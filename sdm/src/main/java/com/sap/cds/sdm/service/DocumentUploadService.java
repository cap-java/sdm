package com.sap.cds.sdm.service;

import com.sap.cds.sdm.constants.SDMConstants;
import com.sap.cds.sdm.handler.TokenHandler;
import com.sap.cds.sdm.model.CmisDocument;
import com.sap.cds.sdm.model.SDMCredentials;
import com.sap.cds.services.ServiceException;
import io.reactivex.BackpressureOverflowStrategy;
import io.reactivex.Flowable;
import io.reactivex.Single;
import java.io.*;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.classic.methods.HttpUriRequestBase;
import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.entity.mime.InputStreamBody;
import org.apache.hc.client5.http.entity.mime.MultipartEntityBuilder;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.ParseException;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DocumentUploadService {

  private final CloseableHttpClient httpClient;
  MemoryMXBean memoryMXBean;
  private static final Logger logger = LoggerFactory.getLogger(DocumentUploadService.class);

  public DocumentUploadService() {
    logger.info("DocumentUploadService is instantiated");
    PoolingHttpClientConnectionManager connectionManager = new PoolingHttpClientConnectionManager();
    connectionManager.setMaxTotal(20);
    connectionManager.setDefaultMaxPerRoute(5);

    // Configure request with timeouts
    RequestConfig requestConfig =
        RequestConfig.custom()
            .setConnectionRequestTimeout(60, TimeUnit.MINUTES)
            .setResponseTimeout(60, TimeUnit.MINUTES)
            .build();

    ConnectionConfig connectionConfig =
        ConnectionConfig.custom().setConnectTimeout(60, TimeUnit.MINUTES).build();
    connectionManager.setDefaultConnectionConfig(connectionConfig);

    // Create a HttpClient using the connection manager
    httpClient =
        HttpClients.custom()
            .setConnectionManager(connectionManager)
            .setDefaultRequestConfig(requestConfig)
            .build();

    // Getting the handle to Mem management bean to print out heap mem used at required intervals.
    memoryMXBean = ManagementFactory.getMemoryMXBean();
  }

  /*
   * Reactive Java implementation to create document.
   */
  public Single<JSONObject> createDocumentRx(
      CmisDocument cmisDocument, SDMCredentials sdmCredentials, String jwtToken) {
    return Single.defer(
            () -> {
              try {
                //  Obtain DI token
                // String accessToken = TokenHandler.getDIToken(jwtToken, sdmCredentials);
                String accessToken = TokenHandler.getAccessToken(jwtToken, sdmCredentials);
                String sdmUrl =
                    sdmCredentials.getUrl() + "browser/" + cmisDocument.getRepositoryId() + "/root";

                //  Set HTTP headers
                Map<String, String> headers = new HashMap<>();
                headers.put("Authorization", "Bearer " + accessToken);
                headers.put("Connection", "keep-alive");

                long totalSize = cmisDocument.getContentLength();
                int chunkSize = SDMConstants.CHUNK_SIZE;

                if (totalSize <= 200 * 1024 * 1024) {
                  //  Upload directly if file is ≤ 200MB
                  return uploadSingleChunk(cmisDocument, headers, sdmUrl);
                } else {
                  //  Upload in chunks if file is > 100MB
                  return uploadLargeFileInChunks(cmisDocument, headers, sdmUrl, chunkSize);
                }
              } catch (Exception e) {
                return Single.error(
                    new IOException(" Error uploading document: " + e.getMessage(), e));
              }
            })
        .subscribeOn(io.reactivex.schedulers.Schedulers.io());
  }

  private CloseableHttpResponse performRequestWithRetry(String sdmUrl, HttpUriRequestBase request)
      throws IOException {
    return Flowable.fromCallable(() -> httpClient.execute(request))
        .onBackpressureBuffer(
            3, // Keeping a very low buffer as we hardly need it as the consumer (di call to
            // appendcontent) is fast enough for the producer (sending the rest call) as we are
            // making synchronous call
            () ->
                logger.error(
                    "Buffer overflow! Handle appropriately."), // Callback for overflow handling
            BackpressureOverflowStrategy
                .ERROR) // Strategy when overflow happens: just emit an error.
        .retryWhen(RetryUtils.retryLogic(3))
        .blockingSingle();
  }

  /*
   * CMIS call to appending content stream
   */
  private void appendContentStream(
      CmisDocument cmisDocument,
      Map<String, String> headers,
      String sdmUrl,
      byte[] chunkBuffer,
      int bytesRead,
      boolean isLastChunk,
      int chunkIndex)
      throws IOException, ParseException {

    MultipartEntityBuilder builder = MultipartEntityBuilder.create();
    builder.addTextBody("cmisaction", "appendContent");
    builder.addTextBody("objectId", cmisDocument.getObjectId());
    builder.addTextBody("propertyId[0]", "cmis:name");
    builder.addTextBody("propertyValue[0]", cmisDocument.getFileName());
    builder.addTextBody("propertyId[1]", "cmis:objectTypeId");
    builder.addTextBody("propertyValue[1]", "cmis:document");
    builder.addTextBody("isLastChunk", String.valueOf(isLastChunk));
    builder.addTextBody("filename", cmisDocument.getFileName());
    builder.addTextBody("succinct", "true");
    builder.addPart(
        "media",
        new InputStreamBody(
            new ByteArrayInputStream(chunkBuffer, 0, bytesRead), cmisDocument.getFileName()));

    HttpEntity entity = builder.build();

    HttpPost request = new HttpPost(sdmUrl);
    request.setEntity(entity);
    headers.forEach(request::addHeader);

    long startChunkUploadTime = System.currentTimeMillis();
    try (CloseableHttpResponse response = performRequestWithRetry(sdmUrl, request)) {
      long endChunkUploadTime = System.currentTimeMillis();
      logger.debug(
          " Chunk "
              + chunkIndex
              + " appendContent completed and it took "
              + ((int) ((endChunkUploadTime - startChunkUploadTime) / 1000))
              + " seconds");
    }
  }

  private String createEmptyDocument(
      CmisDocument cmisDocument, Map<String, String> headers, String sdmUrl)
      throws IOException, ParseException {

    MultipartEntityBuilder builder = MultipartEntityBuilder.create();
    builder.addTextBody("cmisaction", "createDocument");
    builder.addTextBody("objectId", cmisDocument.getFolderId());
    builder.addTextBody("propertyId[0]", "cmis:name");
    builder.addTextBody("propertyValue[0]", cmisDocument.getFileName());
    builder.addTextBody("propertyId[1]", "cmis:objectTypeId");
    builder.addTextBody("propertyValue[1]", "cmis:document");
    builder.addTextBody("succinct", "true");

    HttpEntity entity = builder.build();

    HttpPost request = new HttpPost(sdmUrl);
    request.setEntity(entity);
    headers.forEach(request::addHeader);

    try (CloseableHttpResponse response = performRequestWithRetry(sdmUrl, request)) {
      logger.info("Empty Document Created: " + response.getCode());
      if (response.getEntity() == null) {
        throw new IOException("Response entity is null!");
      }
      return EntityUtils.toString(response.getEntity());
    }
  }

  private Single<JSONObject> uploadSingleChunk(
      CmisDocument cmisDocument, Map<String, String> headers, String sdmUrl) {

    return Single.defer(
        () -> {
          try {
            //  Initialize ReadAheadInputStream
            InputStream originalStream = cmisDocument.getContent();
            if (originalStream == null) {
              return Single.error(new IOException(" File stream is null!"));
            }

            //  Prepare Multipart Request
            MultipartEntityBuilder builder = MultipartEntityBuilder.create();
            builder.addTextBody("cmisaction", "createDocument");
            builder.addTextBody("objectId", cmisDocument.getFolderId());
            builder.addTextBody("propertyId[0]", "cmis:name");
            builder.addTextBody("propertyValue[0]", cmisDocument.getFileName());
            builder.addTextBody("propertyId[1]", "cmis:objectTypeId");
            builder.addTextBody("propertyValue[1]", "cmis:document");
            builder.addTextBody("succinct", "true");
            // Add media part with file metadata

            builder.addBinaryBody(
                "filename",
                cmisDocument.getContent(),
                ContentType.create(cmisDocument.getMimeType()),
                cmisDocument.getFileName());
            HttpEntity entity = builder.build();

            HttpPost request = new HttpPost(sdmUrl);
            request.setEntity(entity);
            headers.forEach(request::addHeader);
            return Single.fromCallable(
                    () -> {
                      try (CloseableHttpResponse response =
                          performRequestWithRetry(sdmUrl, request)) {
                        String responseBody = EntityUtils.toString(response.getEntity());
                        logger.debug(" Upload Response: " + responseBody);

                        Map<String, String> finalResMap = new HashMap<>();
                        formResponse(cmisDocument, finalResMap, responseBody);

                        return new JSONObject(finalResMap);
                      }
                    })
                .toFlowable()
                .retryWhen(RetryUtils.retryLogic(3))
                .singleOrError();
          } catch (Exception e) {
            return Single.error(
                new IOException(" Error uploading small document: " + e.getMessage(), e));
          }
        });
  }

  private Single<JSONObject> uploadLargeFileInChunks(
      CmisDocument cmisDocument, Map<String, String> headers, String sdmUrl, int chunkSize) {

    return Single.defer(
        () -> {
          ReadAheadInputStream chunkedStream = null;
          try {
            InputStream originalStream = cmisDocument.getContent();
            if (originalStream == null) {
              return Single.error(new IOException("File stream is null!"));
            }

            chunkedStream =
                new ReadAheadInputStream(originalStream, cmisDocument.getContentLength());

            // Step 1: Initial Request (Without Content) and Get `objectId`. It is required to
            // set in every chunk appendContent
            String responseBody = createEmptyDocument(cmisDocument, headers, sdmUrl);
            logger.debug("Response Body: " + responseBody);

            String objectId =
                (new JSONObject(responseBody))
                    .getJSONObject("succinctProperties")
                    .getString("cmis:objectId");
            cmisDocument.setObjectId(objectId);
            logger.info("objectId of empty doc is " + objectId);

            // Step 2: Upload Chunks Sequentially
            int chunkIndex = 0;
            byte[] chunkBuffer = new byte[chunkSize];
            int bytesRead;
            boolean hasMoreChunks = true;
            while (hasMoreChunks) {
              long startChunkUploadTime = System.currentTimeMillis();

              // Step 3: Read next chunk
              bytesRead = chunkedStream.read(chunkBuffer, 0, chunkSize);
              logger.debug("bytesRead is " + bytesRead);
              // Step 4: Fetch remaining bytes before checking EOF
              long remainingBytes = chunkedStream.getRemainingBytes();
              logger.debug("remainingBytes is " + remainingBytes);

              // Step 5: Check if it's the last chunk
              boolean isLastChunk = bytesRead < chunkSize || chunkedStream.isEOFReached();

              // Step 6: If no bytes were read AND queue still has data, fetch from queue
              if (bytesRead == -1 && !chunkedStream.isChunkQueueEmpty()) {
                logger.info("Premature exit detected. Fetching last chunk from queue...");
                byte[] lastChunk = chunkedStream.getLastChunkFromQueue();
                bytesRead = lastChunk.length;
                System.arraycopy(lastChunk, 0, chunkBuffer, 0, bytesRead);
                isLastChunk = true; // It has to be the last chunk
              }

              // Log every chunk details
              logger.info(
                  "Chunk "
                      + chunkIndex
                      + " | BytesRead: "
                      + bytesRead
                      + " | RemainingBytes: "
                      + remainingBytes
                      + " | isLastChunk? "
                      + isLastChunk);

              // Step 7: Append Chunk. Call cmis api to append content stream
              if (bytesRead > 0) {
                appendContentStream(
                    cmisDocument, headers, sdmUrl, chunkBuffer, bytesRead, isLastChunk, chunkIndex);
              }

              long endChunkUploadTime = System.currentTimeMillis();
              logger.debug(
                  " Chunk "
                      + chunkIndex
                      + " having "
                      + bytesRead
                      + " bytes is read and it took "
                      + ((int) (endChunkUploadTime - startChunkUploadTime) / 1000)
                      + " seconds");

              chunkIndex++;

              if (isLastChunk) {
                // Just for debug purpose log the heap consumption details.
                logger.info("Heap Memory Usage during the Upload when chunkIndex is " + chunkIndex);
                printMemoryConsumption();
                hasMoreChunks = false;
              }
            }
            // Step 8: Finally use the custom formResponse to return
            Map<String, String> finalResMap = new HashMap<>();
            this.formResponse(cmisDocument, finalResMap, responseBody);
            return Single.just(new JSONObject(finalResMap));
          } catch (Exception e) {
            logger.error("Exception in uploadLargeFileInChunks: " + e.getMessage());
            return Single.error(
                new IOException("Error uploading document in chunks: " + e.getMessage(), e));
          } finally {
            if (chunkedStream != null) {
              try {
                chunkedStream.close();
              } catch (IOException e) {
                logger.error(
                    "Error closing chunkedStream: \n" + Arrays.toString(e.getStackTrace()));
              }
            }
          }
        });
  }

  private void formResponse(
      CmisDocument cmisDocument, Map<String, String> finalResponse, String responseBody) {
    logger.info("Entering formResponse method");
    String status = "success";
    String name = cmisDocument.getFileName();
    String id = cmisDocument.getAttachmentId();
    String objectId = "";
    String error = "";
    String versionseriesId = "";

    try {
      logger.debug("Parsing responseBody: " + responseBody);
      JSONObject jsonResponse = new JSONObject(responseBody);
      if (jsonResponse.has("succinctProperties")) {
        JSONObject succinctProperties = jsonResponse.getJSONObject("succinctProperties");
        objectId = succinctProperties.getString("cmis:objectId");
        System.out.println("SUCCINT " + succinctProperties);
        versionseriesId =
            succinctProperties.get("cmis:versionSeriesId") != null
                ? succinctProperties.getString("cmis:versionSeriesId")
                : null;
        finalResponse.put("versionSeriesId", versionseriesId);
      } else if (jsonResponse.has("properties")
          && jsonResponse.getJSONObject("properties").has("cmis:objectId")) {
        objectId =
            jsonResponse
                .getJSONObject("properties")
                .getJSONObject("cmis:objectId")
                .getString("value");

      } else {
        String message = jsonResponse.optString("message", "Unknown error");
        status = "fail";
        error = message;
      }

      finalResponse.put("name", name);
      finalResponse.put("id", id);
      finalResponse.put("status", status);
      finalResponse.put("message", error);
      if (!objectId.isEmpty()) {
        finalResponse.put("objectId", objectId);
      }
    } catch (Exception e) {
      logger.error("Exception in formResponse: " + e.getMessage());
      throw new ServiceException(SDMConstants.getGenericError("upload"));
    }
  }

  // Helper method to convert bytes to megabytes
  private static long bytesToMegabytes(long bytes) {
    return bytes / (1024 * 1024);
  }

  /*
   * Utility method to log the memory usage details
   */
  private void printMemoryConsumption() {
    MemoryUsage heapMemoryUsage = this.memoryMXBean.getHeapMemoryUsage();
    // Print the heap memory usage details
    logger.info(
        "Init: {} MB, \t\t|Used: {} MB \t\t|Committed: {} MB  \t\t|Max: {} MB",
        bytesToMegabytes(heapMemoryUsage.getInit()),
        bytesToMegabytes(heapMemoryUsage.getUsed()),
        bytesToMegabytes(heapMemoryUsage.getCommitted()),
        bytesToMegabytes(heapMemoryUsage.getMax()));
  }
}
