package com.sap.cds.sdm.service.handler;

import com.sap.cds.ql.Insert;
import com.sap.cds.reflect.CdsAssociationType;
import com.sap.cds.reflect.CdsElement;
import com.sap.cds.reflect.CdsEntity;
import com.sap.cds.reflect.CdsModel;
import com.sap.cds.sdm.constants.SDMConstants;
import com.sap.cds.sdm.handler.TokenHandler;
import com.sap.cds.sdm.model.CmisDocument;
import com.sap.cds.sdm.model.SDMCredentials;
import com.sap.cds.sdm.persistence.DBQuery;
import com.sap.cds.sdm.service.RegisterService;
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

@ServiceName(value = "*", type = RegisterService.class)
public class SDMCustomServiceHandler {
  private final SDMService sdmService;
  private final List<DraftService> draftService;
  private final TokenHandler tokenHandler;
  private final DBQuery dbQuery;
  private final PersistenceService persistenceService;

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

  // @On(event = RegisterService.EVENT_COPY_ATTACHMENT)
  // public void copyAttachments(AttachmentCopyEventContext context) throws IOException {
  //   String[] splitFacet = context.getFacet().split("\\.");
  //   if (splitFacet.length < 3) {
  //     throw new ServiceException(SDMConstants.FAILED_TO_FETCH_FACET);
  //   }
  //   String facet = splitFacet[2];
  //   String upID = context.getUpId();
  //   String folderName = upID + "__" + facet;
  //   String repositoryId = SDMConstants.REPOSITORY_ID;
  //   Boolean isSystemUser = context.getSystemUser();
  //   boolean folderExists = true;

  //   SDMCredentials sdmCredentials = tokenHandler.getSDMCredentials();
  //   String folderId =
  //       sdmService.getFolderIdByPath(folderName, repositoryId, sdmCredentials, isSystemUser);
  //   if (folderId == null) {
  //     folderExists = false;
  //     folderId =
  //         sdmService.createFolder(
  //             folderName, SDMConstants.REPOSITORY_ID, sdmCredentials, isSystemUser);
  //     JSONObject jsonObject = new JSONObject(folderId);
  //     JSONObject succinctProperties = jsonObject.getJSONObject("succinctProperties");
  //     folderId = succinctProperties.getString("cmis:objectId");
  //   }
  //   CmisDocument cmisDocument = new CmisDocument();
  //   cmisDocument.setRepositoryId(repositoryId);
  //   cmisDocument.setFolderId(folderId);
  //   List<String> objectIds = context.getObjectIds();
  //   List<List<String>> attachmentsMetadata = new ArrayList<>();
  //   for (String objectId : objectIds) {
  //     cmisDocument.setObjectId(objectId);
  //     try {
  //       attachmentsMetadata.add(
  //           sdmService.copyAttachment(cmisDocument, sdmCredentials, isSystemUser));
  //     } catch (ServiceException e) {
  //       if (!folderExists) {
  //         // deleteFolder
  //         sdmService.deleteDocument("deleteTree", folderId, context.getUserInfo().getName());
  //         throw new ServiceException(e.getMessage());
  //       } else {
  //         for (List<String> attachmentMetadata : attachmentsMetadata) {
  //           // delete the copied attachments
  //           sdmService.deleteDocument(
  //               "delete", attachmentMetadata.get(2), context.getUserInfo().getName());
  //         }
  //         throw new ServiceException(e.getMessage());
  //       }
  //     }
  //   }

  //   String upIdKey = "";
  //   CdsModel model = context.getModel();
  //   Optional<CdsEntity> attachmentDraftEntity = model.findEntity(context.getFacet() + "_drafts");
  //   Optional<CdsElement> upAssociation = attachmentDraftEntity.get().findAssociation("up_");
  //   if (upAssociation.isPresent()) {
  //     CdsElement association = upAssociation.get();
  //     CdsAssociationType assocType = association.getType();
  //     List<String> fkElements = assocType.refs().map(ref -> "up__" + ref.path()).toList();
  //     upIdKey = fkElements.get(0);
  //   }
  //   Map<String, Object> updatedFields = new HashMap<>();
  //   for (List<String> attachmentMetadata : attachmentsMetadata) {
  //     String fileName = attachmentMetadata.get(0);
  //     String mimeType = attachmentMetadata.get(1);
  //     String newObjectId = attachmentMetadata.get(2);
  //     updatedFields.put("objectId", newObjectId);
  //     updatedFields.put("repositoryId", repositoryId);
  //     updatedFields.put("folderId", folderId);
  //     updatedFields.put("status", "Clean");
  //     updatedFields.put("mimeType", mimeType);
  //     updatedFields.put("fileName", fileName);
  //     updatedFields.put("HasDraftEntity", false);
  //     updatedFields.put("HasActiveEntity", false);
  //     updatedFields.put(
  //         "contentId", newObjectId + ":" + folderId + ":" + context.getFacet() + ":" + mimeType);
  //     updatedFields.put(upIdKey, upID);

  //     var insert = Insert.into(context.getFacet()).entry(updatedFields);
  //     for (DraftService draftS : draftService) {
  //       // Check if the draft service name matches the context facet
  //       if (context.getFacet().contains(draftS.getName())) {
  //         draftS.newDraft(insert);
  //       }
  //     }
  //   }
  //   context.setCompleted();
  // }

  // @On(event = RegisterService.EVENT_COPY_ATTACHMENT)
  // public void copyAttachments(AttachmentCopyEventContext context) throws IOException {

  //   System.out.println("Inside copyAttachments handler of SDMCustomServiceHandler");
  //   String parentEntity = context.getParentEntity();
  //   System.out.println("parentEntity: " + parentEntity);
  //   String compositionName = context.getCompositionName();
  //   System.out.println("compositionName: " + compositionName);
  //   String upID = context.getUpId();
  //   System.out.println("upID: " + upID);
  //   String folderName = upID + "__" + compositionName;
  //   System.out.println("folderName: " + folderName);
  //   String repositoryId = SDMConstants.REPOSITORY_ID;
  //   System.out.println("repositoryId: " + repositoryId);
  //   Boolean isSystemUser = context.getSystemUser();
  //   System.out.println("isSystemUser: " + isSystemUser);
  //   boolean folderExists = true;

  //   SDMCredentials sdmCredentials = tokenHandler.getSDMCredentials();
  //   String folderId =
  //       sdmService.getFolderIdByPath(folderName, repositoryId, sdmCredentials, isSystemUser);
  //   System.out.println("folderId: " + folderId);
  //   if (folderId == null) {
  //     folderExists = false;
  //     folderId =
  //         sdmService.createFolder(
  //             folderName, SDMConstants.REPOSITORY_ID, sdmCredentials, isSystemUser);
  //     JSONObject jsonObject = new JSONObject(folderId);
  //     JSONObject succinctProperties = jsonObject.getJSONObject("succinctProperties");
  //     folderId = succinctProperties.getString("cmis:objectId");
  //     System.out.println("Created new folder with folderId: " + folderId);
  //   }
  //   CmisDocument cmisDocument = new CmisDocument();

  //   List<String> objectIds = context.getObjectIds();
  //   System.out.println("objectIds: " + objectIds);
  //   List<List<String>> attachmentsMetadata = new ArrayList<>();
  //   for (String objectId : objectIds) {
  //     // get Link Url from objectId and set to cmisDocument
  //     cmisDocument = dbQuery.getAttachmentForObjectID(persistenceService, objectId, context);
  //     System.out.println("cmisDocument: " + cmisDocument);
  //     cmisDocument.setObjectId(objectId);
  //     cmisDocument.setRepositoryId(repositoryId);
  //     cmisDocument.setFolderId(folderId);
  //     try {
  //       attachmentsMetadata.add(
  //           sdmService.copyAttachment(cmisDocument, sdmCredentials, isSystemUser));
  //     } catch (ServiceException e) {
  //       if (!folderExists) {
  //         // deleteFolder
  //         System.out.println(
  //             "Exception occurred, deleting created folder with folderId: " + folderId);
  //         sdmService.deleteDocument("deleteTree", folderId, context.getUserInfo().getName());
  //         throw new ServiceException(e.getMessage());
  //       } else {
  //         System.out.println("Exception occurred, deleting copied attachments");
  //         for (List<String> attachmentMetadata : attachmentsMetadata) {
  //           // delete the copied attachments
  //           sdmService.deleteDocument(
  //               "delete", attachmentMetadata.get(2), context.getUserInfo().getName());
  //         }
  //         throw new ServiceException(e.getMessage());
  //       }
  //     }
  //   }

  //   // Find the parent entity's draft table and composition
  //   String upIdKey = null;
  //   CdsModel model = context.getModel();

  //   Optional<CdsEntity> optionalParentEntity = model.findEntity(parentEntity);
  //   System.out.println("optionalParentEntity: " + optionalParentEntity);
  //   if (optionalParentEntity.isEmpty()) {
  //     throw new ServiceException("Unable to find parent entity: " + parentEntity);
  //   }

  //   // Find the composition element in the parent draft entity
  //   Optional<CdsElement> compositionElement =
  //       optionalParentEntity.get().findElement(compositionName);
  //   System.out.println("compositionElement: " + compositionElement);
  //   if (compositionElement.isEmpty() || !compositionElement.get().getType().isAssociation()) {
  //     throw new ServiceException(
  //         "Unable to find composition '" + compositionName + "' in entity: " + parentEntity);
  //   }

  //   // Get the target entity of the composition
  //   CdsAssociationType assocType = (CdsAssociationType) compositionElement.get().getType();
  //   System.out.println("assocType: " + assocType);
  //   String targetEntityName = assocType.getTarget().getQualifiedName();
  //   System.out.println("targetEntityName: " + targetEntityName);

  //   // Find the target entity's draft table to get upIdKey
  //   Optional<CdsEntity> attachmentDraftEntity = model.findEntity(targetEntityName + "_drafts");
  //   System.out.println("attachmentDraftEntity: " + attachmentDraftEntity);
  //   if (attachmentDraftEntity.isPresent()) {
  //     Optional<CdsElement> upAssociation = attachmentDraftEntity.get().findAssociation("up_");
  //     System.out.println("upAssociation: " + upAssociation);
  //     if (upAssociation.isPresent()) {
  //       CdsElement association = upAssociation.get();
  //       System.out.println("association: " + association);
  //       CdsAssociationType upAssocType = association.getType();
  //       System.out.println("upAssocType: " + upAssocType);
  //       List<String> fkElements = upAssocType.refs().map(ref -> "up__" + ref.path()).toList();
  //       System.out.println("fkElements: " + fkElements);
  //       upIdKey = fkElements.get(0);
  //       System.out.println("upIdKey: " + upIdKey);
  //     }
  //   }

  //   final String finalUpIdKey = upIdKey;

  //   // Process attachments with new Insert pattern
  //   Map<String, Object> updatedFields = new HashMap<>();
  //   for (List<String> attachmentMetadata : attachmentsMetadata) {
  //     String fileName = attachmentMetadata.get(0);
  //     System.out.println("fileName: " + fileName);
  //     String mimeType = attachmentMetadata.get(1);
  //     System.out.println("mimeType: " + mimeType);
  //     if (mimeType.equalsIgnoreCase("application/internet-shortcut")) {
  //       int dotIndex = fileName.lastIndexOf('.');
  //       fileName = fileName.substring(0, dotIndex);
  //     }
  //     String newObjectId = attachmentMetadata.get(2);

  //     updatedFields.put("objectId", newObjectId);
  //     updatedFields.put("repositoryId", repositoryId);
  //     updatedFields.put("folderId", folderId);
  //     updatedFields.put("status", "Clean");
  //     updatedFields.put("mimeType", mimeType);
  //     // updatedFields.put("type", cmisDocument.getType());
  //     updatedFields.put("fileName", fileName);
  //     updatedFields.put("HasDraftEntity", false);
  //     updatedFields.put("HasActiveEntity", false);
  //     // updatedFields.put("linkUrl", cmisDocument.getUrl());
  //     updatedFields.put(
  //         "contentId",
  //         newObjectId
  //             + ":"
  //             + folderId
  //             + ":"
  //             + parentEntity
  //             + "."
  //             + compositionName
  //             + ":"
  //             + mimeType);
  //     updatedFields.put(upIdKey, upID);

  //     // Use the recommended Insert pattern for projection entities
  //     // Remove "up__" prefix from finalUpIdKey for the filter condition
  //     String baseKeyField = finalUpIdKey != null ? finalUpIdKey.replace("up__", "") : "ID";
  //     var insert =
  //         Insert.into(parentEntity, e ->
  // e.filter(e.get(baseKeyField).eq(upID)).to(compositionName))
  //             .entry(updatedFields);
  //     System.out.println("Insert: " + insert);
  //     System.out.println("Using baseKeyField: " + baseKeyField + " for filter");

  //     DraftService matchingService =
  //         draftService.stream()
  //             .filter(ds -> parentEntity.contains(ds.getName()))
  //             .findFirst()
  //             .orElse(null);

  //     if (matchingService != null) {
  //       System.out.println("Using DraftService: " + matchingService.getName());
  //       matchingService.newDraft(insert);
  //     } else {
  //       throw new ServiceException("No suitable service found for entity: " + parentEntity);
  //     }
  //   }
  //   context.setCompleted();
  // }

  @On(event = RegisterService.EVENT_COPY_ATTACHMENT)
  public void copyAttachments(AttachmentCopyEventContext context) throws IOException {

    System.out.println("Inside copyAttachments handler of SDMCustomServiceHandler");
    String parentEntity = context.getParentEntity();
    System.out.println("parentEntity: " + parentEntity);
    String compositionName = context.getCompositionName();
    System.out.println("compositionName: " + compositionName);
    String upID = context.getUpId();
    System.out.println("upID: " + upID);
    String folderName = upID + "__" + compositionName;
    System.out.println("folderName: " + folderName);
    String repositoryId = SDMConstants.REPOSITORY_ID;
    System.out.println("repositoryId: " + repositoryId);
    Boolean isSystemUser = context.getSystemUser();
    System.out.println("isSystemUser: " + isSystemUser);

    SDMCredentials sdmCredentials = tokenHandler.getSDMCredentials();
    // Check if folder exists before trying to create it
    boolean folderExists =
        sdmService.getFolderIdByPath(folderName, repositoryId, sdmCredentials, isSystemUser)
            != null;
    String folderId = ensureFolderExists(folderName, repositoryId, sdmCredentials, isSystemUser);

    List<String> objectIds = context.getObjectIds();
    System.out.println("objectIds: " + objectIds);

    CopyAttachmentsResult copyResult =
        copyAttachmentsToSDM(
            context, objectIds, folderId, repositoryId, sdmCredentials, isSystemUser, folderExists);

    List<List<String>> attachmentsMetadata = copyResult.getAttachmentsMetadata();
    List<CmisDocument> populatedDocuments = copyResult.getPopulatedDocuments();
    System.out.println("attachmentsMetadata: " + attachmentsMetadata);

    String upIdKey = resolveUpIdKey(context, parentEntity, compositionName);
    System.out.println("upIdKey: " + upIdKey);
    createDraftEntries(
        attachmentsMetadata,
        populatedDocuments,
        parentEntity,
        compositionName,
        upID,
        upIdKey,
        repositoryId,
        folderId);

    context.setCompleted();
  }

  private String ensureFolderExists(
      String folderName, String repositoryId, SDMCredentials sdmCredentials, Boolean isSystemUser)
      throws IOException {
    String folderId =
        sdmService.getFolderIdByPath(folderName, repositoryId, sdmCredentials, isSystemUser);
    System.out.println("folderId: " + folderId);
    if (folderId == null) {
      folderId =
          sdmService.createFolder(
              folderName, SDMConstants.REPOSITORY_ID, sdmCredentials, isSystemUser);
      JSONObject jsonObject = new JSONObject(folderId);
      JSONObject succinctProperties = jsonObject.getJSONObject("succinctProperties");
      folderId = succinctProperties.getString("cmis:objectId");
      System.out.println("Created new folder with folderId: " + folderId);
    }
    return folderId;
  }

  private CopyAttachmentsResult copyAttachmentsToSDM(
      AttachmentCopyEventContext context,
      List<String> objectIds,
      String folderId,
      String repositoryId,
      SDMCredentials sdmCredentials,
      Boolean isSystemUser,
      boolean folderExists)
      throws IOException {
    List<List<String>> attachmentsMetadata = new ArrayList<>();
    List<CmisDocument> populatedDocuments = new ArrayList<>();

    for (String objectId : objectIds) {
      CmisDocument cmisDocument =
          dbQuery.getAttachmentForObjectID(persistenceService, objectId, context);
      System.out.println("cmisDocument: " + cmisDocument);
      cmisDocument.setObjectId(objectId);
      cmisDocument.setRepositoryId(repositoryId);
      cmisDocument.setFolderId(folderId);

      // Create individual document for each attachment with its own type and linkUrl
      CmisDocument populatedDocument = new CmisDocument();
      // populatedDocument.setType(cmisDocument.getType());
      // populatedDocument.setUrl(cmisDocument.getUrl());
      populatedDocuments.add(populatedDocument);

      try {
        attachmentsMetadata.add(
            sdmService.copyAttachment(cmisDocument, sdmCredentials, isSystemUser));
      } catch (ServiceException e) {
        handleCopyFailure(context, folderId, folderExists, attachmentsMetadata, e);
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
      System.out.println("Exception occurred, deleting created folder with folderId: " + folderId);
      sdmService.deleteDocument("deleteTree", folderId, context.getUserInfo().getName());
    } else {
      System.out.println("Exception occurred, deleting copied attachments");
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
    System.out.println("optionalParentEntity: " + optionalParentEntity);
    if (optionalParentEntity.isEmpty()) {
      throw new ServiceException("Unable to find parent entity: " + parentEntity);
    }

    Optional<CdsElement> compositionElement =
        optionalParentEntity.get().findElement(compositionName);
    System.out.println("compositionElement: " + compositionElement);
    if (compositionElement.isEmpty() || !compositionElement.get().getType().isAssociation()) {
      throw new ServiceException(
          "Unable to find composition '" + compositionName + "' in entity: " + parentEntity);
    }

    CdsAssociationType assocType = (CdsAssociationType) compositionElement.get().getType();
    System.out.println("assocType: " + assocType);
    String targetEntityName = assocType.getTarget().getQualifiedName();
    System.out.println("targetEntityName: " + targetEntityName);

    Optional<CdsEntity> attachmentDraftEntity = model.findEntity(targetEntityName + "_drafts");
    System.out.println("attachmentDraftEntity: " + attachmentDraftEntity);
    if (attachmentDraftEntity.isPresent()) {
      Optional<CdsElement> upAssociation = attachmentDraftEntity.get().findAssociation("up_");
      System.out.println("upAssociation: " + upAssociation);
      if (upAssociation.isPresent()) {
        CdsElement association = upAssociation.get();
        System.out.println("association: " + association);
        CdsAssociationType upAssocType = association.getType();
        System.out.println("upAssocType: " + upAssocType);
        List<String> fkElements = upAssocType.refs().map(ref -> "up__" + ref.path()).toList();
        System.out.println("fkElements: " + fkElements);
        String upIdKey = fkElements.get(0);
        System.out.println("upIdKey: " + upIdKey);
        return upIdKey;
      }
    }
    return null;
  }

  private void createDraftEntries(
      List<List<String>> attachmentsMetadata,
      List<CmisDocument> populatedDocuments,
      String parentEntity,
      String compositionName,
      String upID,
      String upIdKey,
      String repositoryId,
      String folderId) {

    for (int i = 0; i < attachmentsMetadata.size(); i++) {
      List<String> attachmentMetadata = attachmentsMetadata.get(i);
      CmisDocument cmisDocument = populatedDocuments.get(i);
      Map<String, Object> updatedFields = new HashMap<>();

      String fileName = attachmentMetadata.get(0);
      System.out.println("fileName: " + fileName);
      String mimeType = attachmentMetadata.get(1);
      System.out.println("mimeType: " + mimeType);
      if (mimeType.equalsIgnoreCase("application/internet-shortcut")) {
        int dotIndex = fileName.lastIndexOf('.');
        fileName = fileName.substring(0, dotIndex);
      }
      String newObjectId = attachmentMetadata.get(2);

      updatedFields.put("objectId", newObjectId);
      updatedFields.put("repositoryId", repositoryId);
      updatedFields.put("folderId", folderId);
      updatedFields.put("status", "Clean");
      updatedFields.put("mimeType", mimeType);
      // updatedFields.put("type", cmisDocument.getType()); // Individual type for each attachment
      updatedFields.put("fileName", fileName);
      updatedFields.put("HasDraftEntity", false);
      updatedFields.put("HasActiveEntity", false);
      // updatedFields.put("linkUrl", cmisDocument.getUrl()); // Individual linkUrl for each
      // attachment
      updatedFields.put(
          "contentId",
          newObjectId
              + ":"
              + folderId
              + ":"
              + parentEntity
              + "."
              + compositionName
              + ":"
              + mimeType);
      updatedFields.put(upIdKey, upID);
      System.out.println("updatedFields: " + updatedFields);

      String baseKeyField = upIdKey != null ? upIdKey.replace("up__", "") : "ID";
      var insert =
          Insert.into(parentEntity, e -> e.filter(e.get(baseKeyField).eq(upID)).to(compositionName))
              .entry(updatedFields);
      System.out.println("Insert: " + insert);
      System.out.println("Using baseKeyField: " + baseKeyField + " for filter");

      DraftService matchingService =
          draftService.stream()
              .filter(ds -> parentEntity.contains(ds.getName()))
              .findFirst()
              .orElse(null);

      if (matchingService != null) {
        System.out.println("Using DraftService: " + matchingService.getName());
        matchingService.newDraft(insert);
      } else {
        throw new ServiceException("No suitable service found for entity: " + parentEntity);
      }
    }
  }
}
