package com.sap.cds.sdm.handler.applicationservice;

import com.sap.cds.CdsData;
import com.sap.cds.Result;
import com.sap.cds.ql.CQL;
import com.sap.cds.ql.Predicate;
import com.sap.cds.ql.cqn.CqnSelect;
import com.sap.cds.ql.cqn.Modifier;
import com.sap.cds.reflect.CdsAnnotation;
import com.sap.cds.reflect.CdsAssociationType;
import com.sap.cds.reflect.CdsElement;
import com.sap.cds.reflect.CdsElementDefinition;
import com.sap.cds.reflect.CdsEntity;
import com.sap.cds.reflect.CdsModel;
import com.sap.cds.sdm.caching.CacheConfig;
import com.sap.cds.sdm.caching.ErrorMessageKey;
import com.sap.cds.sdm.constants.SDMConstants;
import com.sap.cds.sdm.constants.SDMErrorMessages;
import com.sap.cds.sdm.constants.SDMUIErrorKeys;
import com.sap.cds.sdm.handler.TokenHandler;
import com.sap.cds.sdm.handler.applicationservice.helper.SDMBeforeReadItemsModifier;
import com.sap.cds.sdm.handler.common.SDMApplicationHandlerHelper;
import com.sap.cds.sdm.model.CmisDocument;
import com.sap.cds.sdm.model.RepoValue;
import com.sap.cds.sdm.model.SDMCredentials;
import com.sap.cds.sdm.persistence.DBQuery;
import com.sap.cds.sdm.service.SDMService;
import com.sap.cds.sdm.utilities.SDMUtils;
import com.sap.cds.services.cds.ApplicationService;
import com.sap.cds.services.cds.CdsReadEventContext;
import com.sap.cds.services.draft.Drafts;
import com.sap.cds.services.handler.EventHandler;
import com.sap.cds.services.handler.annotations.After;
import com.sap.cds.services.handler.annotations.Before;
import com.sap.cds.services.handler.annotations.HandlerOrder;
import com.sap.cds.services.handler.annotations.ServiceName;
import com.sap.cds.services.persistence.PersistenceService;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.stream.Collectors;
import org.ehcache.Cache;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ServiceName(value = "*", type = ApplicationService.class)
public class SDMReadAttachmentsHandler implements EventHandler {

  private static final Logger logger = LoggerFactory.getLogger(SDMReadAttachmentsHandler.class);

  private final PersistenceService persistenceService;
  private final SDMService sdmService;
  private final TokenHandler tokenHandler;
  private final DBQuery dbQuery;

  public SDMReadAttachmentsHandler(
      PersistenceService persistenceService,
      SDMService sdmService,
      TokenHandler tokenHandler,
      DBQuery dbQuery) {
    this.persistenceService = persistenceService;
    this.sdmService = sdmService;
    this.tokenHandler = tokenHandler;
    this.dbQuery = dbQuery;
  }

  /*
    Error message caching requires the CAP context to retrieve localized messages, which may not be
    available at all error throw sites. To ensure availability, error messages are cached during the
    before read event when the context is guaranteed to be present.
  */
  private void setErrorMessagesInCache(CdsReadEventContext context) {
    logger.debug("Setting error messages in cache");
    // Check if cache is available
    Cache<ErrorMessageKey, String> errorMessageCache = CacheConfig.getErrorMessageCache();
    if (errorMessageCache == null) {
      logger.debug("Error message cache not initialized, skipping");
      return; // Cache not initialized, skip
    }

    // Check if localized error messages are already cached
    ErrorMessageKey cacheCheckKey = new ErrorMessageKey();
    cacheCheckKey.setKey("localizedErrorMessagesSetInCache");
    String cacheValue = errorMessageCache.get(cacheCheckKey);

    if ("true".equals(cacheValue)) {
      logger.debug("Error messages already cached, skipping");
      return; // Skip processing if already cached
    }

    Map<String, Object> errorMessages = SDMErrorMessages.getAllErrorMessages();
    Map<String, Object> errorKeys = SDMUIErrorKeys.getAllUIErrorKeys();
    logger.debug("Caching {} error messages", errorMessages.size());
    String localizedMessage;
    String localizedErrorMessageKey;
    for (Map.Entry<String, Object> entry : errorMessages.entrySet()) {
      String errorMessage = entry.getKey();
      Object errorValue = entry.getValue();
      localizedErrorMessageKey = String.valueOf(errorKeys.get(errorMessage + "_KEY"));
      localizedMessage =
          context
              .getCdsRuntime()
              .getLocalizedMessage(
                  localizedErrorMessageKey, null, context.getParameterInfo().getLocale());
      ErrorMessageKey errorMessageKey = new ErrorMessageKey();
      errorMessageKey.setKey(errorMessage);
      errorMessageCache.put(
          errorMessageKey,
          java.util.Objects.equals(localizedMessage, localizedErrorMessageKey)
              ? String.valueOf(errorValue)
              : localizedMessage);
    }

    // Mark that localized error messages have been cached
    errorMessageCache.put(cacheCheckKey, "true");
    logger.debug("Error messages cached successfully");
  }

  @Before
  @HandlerOrder(HandlerOrder.EARLY + 500)
  public void processBefore(CdsReadEventContext context) throws IOException {
    logger.info("Processing read request for entity: {}", context.getTarget().getQualifiedName());
    logger.debug(
        "START: Reading attachments for entity: {}", context.getTarget().getQualifiedName());
    String repositoryId = SDMConstants.REPOSITORY_ID;
    if (repositoryId == null) {
      logger.debug("Repository ID is null, skipping processing");
      return;
    }
    setErrorMessagesInCache(context);
    if (context.getTarget().getAnnotationValue(SDMConstants.ANNOTATION_IS_MEDIA_DATA, false)) {
      try {
        // update the uploadStatus of all blank attachments with success this is for existing
        // attachments
        logger.debug("Target is a media entity, processing attachment logic");
        RepoValue repoValue = checkRepositoryTypeWithFallback(repositoryId, context);

        // Only process virus scan logic if repository info is available
        if (repoValue != null) {
          logger.debug(
              "Repository value found. Async virus scan enabled: {}",
              repoValue.getIsAsyncVirusScanEnabled());
          Optional<CdsEntity> attachmentDraftEntity =
              context.getModel().findEntity(context.getTarget().getQualifiedName() + "_drafts");
          String upIdKey = "", upID = "";
          if (attachmentDraftEntity.isPresent()) {
            upIdKey = SDMUtils.getUpIdKey(attachmentDraftEntity.get());
            CqnSelect select = (CqnSelect) context.get("cqn");
            upID = SDMUtils.fetchUPIDFromCQN(select, attachmentDraftEntity.get());
            logger.debug("Processing attachments for upID: {}", upID);

            if (!repoValue.getIsAsyncVirusScanEnabled()) {
              logger.debug("Sync virus scan mode: updating in-progress upload status to success");
              dbQuery.updateInProgressUploadStatusToSuccess(
                  attachmentDraftEntity.get(), persistenceService, upID, upIdKey);
            }
            if (repoValue.getIsAsyncVirusScanEnabled()) {
              logger.debug("Async virus scan mode: processing virus scan in-progress attachments");
              processVirusScanInProgressAttachments(context, upID, upIdKey);
            }
          }

          // Get attachment associations to handle deep reads with expand
          CdsModel cdsModel = context.getModel();
          List<String> fieldNames =
              getAttachmentAssociations(cdsModel, context.getTarget(), "", new ArrayList<>());
          logger.debug("Found {} attachment associations", fieldNames.size());

          // Create a combined modifier that handles both expand scenarios and repositoryId filter
          final SDMBeforeReadItemsModifier itemsModifier =
              new SDMBeforeReadItemsModifier(fieldNames);
          final Predicate repositoryFilter =
              CQL.or(CQL.get("repositoryId").eq(repositoryId), CQL.get("repositoryId").isNull());
          logger.debug(
              "Creating CQN modifier with {} field names and repository filter", fieldNames.size());

          CqnSelect modifiedCqn =
              CQL.copy(
                  context.getCqn(),
                  new Modifier() {
                    @SuppressWarnings({"rawtypes", "unchecked"})
                    @Override
                    public List items(List items) {
                      // Always handle items for expand scenarios
                      return itemsModifier.items(items);
                    }

                    @Override
                    public Predicate where(Predicate where) {
                      // Always apply repositoryId filter for all reads
                      if (where == null) {
                        return repositoryFilter;
                      }
                      return CQL.and(where, repositoryFilter);
                    }
                  });
          context.setCqn(modifiedCqn);
          logger.debug("CQN query modified with repository filter and required fields");
        } else {
          logger.warn(
              "Repository value is null for repository ID: {}. Proceeding with limited functionality",
              repositoryId);
          context.setCqn(context.getCqn());
        }
      } catch (Exception e) {
        logger.error("Error in SDMReadAttachmentsHandler.processBefore: {}", e.getMessage(), e);
        // Re-throw to maintain error handling behavior
        throw e;
      }

    } else {
      logger.debug(
          "Target entity {} is not a media entity, skipping attachment processing",
          context.getTarget().getQualifiedName());
      context.setCqn(context.getCqn());
    }
    logger.debug("END: Read attachments processing completed");
  }

  /**
   * Recursively get all attachment associations in the entity tree. This is needed to properly
   * handle deep navigation like Books/covers with $expand=statusNav
   */
  private List<String> getAttachmentAssociations(
      CdsModel model, CdsEntity entity, String associationName, List<String> processedEntities) {
    List<String> associationNames = new ArrayList<>();
    if (SDMApplicationHandlerHelper.isMediaEntity(entity)) {
      logger.debug("Found media entity association: {}", associationName);
      associationNames.add(associationName);
    }

    Map<String, CdsEntity> annotatedEntities =
        entity
            .associations()
            .collect(
                Collectors.toMap(
                    CdsElementDefinition::getName,
                    element -> element.getType().as(CdsAssociationType.class).getTarget()));

    if (annotatedEntities.isEmpty()) {
      return associationNames;
    }

    for (Entry<String, CdsEntity> associatedElement : annotatedEntities.entrySet()) {
      if (!associationNames.contains(associatedElement.getKey())
          && !processedEntities.contains(associatedElement.getKey())
          && !Drafts.SIBLING_ENTITY.equals(associatedElement.getKey())) {
        processedEntities.add(associatedElement.getKey());
        List<String> result =
            getAttachmentAssociations(
                model, associatedElement.getValue(), associatedElement.getKey(), processedEntities);
        associationNames.addAll(result);
      }
    }
    return associationNames;
  }

  /**
   * Processes attachment data and sets criticality values based on upload status. Java equivalent
   * of the frontend JavaScript logic. This method will be called after data is read to enhance it
   * with criticality values.
   *
   * @param context the CDS read event context containing attachment data
   */
  private void processVirusScanInProgressAttachments(
      CdsReadEventContext context, String upID, String upIDkey) {
    try {
      logger.debug("START: Processing virus scan in-progress attachments for upID: {}", upID);
      // Get the statuses of existing attachments and assign color code
      // Get all attachments with virus scan in progress
      Optional<CdsEntity> attachmentDraftEntity =
          context.getModel().findEntity(context.getTarget().getQualifiedName() + "_drafts");
      Optional<CdsEntity> attachmentActiveEntity =
          context.getModel().findEntity(context.getTarget().getQualifiedName());

      List<CmisDocument> attachmentsInProgress =
          dbQuery.getAttachmentsWithVirusScanInProgress(
              attachmentDraftEntity.orElse(null),
              attachmentActiveEntity.orElse(null),
              persistenceService,
              upID,
              upIDkey);
      logger.debug(
          "Found {} attachments with virus scan in progress", attachmentsInProgress.size());

      // Get SDM credentials
      var sdmCredentials = tokenHandler.getSDMCredentials();

      // Iterate through each attachment and call getObject
      for (CmisDocument attachment : attachmentsInProgress) {
        processAttachmentVirusScanStatus(
            attachment,
            sdmCredentials,
            attachmentDraftEntity.orElse(null),
            attachmentActiveEntity.orElse(null),
            persistenceService);
      }

      if (!attachmentsInProgress.isEmpty()) {
        logger.info(
            "Processed {} attachments with virus scan status updates",
            attachmentsInProgress.size());
      }
      logger.debug("END: Process virus scan in-progress attachments");

    } catch (Exception e) {
      logger.error("Error processing virus scan in progress attachments: {}", e.getMessage(), e);
    }
  }

  /**
   * Processes a single attachment to check and update its virus scan status.
   *
   * @param attachment the attachment document to process
   * @param sdmCredentials the SDM credentials for API calls
   * @param attachmentDraftEntity the draft entity for the attachment
   * @param attachmentActiveEntity the active entity for the attachment
   * @param persistenceService the persistence service for database operations
   */
  private void processAttachmentVirusScanStatus(
      CmisDocument attachment,
      SDMCredentials sdmCredentials,
      CdsEntity attachmentDraftEntity,
      CdsEntity attachmentActiveEntity,
      PersistenceService persistenceService) {
    try {
      String objectId = attachment.getObjectId();
      if (objectId != null && !objectId.isEmpty()) {
        logger.debug(
            "Checking virus scan status for objectId: {}, filename: {}",
            objectId,
            attachment.getFileName());

        // Call getObject to check the current state
        JSONObject objectResponse = sdmService.getObject(objectId, sdmCredentials, false);

        if (objectResponse != null) {
          JSONObject succinctProperties = objectResponse.getJSONObject("succinctProperties");
          String currentFileName = succinctProperties.getString("cmis:name");

          // Extract scanStatus if available
          String scanStatus = null;
          if (succinctProperties.has("sap:virusScanStatus")) {
            scanStatus = succinctProperties.getString("sap:virusScanStatus");
            logger.debug("Virus scan status from SDM: {}", scanStatus);
          } else {
            logger.debug("No virus scan status found in SDM response for objectId: {}", objectId);
          }

          logger.debug(
              "Retrieved object for attachmentId: {}, filename: {}, scanStatus: {}",
              attachment.getAttachmentId(),
              currentFileName,
              scanStatus);

          // Update the uploadStatus based on the scan status
          if (scanStatus != null) {
            SDMConstants.ScanStatus scanStatusEnum = SDMConstants.ScanStatus.fromValue(scanStatus);
            dbQuery.updateUploadStatusByScanStatus(
                attachmentDraftEntity,
                attachmentActiveEntity,
                persistenceService,
                objectId,
                scanStatusEnum);
            logger.debug(
                "Updated uploadStatus for objectId: {} based on scanStatus: {}",
                objectId,
                scanStatus);
          }
        } else {
          logger.warn(
              "Object not found for attachmentId: {}, objectId: {}",
              attachment.getAttachmentId(),
              objectId);
        }
      }
    } catch (IOException e) {
      logger.error(
          "Error processing attachment with objectId: {}, error: {}",
          attachment.getObjectId(),
          e.getMessage());
    } catch (Exception e) {
      logger.error(
          "Unexpected error processing attachment with objectId: {}, error: {}",
          attachment.getObjectId(),
          e.getMessage());
    }
  }

  /**
   * Checks the repository type with fallback handling. Returns null if the check fails, allowing
   * the caller to proceed with limited functionality.
   *
   * @param repositoryId the repository ID to check
   * @param context the CDS read event context containing user information
   * @return the RepoValue if successful, null otherwise
   */
  private RepoValue checkRepositoryTypeWithFallback(
      String repositoryId, CdsReadEventContext context) {
    try {
      return sdmService.checkRepositoryType(repositoryId, context.getUserInfo().getTenant());
    } catch (Exception e) {
      logger.warn(
          "Failed to check repository type, proceeding without repository info: {}",
          e.getMessage());
      return null;
    }
  }

  /**
   * After reading a parent entity, counts its attachments per composition facet and sets the
   * corresponding virtual uploadable flag (e.g. {@code isAttachmentsUploadable}) in each result
   * row. Values are computed at read time so no flag is ever written to the consumer's database.
   */
  @After
  @HandlerOrder(HandlerOrder.LATE)
  public void populateUploadableFlags(CdsReadEventContext context, List<CdsData> data) {
    if (data == null || data.isEmpty()) return;

    CdsEntity target = context.getTarget();
    logger.info(
        "populateUploadableFlags: entity={} rows={}", target.getQualifiedName(), data.size());

    List<FacetInfo> facets = findFacetsWithMaxCount(target);
    if (!facets.isEmpty()) {
      logger.debug(
          "populateUploadableFlags Path1: entity={} facets={}",
          target.getQualifiedName(),
          facets.size());

      String keyField =
          target
              .elements()
              .filter(CdsElement::isKey)
              .filter(e -> !"IsActiveEntity".equals(e.getName()))
              .map(CdsElement::getName)
              .findFirst()
              .orElse(null);
      if (keyField == null) return;

      long keyFieldCount =
          target
              .elements()
              .filter(CdsElement::isKey)
              .filter(e -> !"IsActiveEntity".equals(e.getName()))
              .count();
      if (keyFieldCount > 1) {
        logger.warn(
            "populateUploadableFlags Path1: entity={} has {} key fields; only '{}' is used for parentId lookup",
            target.getQualifiedName(),
            keyFieldCount,
            keyField);
      }

      CdsModel model = context.getModel();
      // Cache keyed by "facetName|parentId|isDraft" to avoid one DB query per row per facet.
      Map<String, Boolean> uploadableCache = new HashMap<>();
      for (CdsData row : data) {
        // Determine draft state per row — a single result set can mix active and draft records.
        boolean rowIsDraft = Boolean.FALSE.equals(row.get("IsActiveEntity"));
        Object keyVal = row.get(keyField);
        if (keyVal == null) {
          logger.debug("populateUploadableFlags Path1: skipping row with null keyVal");
          continue;
        }
        String parentId = keyVal.toString();

        for (FacetInfo facet : facets) {
          String attachmentEntityBase = target.getQualifiedName() + "." + facet.facetName;
          CdsEntity attachmentEntity =
              resolveAttachmentEntityForCount(model, attachmentEntityBase, rowIsDraft);
          if (attachmentEntity == null) {
            logger.debug(
                "populateUploadableFlags Path1: entity not found, skipping facet={}",
                facet.facetName);
            continue;
          }

          String upIdKey = SDMUtils.getUpIdKey(attachmentEntity);
          if (upIdKey.isEmpty()) continue;

          String cacheKey = facet.facetName + "|" + parentId + "|" + rowIsDraft;
          boolean isUploadable =
              uploadableCache.computeIfAbsent(
                  cacheKey,
                  k ->
                      dbQuery
                              .getAttachmentsForUPID(
                                  attachmentEntity, persistenceService, parentId, upIdKey)
                              .rowCount()
                          < facet.maxCount);
          logger.debug(
              "Path1: entity={} parentId={} facet={} uploadable={}",
              target.getQualifiedName(),
              parentId,
              facet.facetName,
              isUploadable);
          row.put(facet.virtualFieldName, isUploadable);
        }
      }
      return;
    }

    logger.info(
        "populateUploadableFlags Path2: entity={} checking for up_ expansion",
        target.getQualifiedName());
    populateUploadableFlagsViaUp(context, target, data);
  }

  /**
   * Populates {@code up_.isXxxUploadable} on attachment entity result rows that carry an expanded
   * {@code up_} navigation property. Called when the target entity is an attachment (not a parent)
   * and Fiori requested {@code $expand=up_} to evaluate the Insert button state.
   */
  private void populateUploadableFlagsViaUp(
      CdsReadEventContext context, CdsEntity attachmentEntity, List<CdsData> data) {
    String entityQName = attachmentEntity.getQualifiedName();
    boolean hasUpData = data.stream().anyMatch(row -> row.get("up_") != null);
    logger.info(
        "populateUploadableFlagsViaUp: entity={} rows={} hasUpData={}",
        entityQName,
        data.size(),
        hasUpData);
    if (!hasUpData) return;

    // CAP names draft sibling tables with a "_drafts" suffix — a stable framework convention.
    boolean isDraft = entityQName.endsWith("_drafts");
    logger.debug("populateUploadableFlagsViaUp: isDraft={}", isDraft);
    String baseEntityName =
        isDraft ? entityQName.substring(0, entityQName.length() - 7) : entityQName;

    int lastDot = baseEntityName.lastIndexOf('.');
    if (lastDot < 0) {
      logger.debug(
          "populateUploadableFlagsViaUp: no dot in entity name={}, skipping", baseEntityName);
      return;
    }
    String facetName = baseEntityName.substring(lastDot + 1);
    String parentBaseEntityName = baseEntityName.substring(0, lastDot);
    logger.info(
        "populateUploadableFlagsViaUp: facetName={} parentEntity={}",
        facetName,
        parentBaseEntityName);

    CdsModel model = context.getModel();
    CdsEntity baseParentEntity = model.findEntity(parentBaseEntityName).orElse(null);
    if (baseParentEntity == null) {
      logger.debug(
          "populateUploadableFlagsViaUp: parent entity not found={}", parentBaseEntityName);
      return;
    }

    Optional<CdsAnnotation<Object>> maxCountAnnotation =
        baseParentEntity
            .compositions()
            .filter(c -> facetName.equals(c.getName()))
            .findFirst()
            .flatMap(c -> c.findAnnotation(SDMConstants.ATTACHMENT_MAXCOUNT));
    if (!maxCountAnnotation.isPresent()) {
      logger.info(
          "populateUploadableFlagsViaUp: no maxCount for facet={} on entity={}",
          facetName,
          parentBaseEntityName);
      return;
    }

    long maxCount;
    try {
      maxCount = Long.parseLong(String.valueOf(maxCountAnnotation.get().getValue()));
    } catch (NumberFormatException e) {
      logger.debug(
          "populateUploadableFlagsViaUp: invalid maxCount value={} for facet={}",
          maxCountAnnotation.get().getValue(),
          facetName);
      return;
    }
    if (maxCount <= 0) {
      logger.debug(
          "populateUploadableFlagsViaUp: maxCount={} is non-positive for facet={}, skipping",
          maxCount,
          facetName);
      return;
    }
    logger.debug("populateUploadableFlagsViaUp: maxCount={} facet={}", maxCount, facetName);

    String virtualFieldName = toVirtualFieldName(facetName);
    logger.debug("populateUploadableFlagsViaUp: virtualField={}", virtualFieldName);

    String upIdKey = SDMUtils.getUpIdKey(attachmentEntity);
    logger.debug("populateUploadableFlagsViaUp: upIdKey={}", upIdKey);
    if (upIdKey.isEmpty()) return;

    Map<String, Boolean> uploadableCache = new HashMap<>();
    for (CdsData row : data) {
      Object upDataObj = row.get("up_");
      if (!(upDataObj instanceof Map)) continue;

      @SuppressWarnings("unchecked")
      Map<String, Object> upMap = (Map<String, Object>) upDataObj;

      Object parentIdObj = row.get(upIdKey);
      if (parentIdObj == null) continue;
      String parentId = parentIdObj.toString();

      boolean isUploadable =
          uploadableCache.computeIfAbsent(
              parentId,
              id -> {
                Result countResult =
                    dbQuery.getAttachmentsForUPID(
                        attachmentEntity, persistenceService, id, upIdKey);
                return countResult.rowCount() < maxCount;
              });

      logger.debug(
          "up_ expansion: entity={} parentId={} facet={} virtualField={} uploadable={}",
          entityQName,
          parentId,
          facetName,
          virtualFieldName,
          isUploadable);
      // Written into the up_ map, not into row: Fiori evaluates the Insert button state from
      // up_.isXxxUploadable via the $expand=up_ response, not from the attachment row itself.
      upMap.put(virtualFieldName, isUploadable);
    }
  }

  private List<FacetInfo> findFacetsWithMaxCount(CdsEntity target) {
    List<FacetInfo> result = new ArrayList<>();
    List<CdsElementDefinition> compositions = target.compositions().collect(Collectors.toList());
    for (CdsElementDefinition composition : compositions) {
      String facetName = composition.getName();
      logger.debug("findFacetsWithMaxCount: checking composition={}", facetName);
      Optional<CdsAnnotation<Object>> maxCountAnnotation =
          composition.findAnnotation(SDMConstants.ATTACHMENT_MAXCOUNT);
      if (!maxCountAnnotation.isPresent()) {
        logger.debug(
            "findFacetsWithMaxCount: no maxCount annotation for composition={}", facetName);
        continue;
      }

      long maxCount;
      try {
        maxCount = Long.parseLong(String.valueOf(maxCountAnnotation.get().getValue()));
      } catch (NumberFormatException e) {
        logger.debug(
            "findFacetsWithMaxCount: invalid maxCount value for composition={}", facetName);
        continue;
      }
      if (maxCount <= 0) {
        logger.debug(
            "findFacetsWithMaxCount: maxCount={} is non-positive for composition={}, skipping",
            maxCount,
            facetName);
        continue;
      }

      String virtualFieldName = toVirtualFieldName(facetName);
      logger.debug(
          "findFacetsWithMaxCount: facet={} virtualField={} maxCount={}",
          facetName,
          virtualFieldName,
          maxCount);
      result.add(new FacetInfo(facetName, virtualFieldName, maxCount));
    }
    logger.debug("findFacetsWithMaxCount: found {} facet(s) with maxCount", result.size());
    return result;
  }

  private CdsEntity resolveAttachmentEntityForCount(
      CdsModel model, String baseEntityName, boolean isDraft) {
    logger.debug("resolveAttachmentEntityForCount: base={} isDraft={}", baseEntityName, isDraft);
    if (isDraft) {
      Optional<CdsEntity> draftOpt = model.findEntity(baseEntityName + "_drafts");
      if (draftOpt.isPresent()) {
        logger.debug(
            "resolveAttachmentEntityForCount: resolved to draft entity={}",
            baseEntityName + "_drafts");
        return draftOpt.get();
      }
      logger.warn(
          "resolveAttachmentEntityForCount: _drafts entity not found for '{}', falling back to active entity",
          baseEntityName);
    }
    CdsEntity active = model.findEntity(baseEntityName).orElse(null);
    logger.debug(
        "resolveAttachmentEntityForCount: resolved to active entity={} found={}",
        baseEntityName,
        active != null);
    return active;
  }

  private static String toVirtualFieldName(String facetName) {
    return "is"
        + Character.toUpperCase(facetName.charAt(0))
        + facetName.substring(1)
        + "Uploadable";
  }

  private static final class FacetInfo {
    final String facetName;
    final String virtualFieldName;
    final long maxCount;

    FacetInfo(String facetName, String virtualFieldName, long maxCount) {
      this.facetName = facetName;
      this.virtualFieldName = virtualFieldName;
      this.maxCount = maxCount;
    }
  }
}
