package com.sap.cds.sdm.service;

import com.sap.cds.sdm.constants.SDMConstants;
import com.sap.cds.sdm.handler.TokenHandler;
import com.sap.cds.sdm.model.CmisDocument;
import com.sap.cds.sdm.model.SDMCredentials;
import com.sap.cds.services.ServiceException;
import io.reactivex.Single;
import java.io.*;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.json.JSONObject;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.*;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

public class DocumentUploadService {

  private final RestTemplate restTemplate;

  MemoryMXBean memoryMXBean;

  public DocumentUploadService() {
    System.out.println("DocumentUploadService is instantiated");
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
    CloseableHttpClient httpClient =
        HttpClients.custom()
            .setConnectionManager(connectionManager)
            .setDefaultRequestConfig(requestConfig)
            .build();

    // Pass the HttpClient to the request factory
    // Create the factory with the HttpClient
    HttpComponentsClientHttpRequestFactory requestFactory =
        new HttpComponentsClientHttpRequestFactory(httpClient);

    requestFactory.setConnectTimeout(3600000);
    requestFactory.setConnectionRequestTimeout(3600000);

    // Create the RestTemplate with this request factory
    restTemplate = new RestTemplate(requestFactory);

    // Add interceptors if needed. May be if for debug logs etc.
    restTemplate
        .getInterceptors()
        .add(
            (request, body, execution) -> {
              // Log, modify headers, etc.
              return execution.execute(request, body);
            });
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
                String accessToken = TokenHandler.getDIToken(jwtToken, sdmCredentials);
                String sdmUrl =
                    sdmCredentials.getUrl() + "browser/" + cmisDocument.getRepositoryId() + "/root";

                //  Set HTTP headers
                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.MULTIPART_FORM_DATA);
                headers.set("Authorization", "Bearer " + accessToken);
                headers.setConnection("keep-alive");

                long totalSize = cmisDocument.getContentLength();
                int chunkSize = 100 * 1024 * 1024; // 100MB chunks

                if (totalSize <= chunkSize) {
                  //  Upload directly if file is ≤ 100MB
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

  /*
   * CMIS call to appending content stream
   */
  private void appendContentStream(
      CmisDocument cmisDocument,
      HttpHeaders headers,
      String sdmUrl,
      byte[] chunkBuffer,
      int bytesRead,
      boolean isLastChunk,
      int chunkIndex)
      throws IOException {

    MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
    body.add("cmisaction", "appendContent");
    body.add("objectId", cmisDocument.getObjectId());

    body.add("propertyId[0]", "cmis:name");
    body.add("propertyValue[0]", cmisDocument.getFileName());
    body.add("propertyId[1]", "cmis:objectTypeId");
    body.add("propertyValue[1]", "cmis:document");

    body.add("isLastChunk", String.valueOf(isLastChunk));
    body.add("filename", cmisDocument.getFileName());
    body.add("succinct", "true");
    InputStreamResource chunkResource =
        new InputStreamResource(new ByteArrayInputStream(chunkBuffer, 0, bytesRead)) {
          @Override
          public long contentLength() {
            return bytesRead;
          }

          @Override
          public String getFilename() {
            return cmisDocument.getFileName();
          }
        };

    body.add(
        "media",
        chunkResource); // In multi part chunking directly adding the chunk as body instead of
    // wrapping each chunk by mimetype HttpHeader

    /*HttpHeaders fileHeaders = new HttpHeaders();
    fileHeaders.setContentType(
        MediaType.parseMediaType(cmisDocument.getMimeType())); //  Ensures correct type
    body.add("media", new HttpEntity<>(chunkResource, fileHeaders)); //  Preserve file metadata
    */

    long startChunkUploadTime = System.currentTimeMillis();
    HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);
    ResponseEntity<String> response =
        restTemplate.exchange(sdmUrl, HttpMethod.POST, requestEntity, String.class);
    long endChunkUploadTime = System.currentTimeMillis();

    System.out.println(
        " Chunk "
            + chunkIndex
            + " Uploaded. Response: "
            + response.getBody()
            + " and it took "
            + ((int) ((endChunkUploadTime - startChunkUploadTime) / 1000))
            + " seconds");
  }

  private ResponseEntity<String> createEmptyDocument(
      CmisDocument cmisDocument, HttpHeaders headers, String sdmUrl) throws IOException {

    MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
    body.add("cmisaction", "createDocument");
    body.add("objectId", cmisDocument.getFolderId());
    body.add("propertyId[0]", "cmis:name");
    body.add("propertyValue[0]", cmisDocument.getFileName());
    body.add("propertyId[1]", "cmis:objectTypeId");
    body.add("propertyValue[1]", "cmis:document");
    body.add("succinct", "true");

    HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);
    ResponseEntity<String> response =
        restTemplate.exchange(sdmUrl, HttpMethod.POST, requestEntity, String.class);
    System.out.println(" Empty Document Created: " + response.getBody());

    return response;
  }

  private Single<JSONObject> uploadSingleChunk(
      CmisDocument cmisDocument, HttpHeaders headers, String sdmUrl) {

    return Single.defer(
        () -> {
          try {
            //  Initialize ReadAheadInputStream
            InputStream originalStream = cmisDocument.getContent();
            if (originalStream == null) {
              return Single.error(new IOException(" File stream is null!"));
            }

            ReadAheadInputStream reReadableStream =
                new ReadAheadInputStream(originalStream, cmisDocument.getContentLength());
            // Need to wrap known content length InputStreamResource with a custom class because if
            // not InputStream will be read by InputStreamResource multiple times just to know the
            // length!
            ReReadableInputStreamResource fileResource =
                new ReReadableInputStreamResource(
                    reReadableStream,
                    cmisDocument.getFileName(),
                    cmisDocument.getContentLength(),
                    cmisDocument.getMimeType());

            //  Prepare Multipart Request
            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            body.add("cmisaction", "createDocument");
            body.add("objectId", cmisDocument.getFolderId());
            body.add("propertyId[0]", "cmis:name");
            body.add("propertyValue[0]", cmisDocument.getFileName());
            body.add("propertyId[1]", "cmis:objectTypeId");
            body.add("propertyValue[1]", "cmis:document");
            body.add("succinct", "true");

            HttpHeaders fileHeaders = new HttpHeaders();
            fileHeaders.setContentType(
                MediaType.parseMediaType(cmisDocument.getMimeType())); //  Ensures correct type

            // body.add("media", fileResource); //Just keeping media added directly to the body
            // commented for now
            body.add(
                "media",
                new HttpEntity<>(
                    fileResource,
                    fileHeaders)); //  To preserve file metadata wrap media content with the
            // HttpHeader explicitly stating mimetype

            HttpEntity<MultiValueMap<String, Object>> requestEntity =
                new HttpEntity<>(body, headers);

            //  Send Request
            ResponseEntity<String> response =
                restTemplate.exchange(sdmUrl, HttpMethod.POST, requestEntity, String.class);
            System.out.println(" Upload Response: " + response.getBody());

            Map<String, String> finalResMap = new HashMap<>();
            this.formResponse(
                cmisDocument, finalResMap, response); // Use formResponse to for the custom Response
            return Single.just(new JSONObject(finalResMap));
          } catch (Exception e) {
            return Single.error(
                new IOException(" Error uploading small document: " + e.getMessage(), e));
          }
        });
  }

  private Single<JSONObject> uploadLargeFileInChunks(
      CmisDocument cmisDocument, HttpHeaders headers, String sdmUrl, int chunkSize) {

    return Single.defer(
        () -> {
          try {
            InputStream originalStream = cmisDocument.getContent();
            if (originalStream == null) {
              return Single.error(new IOException(" File stream is null!"));
            }

            try (ReadAheadInputStream chunkedStream =
                new ReadAheadInputStream(originalStream, cmisDocument.getContentLength())) {
              //  Step 1: Initial Request (Without Content) and Get `objectId`. It is required to
              // set in every chunk appendContent
              ResponseEntity<String> responseEntity =
                  createEmptyDocument(cmisDocument, headers, sdmUrl);
              String objectId =
                  (new JSONObject(responseEntity.getBody()))
                      .getJSONObject("succinctProperties")
                      .getString("cmis:objectId");
              cmisDocument.setObjectId(objectId);

              //  Step 2: Upload Chunks Sequentially
              int chunkIndex = 0;
              byte[] chunkBuffer = new byte[chunkSize];
              int bytesRead;
              boolean hasMoreChunks = true;
              while (hasMoreChunks) {
                long startChunkUBytesReaddTime = System.currentTimeMillis();

                // Step 3: Read next chunk
                bytesRead = chunkedStream.read(chunkBuffer, 0, chunkSize);

                // Step 4: Fetch remaining bytes before checking EOF
                long remainingBytes = chunkedStream.getRemainingBytes();

                // Step 5: Check if it's the last chunk
                boolean isLastChunk = bytesRead < chunkSize || chunkedStream.isEOFReached();

                // Step 6: If no bytes were read AND queue still has data, fetch from queue
                if (bytesRead == -1 && !chunkedStream.isChunkQueueEmpty()) {
                  System.out.println(" Premature exit detected. Fetching last chunk from queue...");
                  byte[] lastChunk = chunkedStream.getLastChunkFromQueue();
                  bytesRead = lastChunk.length;
                  System.arraycopy(lastChunk, 0, chunkBuffer, 0, bytesRead);
                  isLastChunk = true; //  It has to be the last chunk
                }

                // 🔹 Log every chunk details
                System.out.println(
                    "🔹 Chunk "
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
                      cmisDocument,
                      headers,
                      sdmUrl,
                      chunkBuffer,
                      bytesRead,
                      isLastChunk,
                      chunkIndex);
                }

                long endChunkUBytesReaddTime = System.currentTimeMillis();
                System.out.println(
                    " Chunk "
                        + chunkIndex
                        + " having "
                        + bytesRead
                        + " bytes is read and it took "
                        + ((int) (endChunkUBytesReaddTime - startChunkUBytesReaddTime) / 1000)
                        + " seconds");

                chunkIndex++;
                // Just for debug purpose log the heap consumption details.
                if (isLastChunk || chunkIndex % 5 == 0) {
                  System.out.println(
                      "Heap Memory Usage during the Upload when chunkIndex is " + chunkIndex);
                  printMemoryConsumption();
                }

                if (isLastChunk) {

                  System.out.println(" Last chunk processed, exiting upload.");
                  break;
                }
              }
              // Step 8: Finally use the custom formResponse to return
              Map<String, String> finalResMap = new HashMap<>();
              this.formResponse(cmisDocument, finalResMap, responseEntity);
              return Single.just(new JSONObject(finalResMap));
            }

          } catch (Exception e) {
            return Single.error(
                new IOException(" Error uploading document in chunks: " + e.getMessage(), e));
          }
        });
  }

  private void formResponse(
      CmisDocument cmisDocument,
      Map<String, String> finalResponse,
      ResponseEntity<String> response) {
    String status = "success";
    String name = cmisDocument.getFileName();
    String id = cmisDocument.getAttachmentId();
    String objectId = "";
    String error = "";

    try {

      String responseString = response.getBody();
      JSONObject jsonResponse = new JSONObject(responseString);
      int responseCode = response.getStatusCode().value();
      System.out.println("responseString=" + responseString);
      System.out.println("responseCode=" + responseCode);
      if (responseCode == 201 || responseCode == 200) {
        if (jsonResponse.has("succinctProperties")) {
          JSONObject succinctProperties = jsonResponse.getJSONObject("succinctProperties");
          objectId = succinctProperties.getString("cmis:objectId");
        } else if (jsonResponse.has("properties")
            && jsonResponse.getJSONObject("properties").has("cmis:objectId"))
          objectId =
              jsonResponse
                  .getJSONObject("properties")
                  .getJSONObject("cmis:objectId")
                  .getString("value");
      } else {
        String message = jsonResponse.optString("message", "Unknown error");
        if (responseCode == 409
            && "Malware Service Exception: Virus found in the file!".equals(message)) {
          status = "virus";
        } else if (responseCode == 409) {
          status = "duplicate";
        } else {
          status = "fail";
          error = message;
        }
      }

      finalResponse.put("name", name);
      finalResponse.put("id", id);
      finalResponse.put("status", status);
      finalResponse.put("message", error);
      if (!objectId.isEmpty()) {
        finalResponse.put("objectId", objectId);
      }
    } catch (Exception e) {
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
    System.out.printf("Init: %d MB\n", bytesToMegabytes(heapMemoryUsage.getInit()));
    System.out.printf("Used: %d MB\n", bytesToMegabytes(heapMemoryUsage.getUsed()));
    System.out.printf("Committed: %d MB\n", bytesToMegabytes(heapMemoryUsage.getCommitted()));
    System.out.printf("Max: %d MB\n", bytesToMegabytes(heapMemoryUsage.getMax()));
    System.out.println("--------------------------------------------------------------------");
  }
}
