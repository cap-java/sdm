package com.sap.cds.sdm.handler.applicationservice.helper;

import com.sap.cds.CdsData;
import com.sap.cds.reflect.CdsAssociationType;
import com.sap.cds.reflect.CdsEntity;
import com.sap.cds.reflect.CdsModel;
import com.sap.cds.sdm.constants.SDMErrorMessages;
import com.sap.cds.sdm.handler.common.SDMAssociationCascader;
import com.sap.cds.sdm.handler.common.SDMAttachmentsReader;
import com.sap.cds.sdm.model.CmisDocument;
import com.sap.cds.sdm.model.SDMCredentials;
import com.sap.cds.sdm.service.SDMService;
import com.sap.cds.sdm.utilities.SDMUtils;
import com.sap.cds.services.EventContext;
import com.sap.cds.services.ServiceException;
import com.sap.cds.services.persistence.PersistenceService;
import java.io.IOException;
import java.util.*;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AttachmentsHandlerUtils {

  private static final Logger logger = LoggerFactory.getLogger(AttachmentsHandlerUtils.class);

  private AttachmentsHandlerUtils() {
    // Doesn't do anything
  }

  /**
   * Retrieves a list of attachment entity paths for the given CDS entity.
   *
   * <p>This method creates an SDMAttachmentsReader instance to traverse the entity's structure and
   * identify all paths that lead to attachment entities within the CDS model. It uses an
   * SDMAssociationCascader to handle cascading through entity associations and compositions to find
   * nested attachment relationships.
   *
   * @param model the CDS model containing entity definitions and relationships
   * @param entity the target CDS entity to analyze for attachment paths
   * @param persistenceService the persistence service used for data access operations
   * @return a list of strings representing paths to attachment entities, or an empty list if no
   *     attachments are found or if an error occurs during processing
   */
  public static List<String> getAttachmentEntityPaths(
      CdsModel model, CdsEntity entity, PersistenceService persistenceService) {
    try {
      SDMAssociationCascader cascader = new SDMAssociationCascader();
      SDMAttachmentsReader reader = new SDMAttachmentsReader(cascader, persistenceService);
      return reader.getAttachmentEntityPaths(model, entity);
    } catch (Exception e) {
      return new ArrayList<>();
    }
  }

  /**
   * Creates a mapping of attachment entity paths to their corresponding actual paths within the CDS
   * model.
   *
   * <p>This method analyzes both direct and nested attachment compositions within the given entity.
   * It processes direct attachments that are immediate compositions of the entity, and also
   * traverses nested compositions to find attachments in related entities. The resulting mapping
   * provides a translation between logical attachment paths and their actual implementation paths.
   *
   * @param model the CDS model containing entity definitions and relationships
   * @param entity the target CDS entity to analyze for attachment path mappings
   * @param persistenceService the persistence service used for data access operations
   * @return a map where keys are attachment entity paths and values are the corresponding actual
   *     paths, or an empty map if no attachments are found or if an error occurs during processing
   */
  public static Map<String, String> getAttachmentPathMapping(
      CdsModel model, CdsEntity entity, PersistenceService persistenceService) {
    try {
      Map<String, String> pathMapping = new HashMap<>();
      SDMAssociationCascader cascader = new SDMAssociationCascader();
      SDMAttachmentsReader reader = new SDMAttachmentsReader(cascader, persistenceService);

      // Process direct attachments
      entity
          .compositions()
          .forEach(
              composition -> processDirectAttachmentComposition(entity, pathMapping, composition));

      // Process nested attachments
      entity
          .compositions()
          .forEach(
              composition ->
                  processNestedAttachmentComposition(
                      model, entity, reader, pathMapping, composition));

      return pathMapping;
    } catch (Exception e) {
      logger.error(SDMUtils.getErrorMessage("FETCH_ATTACHMENT_COMPOSITION_ERROR"), e.getMessage());
      return new HashMap<>();
    }
  }

  private static void processDirectAttachmentComposition(
      CdsEntity entity, Map<String, String> pathMapping, Object composition) {
    String compositionName = ((com.sap.cds.reflect.CdsElement) composition).getName();
    if (((com.sap.cds.reflect.CdsElement) composition).getType().isAssociation()) {
      CdsAssociationType associationType =
          (CdsAssociationType) ((com.sap.cds.reflect.CdsElement) composition).getType();
      String targetAspect =
          associationType.getTargetAspect().isPresent()
              ? associationType.getTargetAspect().get().getQualifiedName()
              : null;

      if (isDirectAttachmentTargetAspect(targetAspect)) {
        String directPath = entity.getQualifiedName() + "." + compositionName;
        pathMapping.put(directPath, directPath);
      }
    }
  }

  private static void processNestedAttachmentComposition(
      CdsModel model,
      CdsEntity entity,
      SDMAttachmentsReader reader,
      Map<String, String> pathMapping,
      Object composition) {
    String compositionName = ((com.sap.cds.reflect.CdsElement) composition).getName();
    String compositionTargetEntityName = "";

    if (((com.sap.cds.reflect.CdsElement) composition).getType().isAssociation()) {
      CdsAssociationType associationType =
          (CdsAssociationType) ((com.sap.cds.reflect.CdsElement) composition).getType();
      String targetAspect =
          associationType.getTargetAspect().isPresent()
              ? associationType.getTargetAspect().get().getQualifiedName()
              : null;

      if (isDirectAttachmentTargetAspect(targetAspect)) {
        return; // Skip direct attachment compositions
      }

      compositionTargetEntityName = associationType.getTarget().getQualifiedName();
    }

    processCompositionTargetEntity(
        model, entity, reader, pathMapping, compositionName, compositionTargetEntityName);
  }

  private static void processCompositionTargetEntity(
      CdsModel model,
      CdsEntity entity,
      SDMAttachmentsReader reader,
      Map<String, String> pathMapping,
      String compositionName,
      String compositionTargetEntityName) {
    if (!compositionTargetEntityName.isEmpty()) {
      Optional<CdsEntity> targetEntityOpt = model.findEntity(compositionTargetEntityName);
      if (targetEntityOpt.isPresent()) {
        CdsEntity targetEntity = targetEntityOpt.get();
        List<String> attachmentPaths = reader.getAttachmentEntityPaths(model, targetEntity);
        processAttachmentPaths(entity, pathMapping, compositionName, targetEntity, attachmentPaths);
      }
    }
  }

  private static void processAttachmentPaths(
      CdsEntity entity,
      Map<String, String> pathMapping,
      String compositionName,
      CdsEntity targetEntity,
      List<String> attachmentPaths) {
    for (String attachmentPath : attachmentPaths) {
      String entityPath = buildEntityPath(entity, targetEntity, attachmentPath);
      String actualPath = buildActualPath(entity, compositionName, attachmentPath);

      // Only add the mapping if both paths are non-null and the key doesn't already exist
      // This preserves direct attachment mappings from being overwritten by nested ones
      if (entityPath != null && actualPath != null && !pathMapping.containsKey(entityPath)) {
        pathMapping.put(entityPath, actualPath);
      }
    }
  }

  private static boolean isDirectAttachmentTargetAspect(String targetAspect) {
    return targetAspect != null && targetAspect.equalsIgnoreCase("sap.attachments.Attachments");
  }

  /**
   * Fetches attachment data from a nested entity structure based on the target entity and
   * composition name.
   *
   * <p>This method processes the target entity path to extract the entity name, wraps the provided
   * entity data with a parent structure, and then searches for attachments within the nested
   * structure. It parses the attachment composition name to identify both the attachment key (e.g.,
   * "attachments") and the parent key (e.g., "chapters") for precise attachment location.
   *
   * @param targetEntity the qualified name of the target entity (e.g., "ServiceName.EntityName")
   * @param entity the entity data structure containing potential attachment information
   * @param attachmentCompositionName the composition path to the attachments (e.g.,
   *     "chapters.attachments")
   * @return a list of maps representing attachment objects found in the entity structure, or an
   *     empty list if no attachments are found
   */
  public static List<Map<String, Object>> fetchAttachments(
      String targetEntity, Map<String, Object> entity, String attachmentCompositionName) {
    String[] targetEntityPath = targetEntity.split("\\.");
    targetEntity = targetEntityPath[targetEntityPath.length - 1];
    entity = AttachmentsHandlerUtils.wrapEntityWithParent(entity, targetEntity);
    String[] compositionParts = attachmentCompositionName.split("\\.");
    String attachmentKeyFromComposition =
        compositionParts[compositionParts.length - 1]; // Last part (e.g., "attachments")
    String parentKeyFromComposition =
        compositionParts.length >= 2
            ? compositionParts[compositionParts.length - 2]
            : null; // Second last part (e.g., "chapters")

    // Find all attachment arrays in the nested entity structure
    return AttachmentsHandlerUtils.findNestedAttachments(
        entity, attachmentKeyFromComposition, parentKeyFromComposition);
  }

  private static List<Map<String, Object>> findNestedAttachments(
      Map<String, Object> entity, String attachmentKey, String parentKey) {
    return findNestedAttachments(entity, attachmentKey, parentKey, null);
  }

  private static String buildEntityPath(
      CdsEntity parentEntity, CdsEntity targetEntity, String attachmentPath) {
    try {
      String[] pathParts = attachmentPath.split("\\.");
      if (pathParts.length >= 3) {
        // Get the attachment part (last part)
        String attachmentPart = pathParts[pathParts.length - 1];

        // For nested compositions, use the full target entity path to ensure uniqueness
        // For direct attachments on the parent entity, the targetEntity equals parentEntity
        String entityPath;
        if (targetEntity.getQualifiedName().equals(parentEntity.getQualifiedName())) {
          // Direct attachment: use parent entity path
          entityPath = parentEntity.getQualifiedName() + "." + attachmentPart;
        } else {
          // Nested attachment: use target entity path to ensure uniqueness
          entityPath = targetEntity.getQualifiedName() + "." + attachmentPart;
        }
        return entityPath;
      }
    } catch (Exception e) {
      logger.warn(SDMUtils.getErrorMessage("FETCH_ATTACHMENT_COMPOSITION_ERROR"), e.getMessage());
    }
    return null;
  }

  private static String buildActualPath(
      CdsEntity parentEntity, String compositionPropertyName, String attachmentPath) {
    try {
      String[] pathParts = attachmentPath.split("\\.");
      if (pathParts.length >= 3) {
        // Get the attachment part (last part)
        String attachmentPart = pathParts[pathParts.length - 1];

        // Build the new path using parent entity qualified name + composition property name
        return parentEntity.getQualifiedName()
            + "."
            + compositionPropertyName
            + "."
            + attachmentPart;
      }
    } catch (Exception e) {
      logger.warn(SDMUtils.getErrorMessage("FETCH_ATTACHMENT_COMPOSITION_ERROR"), e.getMessage());
    }
    return null;
  }

  private static List<Map<String, Object>> findNestedAttachments(
      Map<String, Object> entity, String attachmentKey, String parentKey, String currentParentKey) {
    List<Map<String, Object>> result = new ArrayList<>();

    for (Map.Entry<String, Object> entry : entity.entrySet()) {
      String key = entry.getKey();
      Object value = entry.getValue();

      // If we found the attachment key
      if (attachmentKey.equals(key) && value instanceof List) {
        result.addAll(processAttachmentKey(value, key, parentKey, currentParentKey));
      }
      // Recursively search in nested objects
      else if (value instanceof Map) {
        result.addAll(processNestedMap(value, key, attachmentKey, parentKey));
      }
      // Recursively search in lists
      else if (value instanceof List) {
        result.addAll(processNestedList(value, key, attachmentKey, parentKey));
      }
    }

    return result;
  }

  private static List<Map<String, Object>> processAttachmentKey(
      Object value, String key, String parentKey, String currentParentKey) {
    List<Map<String, Object>> result = new ArrayList<>();

    // Check if the parent matches (if parentKey is specified)
    if (parentKey == null || isCorrectParentContext(currentParentKey, parentKey)) {
      try {
        List<Map<String, Object>> attachments = (List<Map<String, Object>>) value;
        result.addAll(attachments);
      } catch (ClassCastException e) {
        logger.warn(SDMUtils.getErrorMessage("FETCH_ATTACHMENT_COMPOSITION_ERROR"), e.getMessage());
      }
    }

    return result;
  }

  private static List<Map<String, Object>> processNestedMap(
      Object value, String key, String attachmentKey, String parentKey) {
    List<Map<String, Object>> result = new ArrayList<>();

    try {
      Map<String, Object> nestedMap = (Map<String, Object>) value;
      result.addAll(findNestedAttachments(nestedMap, attachmentKey, parentKey, key));
    } catch (ClassCastException e) {
      logger.warn(SDMUtils.getErrorMessage("FETCH_ATTACHMENT_COMPOSITION_ERROR"), e.getMessage());
    }

    return result;
  }

  private static List<Map<String, Object>> processNestedList(
      Object value, String key, String attachmentKey, String parentKey) {
    List<Map<String, Object>> result = new ArrayList<>();

    try {
      List<?> list = (List<?>) value;
      for (Object item : list) {
        if (item instanceof Map) {
          Map<String, Object> itemMap = (Map<String, Object>) item;
          result.addAll(findNestedAttachments(itemMap, attachmentKey, parentKey, key));
        }
      }
    } catch (ClassCastException e) {
      logger.warn(SDMUtils.getErrorMessage("FETCH_ATTACHMENT_COMPOSITION_ERROR"), e.getMessage());
    }

    return result;
  }

  private static boolean isCorrectParentContext(String currentParentKey, String expectedParentKey) {
    // If no specific parent is expected, any context is valid
    if (expectedParentKey == null) {
      return true;
    }

    // If we're at root level (no current parent) and expecting a specific parent, no match
    if (currentParentKey == null) {
      return false;
    }

    // Check if the current parent matches the expected parent
    return expectedParentKey.equals(currentParentKey);
  }

  /**
   * Wraps an entity data structure with a parent container using the specified target entity name.
   *
   * <p>This utility method creates a new map with the target entity name as the key and the
   * provided entity data as the value. This is necessary because the root of the target entity in
   * the CdsData object is not mentioned explicitly, and hence interferes with the recursive
   * fetching of attachment compositions.
   *
   * @param root the entity data structure to be wrapped
   * @param targetEntity the name to use as the parent key for wrapping the entity data
   * @return a new map containing the target entity name as key and the root entity data as value
   */
  public static Map<String, Object> wrapEntityWithParent(
      Map<String, Object> root, String targetEntity) {
    Map<String, Object> wrapper = new HashMap<>();
    wrapper.put(targetEntity, root);
    return wrapper;
  }

  /**
   * Retrieves comprehensive attachment composition details including parent titles.
   *
   * <p>This method combines attachment composition path mapping with parent title extraction to
   * provide complete details for each attachment composition. Each entry contains the composition
   * name, definition, and parent entity title.
   *
   * @param model the CDS model containing entity definitions and relationships
   * @param entity the target CDS entity to analyze for attachment path mappings
   * @param persistenceService the persistence service used for data access operations
   * @param targetEntity the qualified name of the target entity (e.g., "AdminService.Books")
   * @param entityData the entity data structure containing potential attachment information
   * @return a map where keys are attachment composition definitions and values are maps containing
   *     name, definition, and parentTitle, or an empty map if no attachments are found
   */
  public static Map<String, Map<String, String>> getAttachmentCompositionDetails(
      CdsModel model,
      CdsEntity entity,
      PersistenceService persistenceService,
      String targetEntity,
      Map<String, Object> entityData) {
    Map<String, Map<String, String>> attachmentDetails = new HashMap<>();

    // Get the composition path mapping
    Map<String, String> compositionPathMapping =
        getAttachmentPathMapping(model, entity, persistenceService);

    // Get parent titles
    Map<String, String> parentTitles =
        getAttachmentParentTitles(model, targetEntity, entityData, compositionPathMapping);

    // Combine into comprehensive details
    for (Map.Entry<String, String> entry : compositionPathMapping.entrySet()) {
      String definition = entry.getKey();
      String name = entry.getValue();
      String parentTitle = parentTitles.get(name);

      Map<String, String> details = new HashMap<>();
      details.put("name", name);
      details.put("definition", definition);
      details.put("parentTitle", parentTitle);

      attachmentDetails.put(definition, details);
    }

    return attachmentDetails;
  }

  /**
   * Retrieves parent entity titles for each attachment composition found in the entity structure.
   *
   * <p>This method analyzes the entity data structure to identify attachment compositions and
   * extracts the title (or other identifying field) of the parent entity containing each attachment
   * composition. It handles both direct attachments at the root level and nested attachments within
   * composed entities.
   *
   * @param model the CDS model containing entity definitions and relationships
   * @param targetEntity the qualified name of the target entity (e.g., "AdminService.Books")
   * @param entity the entity data structure containing potential attachment information
   * @param compositionPathMapping the mapping of attachment composition paths obtained from
   *     getAttachmentPathMapping
   * @return a map where keys are attachment composition names and values are the parent entity
   *     titles, or an empty map if no attachments are found
   */
  public static Map<String, String> getAttachmentParentTitles(
      CdsModel model,
      String targetEntity,
      Map<String, Object> entity,
      Map<String, String> compositionPathMapping) {
    Map<String, String> parentTitles = new HashMap<>();

    String[] targetEntityPath = targetEntity.split("\\.");
    String entityName = targetEntityPath[targetEntityPath.length - 1];
    Map<String, Object> wrappedEntity = wrapEntityWithParent(entity, entityName);

    for (Map.Entry<String, String> compositionEntry : compositionPathMapping.entrySet()) {
      String compositionPath = compositionEntry.getValue();
      String parentTitle =
          findParentTitle(model, wrappedEntity, compositionPath, entityName, targetEntity);
      if (parentTitle != null && !parentTitle.isEmpty()) {
        parentTitles.put(compositionPath, parentTitle);
      }
    }

    return parentTitles;
  }

  /**
   * Finds the parent title for a given attachment composition path.
   *
   * @param model the CDS model containing entity definitions and relationships
   * @param entity the wrapped entity data structure
   * @param compositionPath the composition path (e.g., "AdminService.chapters123.attachments" or
   *     "AdminService.Books.references")
   * @param rootEntityName the name of the root entity
   * @param targetEntity the qualified name of the target entity
   * @return the title of the parent entity containing the attachment composition, or null if not
   *     found
   */
  private static String findParentTitle(
      CdsModel model,
      Map<String, Object> entity,
      String compositionPath,
      String rootEntityName,
      String targetEntity) {
    logFindParentTitleStart(entity, compositionPath, rootEntityName, targetEntity);

    try {
      String[] pathParts = compositionPath.split("\\.");
      logger.info("findParentTitle: pathParts={}", String.join(",", pathParts));

      if (pathParts.length < 3) {
        logger.info("findParentTitle: Returning null - insufficient path parts");
        return null;
      }

      String entityPart = pathParts[pathParts.length - 2];
      logger.info("findParentTitle: entityPart={} (second to last)", entityPart);

      if (entityPart.equalsIgnoreCase(rootEntityName)) {
        return handleDirectAttachment(model, entity, rootEntityName, targetEntity);
      } else {
        return handleNestedAttachment(model, entity, rootEntityName, entityPart, targetEntity);
      }
    } catch (Exception e) {
      logger.warn("Error finding parent title for composition path: " + compositionPath, e);
      return null;
    }
  }

  /**
   * Logs the start of findParentTitle operation.
   *
   * @param entity the entity data structure
   * @param compositionPath the composition path
   * @param rootEntityName the root entity name
   * @param targetEntity the target entity name
   */
  private static void logFindParentTitleStart(
      Map<String, Object> entity,
      String compositionPath,
      String rootEntityName,
      String targetEntity) {
    logger.info(
        "findParentTitle: compositionPath={}, rootEntityName={}, targetEntity={}",
        compositionPath,
        rootEntityName,
        targetEntity);
    logger.info("findParentTitle: entity keys={}", entity.keySet());
  }

  /**
   * Handles direct attachment title extraction.
   *
   * @param model the CDS model
   * @param entity the entity data structure
   * @param rootEntityName the root entity name
   * @param targetEntity the target entity name
   * @return the extracted title, or null if not found
   */
  private static String handleDirectAttachment(
      CdsModel model, Map<String, Object> entity, String rootEntityName, String targetEntity) {
    logger.info(
        "findParentTitle: Direct attachment detected, looking up entity.get({})", rootEntityName);
    Object entityData = entity.get(rootEntityName);
    logger.info(
        "findParentTitle: entityData type={}, isNull={}",
        entityData != null ? entityData.getClass().getSimpleName() : "null",
        entityData == null);
    return extractTitleFromEntity(model, targetEntity, entityData);
  }

  /**
   * Handles nested attachment title extraction.
   *
   * @param model the CDS model
   * @param entity the entity data structure
   * @param rootEntityName the root entity name
   * @param entityPart the entity part from the path
   * @param targetEntity the target entity name
   * @return the extracted title, or null if not found
   */
  private static String handleNestedAttachment(
      CdsModel model,
      Map<String, Object> entity,
      String rootEntityName,
      String entityPart,
      String targetEntity) {
    logger.info("findParentTitle: Nested attachment detected");

    Object rootEntity = entity.get(rootEntityName);
    if (!(rootEntity instanceof Map)) {
      logger.info("findParentTitle: Returning null - rootEntity is not a Map");
      return null;
    }

    @SuppressWarnings("unchecked")
    Map<String, Object> rootMap = (Map<String, Object>) rootEntity;
    Object parentCollection = rootMap.get(entityPart);

    if (!(parentCollection instanceof List)) {
      logger.info("findParentTitle: Returning null - parentCollection is not a List");
      return null;
    }

    @SuppressWarnings("unchecked")
    List<Map<String, Object>> parentList = (List<Map<String, Object>>) parentCollection;
    if (parentList.isEmpty()) {
      logger.info("findParentTitle: Returning null - parentList is empty");
      return null;
    }

    String nestedEntityName = determineNestedEntityName(model, targetEntity, entityPart);
    if (nestedEntityName == null) {
      logger.info("findParentTitle: Returning null - nestedEntityName is null");
      return null;
    }

    return extractTitleFromEntity(model, nestedEntityName, parentList.get(0));
  }

  /**
   * Determines the fully qualified entity name for a nested composition.
   *
   * @param model the CDS model
   * @param parentEntityName the parent entity name
   * @param compositionName the composition property name
   * @return the fully qualified nested entity name, or null if not found
   */
  private static String determineNestedEntityName(
      CdsModel model, String parentEntityName, String compositionName) {
    try {
      Optional<CdsEntity> parentEntity = model.findEntity(parentEntityName);
      if (parentEntity.isPresent()) {
        Optional<com.sap.cds.reflect.CdsElement> composition =
            parentEntity.get().findElement(compositionName);
        if (composition.isPresent() && composition.get().getType().isAssociation()) {
          CdsAssociationType associationType = (CdsAssociationType) composition.get().getType();
          return associationType.getTarget().getQualifiedName();
        }
      }
    } catch (Exception e) {
      logger.warn("Error determining nested entity name for composition: " + compositionName, e);
    }
    return null;
  }

  /**
   * Extracts the title field from an entity object using CDS metadata annotations.
   *
   * <p>This method extracts entity titles using @Common.Text annotation on the semantic key field,
   * which is the only mechanism proven to work reliably in both Fiori UI and Java backend through
   * empirical testing.
   *
   * <p><b>How it works:</b>
   *
   * <ol>
   *   <li>Finds the semantic key field from @Common.SemanticKey annotation
   *   <li>Checks if that field has a @Common.Text annotation pointing to a title field
   *   <li>Extracts and returns the value of the title field
   * </ol>
   *
   * <p><b>Important:</b> Define your CDS model as follows for proper title extraction:
   *
   * <pre>{@code
   * entity Books {
   *   key ID : UUID;
   *   title  : String;
   * }
   *
   * annotate Books with @Common.SemanticKey: [ID] {
   *   ID @Common.Text: title;
   * }
   * }</pre>
   *
   * <p><b>Note:</b> UI.HeaderInfo.Title annotations defined in app/common.cds are NOT accessible to
   * Java backend code via CDS Reflection API. They are only used by Fiori UI layer for OData
   * metadata generation.
   *
   * @param model the CDS model containing entity definitions and annotations
   * @param entityName the qualified name of the entity (e.g., "AdminService.Books")
   * @param entityObj the entity object to extract title from
   * @return the title string from annotations, or null if not found
   */
  private static String extractTitleFromEntity(
      CdsModel model, String entityName, Object entityObj) {
    if (!(entityObj instanceof Map)) {
      logger.info("extractTitleFromEntity: entityObj is not a Map for entity: {}", entityName);
      return null;
    }

    @SuppressWarnings("unchecked")
    Map<String, Object> entityMap = (Map<String, Object>) entityObj;

    logger.info(
        "extractTitleFromEntity: Extracting title for entity: {}, data keys: {}",
        entityName,
        entityMap.keySet());

    // Get title field from Common.Text annotation on semantic key field
    // This is proven to work in both Fiori UI and Java backend
    String titleFieldFromSemanticKey = getSemanticKeyField(model, entityName);
    logger.info(
        "extractTitleFromEntity: titleFieldFromSemanticKey = {} for entity: {}",
        titleFieldFromSemanticKey,
        entityName);

    if (titleFieldFromSemanticKey != null) {
      // Check if the semantic key field has a Common.Text annotation pointing to another field
      String titleFieldFromCommonText =
          getTitleFromCommonTextOnField(model, entityName, titleFieldFromSemanticKey);
      logger.info(
          "extractTitleFromEntity: titleFieldFromCommonText = {} for entity: {}",
          titleFieldFromCommonText,
          entityName);

      if (titleFieldFromCommonText != null) {
        // Use the field specified by Common.Text annotation
        Object value = getNestedValue(entityMap, titleFieldFromCommonText);
        logger.info(
            "extractTitleFromEntity: Value for Common.Text field '{}' = {}",
            titleFieldFromCommonText,
            value);
        if (value != null && value instanceof String && !((String) value).trim().isEmpty()) {
          logger.info(
              "extractTitleFromEntity: Returning title from Common.Text annotation: {}", value);
          return (String) value;
        }
      }
    }

    logger.info("extractTitleFromEntity: No title found for entity: {}", entityName);
    // Return null if no annotation-based title is found
    return null;
  }

  /**
   * Extracts the title field name from @Common.Text annotation on a specific field. This mirrors
   * how Fiori determines page titles when UI.HeaderInfo is not present.
   *
   * <p>Example: If field "ID" has @Common.Text: title, this returns "title"
   *
   * @param model the CDS model
   * @param entityName the qualified entity name
   * @param fieldName the field to check for @Common.Text annotation
   * @return the field name from Common.Text annotation, or null if not found
   */
  private static String getTitleFromCommonTextOnField(
      CdsModel model, String entityName, String fieldName) {
    logger.info(
        "getTitleFromCommonTextOnField: Checking field '{}' on entity '{}'", fieldName, entityName);

    if (model == null || entityName == null || fieldName == null) {
      return null;
    }

    try {
      Optional<CdsEntity> entityOpt = model.findEntity(entityName);
      if (!entityOpt.isPresent()) {
        return null;
      }

      Optional<com.sap.cds.reflect.CdsElement> elementOpt = entityOpt.get().findElement(fieldName);
      if (!elementOpt.isPresent()) {
        return null;
      }

      com.sap.cds.reflect.CdsElement element = elementOpt.get();
      logger.info(
          "getTitleFromCommonTextOnField: Found element '{}', checking for Common annotation",
          fieldName);

      // Try Common annotation first (contains Text property)
      String result = extractTextFromCommonAnnotation(element);
      if (result != null) {
        return result;
      }

      // Try Common.Text directly as alternate format
      return extractTextFromCommonTextAnnotation(element);

    } catch (Exception e) {
      logger.info("getTitleFromCommonTextOnField: Error - {}", e.getMessage(), e);
      return null;
    }
  }

  /**
   * Extracts text value from Common annotation's Text property.
   *
   * @param element the CDS element to check
   * @return the parsed text field name, or null if not found
   */
  private static String extractTextFromCommonAnnotation(com.sap.cds.reflect.CdsElement element) {
    Optional<com.sap.cds.reflect.CdsAnnotation<Object>> commonAnnotationOpt =
        element.findAnnotation("Common");
    if (!commonAnnotationOpt.isPresent()) {
      return null;
    }

    Object commonValue = commonAnnotationOpt.get().getValue();
    logger.info(
        "getTitleFromCommonTextOnField: Common annotation value type = {}",
        commonValue != null ? commonValue.getClass().getSimpleName() : "null");

    if (!(commonValue instanceof Map)) {
      return null;
    }

    @SuppressWarnings("unchecked")
    Map<String, Object> commonMap = (Map<String, Object>) commonValue;
    logger.info("getTitleFromCommonTextOnField: Common map keys = {}", commonMap.keySet());

    Object textValue = commonMap.get("Text");
    logger.info("getTitleFromCommonTextOnField: Text value = {}", textValue);

    return parseTextValue(textValue, "getTitleFromCommonTextOnField");
  }

  /**
   * Extracts text value from Common.Text annotation directly.
   *
   * @param element the CDS element to check
   * @return the parsed text field name, or null if not found
   */
  private static String extractTextFromCommonTextAnnotation(
      com.sap.cds.reflect.CdsElement element) {
    Optional<com.sap.cds.reflect.CdsAnnotation<Object>> commonTextOpt =
        element.findAnnotation("Common.Text");
    if (!commonTextOpt.isPresent()) {
      return null;
    }

    Object textValue = commonTextOpt.get().getValue();
    logger.info("getTitleFromCommonTextOnField: Common.Text value = {}", textValue);

    return parseTextValue(textValue, "getTitleFromCommonTextOnField");
  }

  /**
   * Parses a text value by removing CDS element reference markers.
   *
   * @param textValue the raw text value from annotation
   * @param logContext context string for logging
   * @return the parsed field name, or null if textValue is null
   */
  private static String parseTextValue(Object textValue, String logContext) {
    if (textValue == null) {
      return null;
    }

    String result = textValue.toString();
    // Remove CDS annotation wrapper syntax to extract the actual field name
    if (result.startsWith("{==") && result.endsWith("}")) {
      result = result.substring(3, result.length() - 1);
    } else if (result.startsWith("{") && result.endsWith("}")) {
      result = result.substring(1, result.length() - 1);
    }
    logger.info("{}: Parsed title field = {}", logContext, result);
    return result;
  }

  /**
   * Extracts the first field from Common.SemanticKey annotation.
   *
   * @param model the CDS model
   * @param entityName the qualified entity name
   * @return the first semantic key field name, or null if not found
   */
  private static String getSemanticKeyField(CdsModel model, String entityName) {
    if (model == null || entityName == null) {
      return null;
    }

    try {
      Optional<CdsEntity> entityOpt = model.findEntity(entityName);
      if (entityOpt.isPresent()) {
        CdsEntity entity = entityOpt.get();
        Optional<com.sap.cds.reflect.CdsAnnotation<Object>> semanticKeyOpt =
            entity.findAnnotation("Common.SemanticKey");

        if (semanticKeyOpt.isPresent() && semanticKeyOpt.get().getValue() instanceof List) {
          @SuppressWarnings("unchecked")
          List<?> keys = (List<?>) semanticKeyOpt.get().getValue();
          if (!keys.isEmpty()) {
            String rawValue = keys.get(0).toString();
            logger.info("getSemanticKeyField: Raw value from annotation = {}", rawValue);

            // Extract field name from CDS annotation format (e.g., curly braces with equals
            // prefix)
            String fieldName = rawValue;
            if (rawValue.startsWith("{==") && rawValue.endsWith("}")) {
              fieldName = rawValue.substring(3, rawValue.length() - 1);
            } else if (rawValue.startsWith("{") && rawValue.endsWith("}")) {
              fieldName = rawValue.substring(1, rawValue.length() - 1);
            }

            logger.info("getSemanticKeyField: Parsed field name = {}", fieldName);
            return fieldName;
          }
        }
      }
    } catch (Exception e) {
      logger.info("getSemanticKeyField: Error - {}", e.getMessage(), e);
    }

    return null;
  }

  /**
   * Gets a nested value from a map using a path (e.g., "author.name").
   *
   * @param map the map to extract value from
   * @param path the path to the value (can include dots for nested access)
   * @return the value at the path, or null if not found
   */
  private static Object getNestedValue(Map<String, Object> map, String path) {
    if (path == null || map == null) {
      return null;
    }

    String[] parts = path.split("\\.");
    Object current = map;

    for (String part : parts) {
      if (current instanceof Map) {
        @SuppressWarnings("unchecked")
        Map<String, Object> currentMap = (Map<String, Object>) current;
        current = currentMap.get(part);
      } else {
        return null;
      }
    }

    return current;
  }

  /**
   * Validates file names in the provided data for various constraints including whitespace,
   * restricted characters, and duplicates.
   *
   * <p>This method performs comprehensive validation of file names by checking for:
   *
   * <ul>
   *   <li>Whitespace-only or null file names
   *   <li>Restricted characters (such as / and \)
   *   <li>Duplicate file names within the same repository
   * </ul>
   *
   * @param context the event context containing messages for error reporting
   * @param data the list of CDS data containing potential file attachments
   * @param composition the composition name used to locate attachments in the data structure
   * @return true if any validation errors are found, false otherwise
   */
  public static Boolean validateFileNames(
      EventContext context,
      List<CdsData> data,
      String composition,
      String contextInfo,
      Optional<CdsEntity> attachmentEntity) {
    Boolean isError = false;
    String targetEntity = context.getTarget().getQualifiedName();
    String upIdKey = "";
    if (attachmentEntity.isPresent()) {
      upIdKey = SDMUtils.getUpIdKey(attachmentEntity.get());
    }

    // Validation for file names
    Set<String> whitespaceFilenames =
        SDMUtils.FileNameContainsWhitespace(data, composition, targetEntity);
    List<String> restrictedFileNames =
        SDMUtils.FileNameContainsRestrictedCharaters(data, composition, targetEntity);
    Set<String> duplicateFilenames =
        SDMUtils.FileNameDuplicateInDrafts(data, composition, targetEntity, upIdKey);

    // Collecting all the errors
    if (whitespaceFilenames != null && !whitespaceFilenames.isEmpty()) {
      context
          .getMessages()
          .error(SDMUtils.getErrorMessage("FILENAME_WHITESPACE_ERROR_MESSAGE") + contextInfo);
      isError = true;
    }
    if (restrictedFileNames != null && !restrictedFileNames.isEmpty()) {
      context
          .getMessages()
          .error(SDMErrorMessages.nameConstraintMessage(restrictedFileNames) + contextInfo);
      isError = true;
    }
    if (duplicateFilenames != null && !duplicateFilenames.isEmpty()) {
      String formattedMessage =
          String.format(
              "%s%s", SDMErrorMessages.duplicateFilenameFormat(duplicateFilenames), contextInfo);
      context.getMessages().error(formattedMessage);
      isError = true;
    }
    // returning the error message
    return isError;
  }

  /**
   * Fetches attachment data (filename and description) from SDM.
   *
   * @param sdmService the SDM service to fetch data from
   * @param objectId the object ID in SDM
   * @param sdmCredentials the credentials for SDM access
   * @param isSystemUser whether the request is from a system user
   * @return a list containing [filename, description]
   * @throws IOException if there's an error fetching from SDM
   */
  public static JSONObject fetchAttachmentDataFromSDM(
      SDMService sdmService, String objectId, SDMCredentials sdmCredentials, boolean isSystemUser)
      throws IOException {
    return sdmService.getObject(objectId, sdmCredentials, isSystemUser);
  }

  /**
   * Updates the filename property in the secondary properties map if needed.
   *
   * @param fileNameInDB the filename currently in the database
   * @param filenameInRequest the filename from the request
   * @param updatedSecondaryProperties the map to update
   * @throws ServiceException if filename validation fails
   */
  public static void updateFilenameProperty(
      String fileNameInDB,
      String filenameInRequest,
      String fileNameInSDM,
      Map<String, String> updatedSecondaryProperties)
      throws ServiceException {
    if (fileNameInDB == null) {
      if (filenameInRequest != null) {
        if (!filenameInRequest.equals(fileNameInSDM)) {
          updatedSecondaryProperties.put("filename", filenameInRequest);
        }
      } else {
        throw new ServiceException("Filename cannot be empty");
      }
    } else {
      if (filenameInRequest == null) {
        throw new ServiceException("Filename cannot be empty");
      } else if (!fileNameInDB.equals(filenameInRequest)) {
        updatedSecondaryProperties.put("filename", filenameInRequest);
      }
    }
  }

  public static void updateDescriptionProperty(
      String descriptionInDB,
      String descriptionInRequest,
      String descriptionInSDM,
      Map<String, String> updatedSecondaryProperties,
      Boolean isUpdate)
      throws ServiceException {
    // Normalize null to empty string for comparison
    String normalizedRequest = descriptionInRequest == null ? "" : descriptionInRequest;
    String normalizedDB = descriptionInDB == null ? "" : descriptionInDB;
    String normalizedSDM = descriptionInSDM == null ? "" : descriptionInSDM;

    if (descriptionInDB == null
        && isUpdate) { // Attachment did not contain description and is being updated now
      // Only update if the request actually has a value different from what's in SDM
      if (!normalizedRequest.isEmpty() && !normalizedRequest.equals(normalizedSDM)) {
        updatedSecondaryProperties.put("description", normalizedRequest);
      }
    } else if (descriptionInDB
        == null) { // Attachment contained description during upload and it was changed before
      // saving or description was added before save handler (create) was called
      if (!normalizedRequest.equals(normalizedSDM)) {
        updatedSecondaryProperties.put("description", normalizedRequest);
      }
    } else if (!normalizedDB.equals(
        normalizedRequest)) { // Attachment contained description and is being updated now
      updatedSecondaryProperties.put("description", normalizedRequest);
    }
  }

  /**
   * Handles the SDM service response and adds to appropriate error/warning lists.
   *
   * @param responseCode the HTTP response code from SDM
   * @param attachment the attachment map to potentially revert
   * @param fileNameInSDM the original filename in SDM
   * @param filenameInRequest the filename from the request
   * @param propertiesInDB the properties from the database
   * @param secondaryTypeProperties the secondary type properties
   * @param descriptionInSDM the original description in SDM
   * @param noSDMRoles list to add to if 403 error
   * @param duplicateFileNameList list to add to if 409 error
   * @param filesNotFound list to add to if 404 error
   */
  public static void handleSDMUpdateResponse(
      int responseCode,
      Map<String, Object> attachment,
      String fileNameInSDM,
      String filenameInRequest,
      Map<String, String> propertiesInDB,
      Map<String, String> secondaryTypeProperties,
      String descriptionInSDM,
      List<String> noSDMRoles,
      List<String> duplicateFileNameList,
      List<String> filesNotFound) {
    switch (responseCode) {
      case 403:
        noSDMRoles.add(fileNameInSDM);
        revertAttachmentProperties(
            attachment, fileNameInSDM, propertiesInDB, secondaryTypeProperties, descriptionInSDM);
        break;
      case 409:
        duplicateFileNameList.add(filenameInRequest);
        revertAttachmentProperties(
            attachment, fileNameInSDM, propertiesInDB, secondaryTypeProperties, descriptionInSDM);
        break;
      case 404:
        filesNotFound.add(fileNameInSDM);
        revertAttachmentProperties(
            attachment, fileNameInSDM, propertiesInDB, secondaryTypeProperties, descriptionInSDM);
        break;
      case 200:
      case 201:
        // Success cases, do nothing
        break;
      default:
        throw new ServiceException(SDMUtils.getErrorMessage("SDM_SERVER_ERROR"), (Object[]) null);
    }
  }

  /**
   * Handles exceptions from SDM service calls.
   *
   * @param e the service exception
   * @param attachment the attachment map to potentially revert
   * @param fileNameInSDM the original filename in SDM
   * @param filenameInRequest the filename from the request
   * @param propertiesInDB the properties from the database
   * @param secondaryTypeProperties the secondary type properties
   * @param descriptionInSDM the original description in SDM
   * @param filesWithUnsupportedProperties list to add to if unsupported properties error
   * @param badRequest map to add to for other errors
   */
  public static void handleSDMServiceException(
      ServiceException e,
      Map<String, Object> attachment,
      String fileNameInSDM,
      String filenameInRequest,
      Map<String, String> propertiesInDB,
      Map<String, String> secondaryTypeProperties,
      String descriptionInSDM,
      List<String> filesWithUnsupportedProperties,
      Map<String, String> badRequest) {
    if (e.getMessage().startsWith(SDMUtils.getErrorMessage("UNSUPPORTED_PROPERTIES"))) {
      String unsupportedDetails =
          e.getMessage()
              .substring(SDMUtils.getErrorMessage("UNSUPPORTED_PROPERTIES").length())
              .trim();
      filesWithUnsupportedProperties.add(unsupportedDetails);
      revertAttachmentProperties(
          attachment, fileNameInSDM, propertiesInDB, secondaryTypeProperties, descriptionInSDM);
    } else {
      badRequest.put(filenameInRequest, e.getMessage());
      revertAttachmentProperties(
          attachment, filenameInRequest, propertiesInDB, secondaryTypeProperties, descriptionInSDM);
    }
  }

  /**
   * Reverts attachment properties to their original values from the database.
   *
   * @param attachment the attachment map to update
   * @param fileName the filename to restore
   * @param propertiesInDB the properties from the database
   * @param secondaryTypeProperties the secondary type properties mapping
   * @param descriptionInSDM the description to restore
   */
  public static void revertAttachmentProperties(
      Map<String, Object> attachment,
      String fileName,
      Map<String, String> propertiesInDB,
      Map<String, String> secondaryTypeProperties,
      String descriptionInSDM) {
    if (propertiesInDB != null) {
      for (Map.Entry<String, String> entry : propertiesInDB.entrySet()) {
        String dbKey = entry.getKey();
        String dbValue = entry.getValue();

        String secondaryKey =
            secondaryTypeProperties.entrySet().stream()
                .filter(e -> e.getValue().equals(dbKey))
                .map(Map.Entry::getKey)
                .findFirst()
                .orElse(null);

        if (secondaryKey != null) {
          attachment.replace(secondaryKey, dbValue);
        }
      }
    }
    attachment.replace("fileName", fileName);
    attachment.replace("note", descriptionInSDM);
  }

  /**
   * Prepares a CmisDocument with the provided attachment data.
   *
   * @param filenameInRequest the filename from the request
   * @param descriptionInRequest the description from the request
   * @param objectId the object ID in SDM
   * @return a configured CmisDocument
   */
  public static CmisDocument prepareCmisDocument(
      String filenameInRequest, String descriptionInRequest, String objectId) {
    CmisDocument cmisDocument = new CmisDocument();
    cmisDocument.setFileName(filenameInRequest);
    cmisDocument.setDescription(descriptionInRequest);
    cmisDocument.setObjectId(objectId);
    return cmisDocument;
  }

  public static String getContextInfo(String compositionName, String parentTitle) {
    return String.format(SDMErrorMessages.CONTEXT_INFO_TABLE, compositionName)
        + String.format(
            SDMErrorMessages.CONTEXT_INFO_PAGE,
            (parentTitle != null && !parentTitle.trim().isEmpty() ? parentTitle : "Unknown"));
  }
}
