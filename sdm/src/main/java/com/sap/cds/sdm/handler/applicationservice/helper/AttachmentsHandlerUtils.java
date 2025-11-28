package com.sap.cds.sdm.handler.applicationservice.helper;

import com.sap.cds.CdsData;
import com.sap.cds.reflect.CdsAssociationType;
import com.sap.cds.reflect.CdsEntity;
import com.sap.cds.reflect.CdsModel;
import com.sap.cds.sdm.constants.SDMConstants;
import com.sap.cds.sdm.handler.common.SDMAssociationCascader;
import com.sap.cds.sdm.handler.common.SDMAttachmentsReader;
import com.sap.cds.sdm.utilities.SDMUtils;
import com.sap.cds.services.EventContext;
import com.sap.cds.services.persistence.PersistenceService;
import java.util.*;
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
      logger.error(SDMConstants.FETCH_ATTACHMENT_COMPOSITION_ERROR, e.getMessage());
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
    entity = AttachmentsHandlerUtils.wrapEntityWithParent(entity, targetEntity.toLowerCase());
    String[] compositionParts = attachmentCompositionName.split("\\.");
    String attachmentKeyFromComposition =
        compositionParts[compositionParts.length - 1]; // Last part (e.g., "attachments")
    String parentKeyFromComposition =
        compositionParts.length >= 2
            ? compositionParts[compositionParts.length - 2].toLowerCase()
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
      logger.warn(SDMConstants.FETCH_ATTACHMENT_COMPOSITION_ERROR, e.getMessage());
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
      logger.warn(SDMConstants.FETCH_ATTACHMENT_COMPOSITION_ERROR, e.getMessage());
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
        logger.warn(SDMConstants.FETCH_ATTACHMENT_COMPOSITION_ERROR, e.getMessage());
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
      logger.warn(SDMConstants.FETCH_ATTACHMENT_COMPOSITION_ERROR, e.getMessage());
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
      logger.warn(SDMConstants.FETCH_ATTACHMENT_COMPOSITION_ERROR, e.getMessage());
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
        getAttachmentParentTitles(targetEntity, entityData, compositionPathMapping);

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
   * @param targetEntity the qualified name of the target entity (e.g., "AdminService.Books")
   * @param entity the entity data structure containing potential attachment information
   * @param compositionPathMapping the mapping of attachment composition paths obtained from
   *     getAttachmentPathMapping
   * @return a map where keys are attachment composition names and values are the parent entity
   *     titles, or an empty map if no attachments are found
   */
  public static Map<String, String> getAttachmentParentTitles(
      String targetEntity, Map<String, Object> entity, Map<String, String> compositionPathMapping) {
    Map<String, String> parentTitles = new HashMap<>();

    String[] targetEntityPath = targetEntity.split("\\.");
    String entityName = targetEntityPath[targetEntityPath.length - 1];
    Map<String, Object> wrappedEntity = wrapEntityWithParent(entity, entityName.toLowerCase());

    for (Map.Entry<String, String> compositionEntry : compositionPathMapping.entrySet()) {
      String compositionPath = compositionEntry.getValue();
      String parentTitle =
          findParentTitle(wrappedEntity, compositionPath, entityName.toLowerCase());
      if (parentTitle != null) {
        parentTitles.put(compositionPath, parentTitle);
      }
    }

    return parentTitles;
  }

  /**
   * Finds the parent title for a given attachment composition path.
   *
   * @param entity the wrapped entity data structure
   * @param compositionPath the composition path (e.g., "AdminService.chapters123.attachments" or
   *     "AdminService.Books.references")
   * @param rootEntityName the name of the root entity
   * @return the title of the parent entity containing the attachment composition, or null if not
   *     found
   */
  private static String findParentTitle(
      Map<String, Object> entity, String compositionPath, String rootEntityName) {
    try {
      String[] pathParts = compositionPath.split("\\.");

      if (pathParts.length >= 3) {
        String entityPart = pathParts[pathParts.length - 2]; // Second to last part (entity name)

        // Check if this is a direct composition (entity matches root entity)
        if (entityPart.equalsIgnoreCase(rootEntityName)) {
          // Direct attachment at root level (e.g., "AdminService.Books.references")
          return extractTitleFromEntity(entity.get(rootEntityName));
        } else {
          // Nested attachment (e.g., "AdminService.chapters123.attachments")
          // Navigate to the parent entity
          Object rootEntity = entity.get(rootEntityName);
          if (rootEntity instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> rootMap = (Map<String, Object>) rootEntity;
            Object parentCollection = rootMap.get(entityPart);

            if (parentCollection instanceof List) {
              @SuppressWarnings("unchecked")
              List<Map<String, Object>> parentList = (List<Map<String, Object>>) parentCollection;
              if (!parentList.isEmpty()) {
                // Get title from the first item in the collection
                return extractTitleFromEntity(parentList.get(0));
              }
            }
          }
        }
      }
    } catch (Exception e) {
      logger.warn("Error finding parent title for composition path: " + compositionPath, e);
    }

    return null;
  }

  /**
   * Extracts the title field from an entity object, with fallback options.
   *
   * @param entityObj the entity object to extract title from
   * @return the title string, or a fallback identifier, or null if not found
   */
  private static String extractTitleFromEntity(Object entityObj) {
    if (!(entityObj instanceof Map)) {
      return null;
    }

    @SuppressWarnings("unchecked")
    Map<String, Object> entityMap = (Map<String, Object>) entityObj;

    // Priority order: title -> name -> ID -> first non-null string value
    String[] titleFields = {"title", "name", "ID", "id"};

    for (String field : titleFields) {
      Object value = entityMap.get(field);
      if (value != null && value instanceof String && !((String) value).trim().isEmpty()) {
        return (String) value;
      }
    }

    // Fallback: find any string value
    for (Object value : entityMap.values()) {
      if (value != null && value instanceof String && !((String) value).trim().isEmpty()) {
        return (String) value;
      }
    }

    return null;
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
      EventContext context, List<CdsData> data, String composition, String contextInfo) {
    Boolean isError = false;
    String targetEntity = context.getTarget().getQualifiedName();
    logger.info(
        "Validating file names for target entity: {}, composition: {}",
        targetEntity,
        composition);

    // Validation for file names
    Set<String> whitespaceFilenames =
        SDMUtils.FileNameContainsWhitespace(data, composition, targetEntity);
    List<String> restrictedFileNames =
        SDMUtils.FileNameContainsRestrictedCharaters(data, composition, targetEntity);
    Set<String> duplicateFilenames =
        SDMUtils.FileNameDuplicateInDrafts(data, composition, targetEntity);
    logger.info(
        "File name validation results - Whitespace: {}, Restricted: {}, Duplicates: {}",
        whitespaceFilenames,
        restrictedFileNames,
        duplicateFilenames);

    // Collecting all the errors
    if (whitespaceFilenames != null && !whitespaceFilenames.isEmpty()) {
      context.getMessages().error(SDMConstants.FILENAME_WHITESPACE_ERROR_MESSAGE + contextInfo);
      isError = true;
    }
    if (restrictedFileNames != null && !restrictedFileNames.isEmpty()) {
      context
          .getMessages()
          .error(SDMConstants.nameConstraintMessage(restrictedFileNames) + contextInfo);
      isError = true;
    }
    if (duplicateFilenames != null && !duplicateFilenames.isEmpty()) {
      String formattedMessage =
          String.format(
              "%s%s", SDMConstants.duplicateFilenameFormat(duplicateFilenames), contextInfo);
      context.getMessages().error(formattedMessage);
      isError = true;
    }
    // returning the error message
    return isError;
  }
}
