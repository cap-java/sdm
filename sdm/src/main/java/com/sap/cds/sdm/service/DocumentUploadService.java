package com.sap.cds.sdm.service;

import static com.sap.cds.sdm.constants.SDMConstants.NAMED_USER_FLOW;
import static com.sap.cds.sdm.constants.SDMConstants.TECHNICAL_USER_FLOW;

import com.sap.cds.feature.attachments.service.model.servicehandler.AttachmentCreateEventContext;
import com.sap.cds.reflect.CdsEntity;
import com.sap.cds.reflect.CdsModel;
import com.sap.cds.sdm.constants.SDMConstants;
import com.sap.cds.sdm.constants.SDMErrorMessages;
import com.sap.cds.sdm.handler.TokenHandler;
import com.sap.cds.sdm.model.CmisDocument;
import com.sap.cds.sdm.model.SDMCredentials;
import com.sap.cds.sdm.utilities.SDMUtils;
import com.sap.cds.services.ServiceException;
import com.sap.cds.services.environment.CdsProperties;
import com.sap.cds.services.persistence.PersistenceService;
import com.sap.cloud.environment.servicebinding.api.ServiceBinding;
import java.io.*;
import java.lang.management.MemoryMXBean;
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
  private final TokenHandler tokenHandler;

  public DocumentUploadService(
      ServiceBinding binding,
      CdsProperties.ConnectionPool connectionPool,
      TokenHandler tokenHandler) {
    logger.info("DocumentUploadService is instantiated");

    this.connectionPool = connectionPool;
    this.binding = binding;
    this.tokenHandler = tokenHandler;
  }

  /*
   * Implementation to create document.
   */
  public JSONObject createDocument(
      CmisDocument cmisDocument,
      SDMCredentials sdmCredentials,
      boolean isSystemUser,
      AttachmentCreateEventContext eventContext,
      PersistenceService persistenceService)
      throws IOException {
    try {
      if ("application/internet-shortcut".equalsIgnoreCase(cmisDocument.getMimeType())) {
        logger.info("LinkType detected, uploading as single chunk");
        return uploadSingleChunk(cmisDocument, sdmCredentials, isSystemUser);
      }
      long totalSize = cmisDocument.getContentLength();
      int chunkSize = SDMConstants.CHUNK_SIZE;
      CdsModel model = eventContext.getModel();
      Optional<CdsEntity> attachmentDraftEntity =
          model.findEntity(eventContext.getAttachmentEntity() + "_drafts");
      cmisDocument.setUploadStatus(SDMConstants.UPLOAD_STATUS_IN_PROGRESS);
      if (totalSize <= 400 * 1024 * 1024) {

        // Upload directly if file is ≤ 400MB
        return uploadSingleChunk(cmisDocument, sdmCredentials, isSystemUser);
      } else {
        String sdmUrl =
            sdmCredentials.getUrl() + "browser/" + cmisDocument.getRepositoryId() + "/root";
        // Upload in chunks if file is > 400MB
        return uploadLargeFileInChunks(
            cmisDocument,
            sdmUrl,
            chunkSize,
            isSystemUser,
            attachmentDraftEntity.get(),
            persistenceService);
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
      throw new ServiceException(SDMUtils.getErrorMessage("ERROR_IN_SETTING_TIMEOUT"), e);
    }
  }

  /*
   * CMIS call to appending content stream
   */
  private JSONObject appendContentStream(
      CmisDocument cmisDocument,
      String sdmUrl,
      byte[] chunkBuffer,
      int bytesRead,
      boolean isLastChunk,
      int chunkIndex,
      boolean isSystemUser)
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
    String grantType = isSystemUser ? TECHNICAL_USER_FLOW : NAMED_USER_FLOW;
    var httpClient = tokenHandler.getHttpClient(binding, connectionPool, null, grantType);

    Map<String, String> finalResponse = new HashMap<>();

    try {
      this.executeHttpPost(httpClient, request, cmisDocument, finalResponse);
      cmisDocument.setMimeType(finalResponse.get("mimeType"));
      return new JSONObject(finalResponse);
    } catch (Exception e) {
      logger.error("Error in appending content: {}", e.getMessage());
      throw new IOException("Error in appending content: " + e.getMessage(), e);
    }
  }

  private JSONObject createEmptyDocument(
      CmisDocument cmisDocument, String sdmUrl, boolean isSystemUser) {

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
    String grantType = isSystemUser ? TECHNICAL_USER_FLOW : NAMED_USER_FLOW;
    logger.info("This is a :{} flow", grantType);
    var httpClient = tokenHandler.getHttpClient(binding, connectionPool, null, grantType);

    Map<String, String> finalResponse = new HashMap<>();
    executeHttpPost(httpClient, request, cmisDocument, finalResponse);

    return new JSONObject(finalResponse);
  }

  public JSONObject uploadSingleChunk(
      CmisDocument cmisDocument, SDMCredentials sdmCredentials, boolean isSystemUser)
      throws IOException {

    InputStream originalStream = cmisDocument.getContent();
    if (!cmisDocument.getMimeType().equalsIgnoreCase("application/internet-shortcut")
        && originalStream == null) {
      throw new IOException("File stream is null!");
    }
    String grantType = isSystemUser ? TECHNICAL_USER_FLOW : NAMED_USER_FLOW;
    var httpClient = tokenHandler.getHttpClient(binding, connectionPool, null, grantType);

    String sdmUrl = sdmCredentials.getUrl() + "browser/" + cmisDocument.getRepositoryId() + "/root";

    MultipartEntityBuilder builder = MultipartEntityBuilder.create();
    builder.addTextBody("cmisaction", "createDocument", ContentType.TEXT_PLAIN);
    builder.addTextBody("objectId", cmisDocument.getFolderId(), ContentType.TEXT_PLAIN);
    builder.addTextBody("propertyId[0]", "cmis:name", ContentType.TEXT_PLAIN);
    builder.addTextBody("propertyValue[0]", cmisDocument.getFileName(), ContentType.TEXT_PLAIN);
    builder.addTextBody("propertyId[1]", "cmis:objectTypeId", ContentType.TEXT_PLAIN);
    builder.addTextBody("propertyValue[1]", "cmis:document", ContentType.TEXT_PLAIN);
    builder.addTextBody("succinct", "true", ContentType.TEXT_PLAIN);

    if (cmisDocument.getMimeType().equalsIgnoreCase("application/internet-shortcut")) {
      builder.addTextBody("propertyId[2]", "cmis:secondaryObjectTypeIds", ContentType.TEXT_PLAIN);
      builder.addTextBody("propertyValue[2]", "sap:createLink", ContentType.TEXT_PLAIN);
      builder.addTextBody("propertyId[3]", "sap:linkRepositoryId", ContentType.TEXT_PLAIN);
      builder.addTextBody(
          "propertyValue[3]", cmisDocument.getRepositoryId(), ContentType.TEXT_PLAIN);
      builder.addTextBody("propertyId[4]", "sap:linkExternalURL", ContentType.TEXT_PLAIN);
      builder.addTextBody("propertyValue[4]", cmisDocument.getUrl(), ContentType.TEXT_PLAIN);

    } else {
      builder.addBinaryBody(
          "filename",
          cmisDocument.getContent(),
          ContentType.create(cmisDocument.getMimeType()),
          cmisDocument.getFileName());
    }

    HttpEntity entity = builder.build();
    HttpPost request = new HttpPost(sdmUrl);
    request.setEntity(entity);

    Map<String, String> finalResMap = new HashMap<>();
    executeHttpPost(httpClient, request, cmisDocument, finalResMap);

    return new JSONObject(finalResMap);
  }

  private JSONObject uploadLargeFileInChunks(
      CmisDocument cmisDocument,
      String sdmUrl,
      int chunkSize,
      boolean isSystemUser,
      CdsEntity entity,
      PersistenceService persistenceService)
      throws IOException {

    try (ReadAheadInputStream chunkedStream =
        new ReadAheadInputStream(cmisDocument.getContent(), cmisDocument.getContentLength())) {
      if (chunkedStream == null) {
        throw new IOException("File stream is null!");
      }

      // Step 1: Initial Request (Without Content) and Get `objectId`. It is required to
      // set in every chunk appendContent
      JSONObject responseBody = createEmptyDocument(cmisDocument, sdmUrl, isSystemUser);
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
          responseBody =
              appendContentStream(
                  cmisDocument,
                  sdmUrl,
                  chunkBuffer,
                  bytesRead,
                  isLastChunk,
                  chunkIndex,
                  isSystemUser);
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
    String objectId = "", mimeType = "", scanStatus = "";
    String error = "";
    try {
      String responseString = EntityUtils.toString(response.getEntity());
      int responseCode = response.getStatusLine().getStatusCode();
      if (responseCode == 201 || responseCode == 200) {
        JSONObject jsonResponse = new JSONObject(responseString);
        JSONObject succinctProperties = jsonResponse.getJSONObject("succinctProperties");
        status = "success";
        objectId = succinctProperties.getString("cmis:objectId");
        scanStatus =
            succinctProperties.has("sap:virusScanStatus")
                ? succinctProperties.getString("sap:virusScanStatus")
                : null;
        mimeType =
            succinctProperties.has("cmis:contentStreamMimeType")
                ? succinctProperties.getString("cmis:contentStreamMimeType")
                : null;
      } else {
        if (responseCode == 409) {
          JSONObject jsonResponse = new JSONObject(responseString);
          String message = jsonResponse.getString("message");
          if ("Malware Service Exception: Virus found in the file!".equals(message)) {
            status = "virus";
          } else {
            status = "duplicate";
          }
        } else if ((responseCode == 403)
            && (responseString.equals("User does not have required scope"))) {
          status = "unauthorized";
        } else if (responseCode == 403) {
          JSONObject jsonResponse = new JSONObject(responseString);
          String message = jsonResponse.getString("message");
          if ("MIME type of the uploaded file is blocked according to your repository configuration."
              .equals(message)) status = "blocked";
        } else {
          JSONObject jsonResponse = new JSONObject(responseString);
          String message = jsonResponse.getString("message");
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
        finalResponse.put("mimeType", mimeType);

        // Determine upload status based on scan status using enum
        SDMConstants.ScanStatus scanStatusEnum = SDMConstants.ScanStatus.fromValue(scanStatus);
        String uploadStatus;
        switch (scanStatusEnum) {
          case QUARANTINED:
            uploadStatus = SDMConstants.UPLOAD_STATUS_VIRUS_DETECTED;
            break;
          case SCANNING:
            uploadStatus = SDMConstants.VIRUS_SCAN_INPROGRESS;
            break;
          case FAILED:
            uploadStatus = SDMConstants.UPLOAD_STATUS_SCAN_FAILED;
            break;
          case CLEAN:
            uploadStatus = SDMConstants.UPLOAD_STATUS_SUCCESS;
            break;
          case PENDING:
            uploadStatus = SDMConstants.UPLOAD_STATUS_IN_PROGRESS;
            break;
          case BLANK:
          default:
            uploadStatus = SDMConstants.UPLOAD_STATUS_SUCCESS;
            break;
        }
        finalResponse.put("uploadStatus", uploadStatus);
      }
    } catch (IOException e) {
      throw new ServiceException(
          SDMErrorMessages.getGenericError(SDMUtils.getErrorMessage("EVENT_UPLOAD")), e);
    }
  }
}
