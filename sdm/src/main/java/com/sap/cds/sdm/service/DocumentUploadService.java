package com.sap.cds.sdm.service;

import com.sap.cds.sdm.constants.SDMConstants;
import com.sap.cds.sdm.handler.TokenHandler;
import com.sap.cds.sdm.model.CmisDocument;
import com.sap.cds.sdm.model.SDMCredentials;
import com.sap.cds.services.ServiceException;
import com.sap.cds.services.environment.CdsProperties;
import com.sap.cloud.environment.servicebinding.api.ServiceBinding;
import java.io.*;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.util.*;
import org.apache.hc.core5.http.ParseException;
import org.apache.http.HttpEntity;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.mime.MultipartEntityBuilder;
import org.apache.http.entity.mime.content.ContentBody;
import org.apache.http.entity.mime.content.InputStreamBody;
import org.apache.http.util.EntityUtils;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DocumentUploadService {

  MemoryMXBean memoryMXBean;
  private static final Logger logger = LoggerFactory.getLogger(DocumentUploadService.class);
  private final ServiceBinding binding;
  private final CdsProperties.ConnectionPool connectionPool;

  public DocumentUploadService(
      ServiceBinding binding, CdsProperties.ConnectionPool connectionPool) {
    logger.info("DocumentUploadService is instantiated");

    this.connectionPool = connectionPool;
    this.binding = binding;
  }

  /*
   * Implementation to create document.
   */
  public JSONObject createDocument(
      CmisDocument cmisDocument, SDMCredentials sdmCredentials, String jwtToken)
      throws IOException {
    try {
      long totalSize = cmisDocument.getContentLength();
      int chunkSize = SDMConstants.CHUNK_SIZE;

      if (totalSize <= 200 * 1024 * 1024) {
        // Upload directly if file is ≤ 200MB
        return uploadSingleChunk(cmisDocument, sdmCredentials, jwtToken);
      } else {
        String sdmUrl =
            sdmCredentials.getUrl() + "browser/" + cmisDocument.getRepositoryId() + "/root";
        // Upload in chunks if file is > 200MB
        return uploadLargeFileInChunks(cmisDocument, sdmUrl, chunkSize, jwtToken);
      }
    } catch (Exception e) {
      throw new IOException("Error uploading document: " + e.getMessage(), e);
    }
  }

  private void executeHttpPost(
      HttpClient httpClient,
      HttpPost uploadFile,
      CmisDocument cmisDocument,
      Map<String, String> finalResponse)
      throws ServiceException {
    try (CloseableHttpResponse response = (CloseableHttpResponse) httpClient.execute(uploadFile)) {
      formResponse(cmisDocument, finalResponse, response);
    } catch (IOException e) {
      throw new ServiceException("Error in setting timeout", e);
    }
  }

  /*
   * CMIS call to appending content stream
   */
  private void appendContentStream(
      CmisDocument cmisDocument,
      String sdmUrl,
      byte[] chunkBuffer,
      int bytesRead,
      boolean isLastChunk,
      int chunkIndex,
      String jwtToken)
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
        (ContentBody)
            new InputStreamBody(
                new ByteArrayInputStream(chunkBuffer, 0, bytesRead), cmisDocument.getFileName()));

    HttpEntity entity = builder.build();
    Map<String, String> headers = new HashMap<>();
    HttpPost request = new HttpPost(sdmUrl);
    request.setEntity(entity);
    headers.forEach(request::addHeader);

    String subdomain = TokenHandler.getSubdomainFromToken(jwtToken);
    var httpClient =
        TokenHandler.getHttpClient(binding, connectionPool, subdomain, "TOKEN_EXCHANGE");

    Map<String, String> finalResponse = new HashMap<>();

    try {
      this.executeHttpPost(httpClient, request, cmisDocument, finalResponse);

    } catch (Exception e) {
      logger.error("Error in appending content: {}", e.getMessage());
      throw new IOException("Error in appending content: " + e.getMessage(), e);
    }
  }

  private JSONObject createEmptyDocument(CmisDocument cmisDocument, String sdmUrl, String jwtToken)
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

    String subdomain = TokenHandler.getSubdomainFromToken(jwtToken);
    var httpClient =
        TokenHandler.getHttpClient(binding, connectionPool, subdomain, "TOKEN_EXCHANGE");

    Map<String, String> finalResponse = new HashMap<>();
    executeHttpPost(httpClient, request, cmisDocument, finalResponse);

    return new JSONObject(finalResponse);
  }

  public JSONObject uploadSingleChunk(
      CmisDocument cmisDocument, SDMCredentials sdmCredentials, String jwtToken)
      throws IOException {

    InputStream originalStream = cmisDocument.getContent();
    if (originalStream == null) {
      throw new IOException("File stream is null!");
    }

    // Prepare Multipart Request
    String subdomain = TokenHandler.getSubdomainFromToken(jwtToken);
    var httpClient =
        TokenHandler.getHttpClient(binding, connectionPool, subdomain, "TOKEN_EXCHANGE");

    String sdmUrl = sdmCredentials.getUrl() + "browser/" + cmisDocument.getRepositoryId() + "/root";

    MultipartEntityBuilder builder = MultipartEntityBuilder.create();
    builder.addBinaryBody(
        "filename",
        cmisDocument.getContent(),
        ContentType.create(cmisDocument.getMimeType()),
        cmisDocument.getFileName());
    // Add additional form fields
    builder.addTextBody("cmisaction", "createDocument", ContentType.TEXT_PLAIN);
    builder.addTextBody("objectId", cmisDocument.getFolderId(), ContentType.TEXT_PLAIN);
    builder.addTextBody("propertyId[0]", "cmis:name", ContentType.TEXT_PLAIN);
    builder.addTextBody("propertyValue[0]", cmisDocument.getFileName(), ContentType.TEXT_PLAIN);
    builder.addTextBody("propertyId[1]", "cmis:objectTypeId", ContentType.TEXT_PLAIN);
    builder.addTextBody("propertyValue[1]", "cmis:document", ContentType.TEXT_PLAIN);
    builder.addTextBody("succinct", "true", ContentType.TEXT_PLAIN);
    HttpEntity entity = builder.build();
    HttpPost request = new HttpPost(sdmUrl);
    request.setEntity(entity);

    Map<String, String> finalResMap = new HashMap<>();
    executeHttpPost(httpClient, request, cmisDocument, finalResMap);

    return new JSONObject(finalResMap);
  }

  private JSONObject uploadLargeFileInChunks(
      CmisDocument cmisDocument, String sdmUrl, int chunkSize, String jwtToken) throws IOException {

    try (ReadAheadInputStream chunkedStream =
        new ReadAheadInputStream(cmisDocument.getContent(), cmisDocument.getContentLength())) {
      if (chunkedStream == null) {
        throw new IOException("File stream is null!");
      }

      // Step 1: Initial Request (Without Content) and Get `objectId`. It is required to
      // set in every chunk appendContent
      JSONObject responseBody = createEmptyDocument(cmisDocument, sdmUrl, jwtToken);
      logger.info("Response Body: {}", responseBody);

      String objectId = responseBody.getString("objectId");
      cmisDocument.setObjectId(objectId);
      logger.info("objectId of empty doc is {}", objectId);

      // Step 2: Upload Chunks Sequentially
      int chunkIndex = 0;
      byte[] chunkBuffer = new byte[chunkSize];
      int bytesRead;
      boolean hasMoreChunks = true;
      while (hasMoreChunks) {
        long startChunkUploadTime = System.currentTimeMillis();

        // Step 3: Read next chunk
        bytesRead = chunkedStream.read(chunkBuffer, 0, chunkSize);
        logger.info("bytesRead is {}", bytesRead);
        // Step 4: Fetch remaining bytes before checking EOF
        long remainingBytes = chunkedStream.getRemainingBytes();
        logger.info("remainingBytes is {}", remainingBytes);

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
            "Chunk {} | BytesRead: {} | RemainingBytes: {} | isLastChunk? {}",
            chunkIndex,
            bytesRead,
            remainingBytes,
            isLastChunk);

        // Step 7: Append Chunk. Call cmis api to append content stream
        if (bytesRead > 0) {
          appendContentStream(
              cmisDocument, sdmUrl, chunkBuffer, bytesRead, isLastChunk, chunkIndex, jwtToken);
        }

        long endChunkUploadTime = System.currentTimeMillis();
        logger.info(
            "Chunk {} having {} bytes is read and it took {} seconds",
            chunkIndex,
            bytesRead,
            ((int) (endChunkUploadTime - startChunkUploadTime) / 1000));

        chunkIndex++;

        if (isLastChunk) {
          hasMoreChunks = false;
        }
      }
      return responseBody;
    } catch (Exception e) {
      logger.error("Exception in uploadLargeFileInChunks: {}", e.getMessage());
      throw new IOException(
          "Error uploading document in chunks. Make sure you are in stable network during the large file upload");
    }
  }

  private void formResponse(
      CmisDocument cmisDocument,
      Map<String, String> finalResponse,
      CloseableHttpResponse response) {
    String status = "success";
    String name = cmisDocument.getFileName();
    String id = cmisDocument.getAttachmentId();
    String objectId = "";
    String error = "";
    try {
      String responseString = EntityUtils.toString(response.getEntity());
      JSONObject jsonResponse = new JSONObject(responseString);
      int responseCode = response.getStatusLine().getStatusCode();
      if (responseCode == 201 || responseCode == 200) {
        JSONObject succinctProperties = jsonResponse.getJSONObject("succinctProperties");
        status = "success";
        objectId = succinctProperties.getString("cmis:objectId");
      } else {
        String message = jsonResponse.getString("message");
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
      // Construct the final response
      finalResponse.put("name", name);
      finalResponse.put("id", id);
      finalResponse.put("status", status);
      finalResponse.put("message", error);
      if (!objectId.isEmpty()) {
        finalResponse.put("objectId", objectId);
      }
    } catch (IOException e) {
      throw new ServiceException(SDMConstants.getGenericError("upload"));
    }
  }
}
