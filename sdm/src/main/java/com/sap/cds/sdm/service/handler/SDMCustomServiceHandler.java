package com.sap.cds.sdm.service.handler;

import com.sap.cds.ql.Insert;
import com.sap.cds.reflect.CdsAssociationType;
import com.sap.cds.reflect.CdsElement;
import com.sap.cds.reflect.CdsEntity;
import com.sap.cds.reflect.CdsModel;
import com.sap.cds.sdm.constants.SDMConstants;
import com.sap.cds.sdm.handler.TokenHandler;
import com.sap.cds.sdm.model.CmisDocument;
import com.sap.cds.sdm.model.CopyAttachmentsRequest;
import com.sap.cds.sdm.model.CreateDraftEntriesRequest;
import com.sap.cds.sdm.model.SDMCredentials;
import com.sap.cds.sdm.persistence.DBQuery;
import com.sap.cds.sdm.service.RegisterService;
import com.sap.cds.sdm.service.SDMAttachmentsService;
import com.sap.cds.sdm.service.SDMService;
import com.sap.cds.services.ServiceException;
import com.sap.cds.services.draft.DraftService;
import com.sap.cds.services.handler.annotations.On;
import com.sap.cds.services.handler.annotations.ServiceName;
import com.sap.cds.services.persistence.PersistenceService;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ServiceName(value = "*", type = RegisterService.class)
public class SDMCustomServiceHandler {
  private final SDMService sdmService;
  private final DBQuery dbQuery;
  private final List<DraftService> draftService;
  private final TokenHandler tokenHandler;
  private final PersistenceService persistenceService;
  private static final Logger logger = LoggerFactory.getLogger(SDMAttachmentsService.class);

  // Result class for copyAttachmentsToSDM method
  private static class CopyAttachmentsResult {
    private final List<List<String>> attachmentsMetadata;
    private final List<CmisDocument> populatedDocuments;

    public CopyAttachmentsResult(
        List<List<String>> attachmentsMetadata, List<CmisDocument> populatedDocuments) {
      this.attachmentsMetadata = attachmentsMetadata;
      this.populatedDocuments = populatedDocuments;
    }

    public List<List<String>> getAttachmentsMetadata() {
      return attachmentsMetadata;
    }

    public List<CmisDocument> getPopulatedDocuments() {
      return populatedDocuments;
    }
  }

  public SDMCustomServiceHandler(
      SDMService sdmService,
      List<DraftService> draftService,
      TokenHandler tokenHandler,
      DBQuery dbQuery,
      PersistenceService persistenceService) {
    this.sdmService = sdmService;
    this.draftService = draftService;
    this.tokenHandler = tokenHandler;
    this.dbQuery = dbQuery;
    this.persistenceService = persistenceService;
  }

  @On(event = RegisterService.EVENT_COPY_ATTACHMENT)
  public void copyAttachments(AttachmentCopyEventContext context) throws IOException {
    String parentEntity = context.getParentEntity();
    String compositionName = context.getCompositionName();
    String upID = context.getUpId();
    String folderName = upID + "__" + compositionName;
    String repositoryId = SDMConstants.REPOSITORY_ID;
    Boolean isSystemUser = context.getSystemUser();
    logger.info(
        "Copying attachments in SDMCustomServiceHandler for upId: {}, facet: {}, objectIds: {}, isSystemUser: {}",
        upID,
        parentEntity + "." + compositionName,
        context.getObjectIds(),
        isSystemUser);

    SDMCredentials sdmCredentials = tokenHandler.getSDMCredentials();
    // Check if folder exists before trying to create it
    boolean folderExists =
        sdmService.getFolderIdByPath(folderName, repositoryId, sdmCredentials, isSystemUser)
            != null;
    logger.info("Folder '{}' exists: {}", folderName, folderExists);
    String folderId = ensureFolderExists(folderName, repositoryId, sdmCredentials, isSystemUser);
    logger.info("Using folder ID: {}", folderId);

    List<String> objectIds = context.getObjectIds();
    logger.info("Object IDs to copy: {}", objectIds);

    CopyAttachmentsRequest request =
        CopyAttachmentsRequest.builder()
            .context(context)
            .objectIds(objectIds)
            .folderId(folderId)
            .repositoryId(repositoryId)
            .sdmCredentials(sdmCredentials)
            .isSystemUser(isSystemUser)
            .folderExists(folderExists)
            .build();
    logger.info(
        "CopyAttachmentsRequest prepared with folderId: {}, repositoryId: {}, isSystemUser: {}",
        folderId,
        repositoryId,
        isSystemUser);

    CopyAttachmentsResult copyResult = copyAttachmentsToSDM(request);
    logger.info("Attachments copied successfully to SDM : " + copyResult.getAttachmentsMetadata());

    List<List<String>> attachmentsMetadata = copyResult.getAttachmentsMetadata();
    List<CmisDocument> populatedDocuments = copyResult.getPopulatedDocuments();

    String upIdKey = resolveUpIdKey(context, parentEntity, compositionName);
    logger.info("Resolved upIdKey: {}", upIdKey);

    CreateDraftEntriesRequest draftRequest =
        CreateDraftEntriesRequest.builder()
            .attachmentsMetadata(attachmentsMetadata)
            .populatedDocuments(populatedDocuments)
            .parentEntity(parentEntity)
            .compositionName(compositionName)
            .upID(upID)
            .upIdKey(upIdKey)
            .repositoryId(repositoryId)
            .folderId(folderId)
            .build();

    createDraftEntries(draftRequest);

    context.setCompleted();
  }

  private String ensureFolderExists(
      String folderName, String repositoryId, SDMCredentials sdmCredentials, Boolean isSystemUser)
      throws IOException {
    String folderId =
        sdmService.getFolderIdByPath(folderName, repositoryId, sdmCredentials, isSystemUser);
    if (folderId == null) {
      folderId =
          sdmService.createFolder(
              folderName, SDMConstants.REPOSITORY_ID, sdmCredentials, isSystemUser);
      JSONObject jsonObject = new JSONObject(folderId);
      JSONObject succinctProperties = jsonObject.getJSONObject("succinctProperties");
      folderId = succinctProperties.getString("cmis:objectId");
    }
    return folderId;
  }

  private CopyAttachmentsResult copyAttachmentsToSDM(CopyAttachmentsRequest request)
      throws IOException {
    List<List<String>> attachmentsMetadata = new ArrayList<>();
    List<CmisDocument> populatedDocuments = new ArrayList<>();

    for (String objectId : request.getObjectIds()) {
      CmisDocument cmisDocument =
          dbQuery.getAttachmentForObjectID(persistenceService, objectId, request.getContext());
      cmisDocument.setObjectId(objectId);
      cmisDocument.setRepositoryId(request.getRepositoryId());
      cmisDocument.setFolderId(request.getFolderId());

      // Create individual document for each attachment with its own type and linkUrl
      CmisDocument populatedDocument = new CmisDocument();
      populatedDocument.setType(cmisDocument.getType());
      populatedDocument.setUrl(cmisDocument.getUrl());
      populatedDocuments.add(populatedDocument);

      try {
        attachmentsMetadata.add(
            sdmService.copyAttachment(
                cmisDocument, request.getSdmCredentials(), request.getIsSystemUser()));
        logger.info("Attachments metadata after copying: {}", attachmentsMetadata);
      } catch (ServiceException e) {
        logger.info("Copy failure : " + e);
        logger.info("Attachments metadata after copy failure : " + attachmentsMetadata);
        handleCopyFailure(
            request.getContext(),
            request.getFolderId(),
            request.isFolderExists(),
            attachmentsMetadata,
            e);
      }
    }

    return new CopyAttachmentsResult(attachmentsMetadata, populatedDocuments);
  }

  private void handleCopyFailure(
      AttachmentCopyEventContext context,
      String folderId,
      boolean folderExists,
      List<List<String>> attachmentsMetadata,
      ServiceException e)
      throws IOException {
    if (!folderExists) {
      sdmService.deleteDocument("deleteTree", folderId, context.getUserInfo().getName());
    } else {
      for (List<String> attachmentMetadata : attachmentsMetadata) {
        sdmService.deleteDocument(
            "delete", attachmentMetadata.get(2), context.getUserInfo().getName());
      }
    }
    throw new ServiceException(e.getMessage());
  }

  private String resolveUpIdKey(
      AttachmentCopyEventContext context, String parentEntity, String compositionName) {
    CdsModel model = context.getModel();
    Optional<CdsEntity> optionalParentEntity = model.findEntity(parentEntity);
    if (optionalParentEntity.isEmpty()) {
      throw new ServiceException("Unable to find parent entity: " + parentEntity);
    }

    Optional<CdsElement> compositionElement =
        optionalParentEntity.get().findElement(compositionName);
    if (compositionElement.isEmpty() || !compositionElement.get().getType().isAssociation()) {
      throw new ServiceException(
          "Unable to find composition '" + compositionName + "' in entity: " + parentEntity);
    }

    CdsAssociationType assocType = (CdsAssociationType) compositionElement.get().getType();
    String targetEntityName = assocType.getTarget().getQualifiedName();

    Optional<CdsEntity> attachmentDraftEntity = model.findEntity(targetEntityName + "_drafts");
    if (attachmentDraftEntity.isPresent()) {
      Optional<CdsElement> upAssociation = attachmentDraftEntity.get().findAssociation("up_");
      if (upAssociation.isPresent()) {
        CdsElement association = upAssociation.get();
        CdsAssociationType upAssocType = association.getType();
        List<String> fkElements = upAssocType.refs().map(ref -> "up__" + ref.path()).toList();
        String upIdKey = fkElements.get(0);
        return upIdKey;
      }
    }
    return null;
  }

  private void createDraftEntries(CreateDraftEntriesRequest request) {

    for (int i = 0; i < request.getAttachmentsMetadata().size(); i++) {
      List<String> attachmentMetadata = request.getAttachmentsMetadata().get(i);
      CmisDocument cmisDocument = request.getPopulatedDocuments().get(i);
      Map<String, Object> updatedFields = new HashMap<>();

      String fileName = attachmentMetadata.get(0);
      String mimeType = attachmentMetadata.get(1);
      String newObjectId = attachmentMetadata.get(2);
      logger.info(
          "Creating draft entry for attachment - fileName: {}, mimeType: {}, newObjectId: {}",
          fileName,
          mimeType,
          newObjectId);

      updatedFields.put("objectId", newObjectId);
      updatedFields.put("repositoryId", request.getRepositoryId());
      updatedFields.put("folderId", request.getFolderId());
      updatedFields.put("status", "Clean");
      updatedFields.put("mimeType", mimeType);
      updatedFields.put("type", cmisDocument.getType()); // Individual type for each attachment
      updatedFields.put("fileName", fileName);
      updatedFields.put("HasDraftEntity", false);
      updatedFields.put("HasActiveEntity", false);
      updatedFields.put("linkUrl", cmisDocument.getUrl()); // Individual linkUrl for each attachment
      updatedFields.put(
          "contentId",
          newObjectId
              + ":"
              + request.getFolderId()
              + ":"
              + request.getParentEntity()
              + "."
              + request.getCompositionName()
              + ":"
              + mimeType);
      updatedFields.put(request.getUpIdKey(), request.getUpID());

      String baseKeyField =
          request.getUpIdKey() != null ? request.getUpIdKey().replace("up__", "") : "ID";
      var insert =
          Insert.into(
                  request.getParentEntity(),
                  e ->
                      e.filter(e.get(baseKeyField).eq(request.getUpID()))
                          .to(request.getCompositionName()))
              .entry(updatedFields);

      DraftService matchingService =
          draftService.stream()
              .filter(ds -> request.getParentEntity().contains(ds.getName()))
              .findFirst()
              .orElse(null);

      if (matchingService != null) {
        matchingService.newDraft(insert);
      } else {
        throw new ServiceException(
            "No suitable service found for entity: " + request.getParentEntity());
      }
    }
  }
}
