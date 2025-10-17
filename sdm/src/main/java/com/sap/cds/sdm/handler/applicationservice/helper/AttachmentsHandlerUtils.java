package com.sap.cds.sdm.handler.applicationservice.helper;

import com.sap.cds.reflect.CdsAssociationType;
import com.sap.cds.reflect.CdsEntity;
import com.sap.cds.reflect.CdsModel;
import com.sap.cds.sdm.handler.common.SDMAssociationCascader;
import com.sap.cds.sdm.handler.common.SDMAttachmentsReader;
import com.sap.cds.services.cds.ApplicationService;
import com.sap.cds.services.handler.annotations.ServiceName;
import com.sap.cds.services.persistence.PersistenceService;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ServiceName(value = "*", type = ApplicationService.class)
public class AttachmentsHandlerUtils {

  private static final Logger logger = LoggerFactory.getLogger(AttachmentsHandlerUtils.class);

  private AttachmentsHandlerUtils() {
    // Doesn't do anything
  }

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

  public static List<Map<String, Object>> findNestedAttachments(
      Map<String, Object> entity, String attachmentKey, String parentKey) {
    System.out.println(
        "Searching for attachments with key '" + attachmentKey + " under parent key " + parentKey);
    return findNestedAttachments(entity, attachmentKey, parentKey, null);
  }

  public static List<String> getAttachmentEntityPathsWithActualPropertyNames(
      CdsModel model, CdsEntity entity, PersistenceService persistenceService) {
    try {
      List<String> actualPaths = new ArrayList<>();

      // Get all compositions from the target entity
      entity
          .compositions()
          .forEach(
              composition -> {
                String compositionName = composition.getName();
                String compositionTargetEntityName = "";
                if (composition.getType().isAssociation()) {
                  CdsAssociationType assocType = (CdsAssociationType) composition.getType();
                  compositionTargetEntityName = assocType.getTarget().getQualifiedName();
                }

                System.out.println(
                    "Processing composition: "
                        + compositionName
                        + " -> "
                        + compositionTargetEntityName);

                // Check if the target entity of this composition has attachments
                if (!compositionTargetEntityName.isEmpty()) {
                  Optional<CdsEntity> targetEntityOpt =
                      model.findEntity(compositionTargetEntityName);
                  if (targetEntityOpt.isPresent()) {
                    CdsEntity targetEntity = targetEntityOpt.get();

                    // Get attachment paths from the target entity
                    SDMAssociationCascader cascader = new SDMAssociationCascader();
                    SDMAttachmentsReader reader =
                        new SDMAttachmentsReader(cascader, persistenceService);
                    List<String> attachmentPaths =
                        reader.getAttachmentEntityPaths(model, targetEntity);

                    // Transform the paths to use the actual composition property name
                    for (String attachmentPath : attachmentPaths) {
                      String actualPath = buildActualPath(entity, compositionName, attachmentPath);
                      if (actualPath != null) {
                        actualPaths.add(actualPath);
                        System.out.println("Built actual path: " + actualPath);
                      }
                    }
                  }
                }
              });

      return actualPaths;
    } catch (Exception e) {
      logger.error("Error getting attachment entity paths with actual property names", e);
      return new ArrayList<>();
    }
  }

  /**
   * Gets both attachment entity paths and their corresponding actual property paths as a map. This
   * method combines the logic of both getAttachmentEntityPaths and
   * getAttachmentEntityPathsWithActualPropertyNames to ensure accurate mapping between entity paths
   * and actual property paths.
   *
   * @param model the CDS model
   * @param entity the target entity
   * @param persistenceService the persistence service
   * @return a map where key is the entity path (e.g., "AdminService.Chapters.attachments") and
   *     value is the actual property path (e.g., "AdminService.chapters123.attachments")
   */
  public static Map<String, String> getAttachmentPathMapping(
      CdsModel model, CdsEntity entity, PersistenceService persistenceService) {
    try {
      Map<String, String> pathMapping = new HashMap<>();

      // Get all compositions from the target entity
      entity
          .compositions()
          .forEach(
              composition -> {
                String compositionName = composition.getName();
                String compositionTargetEntityName = "";
                if (composition.getType().isAssociation()) {
                  CdsAssociationType assocType = (CdsAssociationType) composition.getType();
                  compositionTargetEntityName = assocType.getTarget().getQualifiedName();
                }

                System.out.println(
                    "Processing composition: "
                        + compositionName
                        + " -> "
                        + compositionTargetEntityName);

                // Check if the target entity of this composition has attachments
                if (!compositionTargetEntityName.isEmpty()) {
                  Optional<CdsEntity> targetEntityOpt =
                      model.findEntity(compositionTargetEntityName);
                  if (targetEntityOpt.isPresent()) {
                    CdsEntity targetEntity = targetEntityOpt.get();

                    // Get attachment paths from the target entity
                    SDMAssociationCascader cascader = new SDMAssociationCascader();
                    SDMAttachmentsReader reader =
                        new SDMAttachmentsReader(cascader, persistenceService);
                    List<String> attachmentPaths =
                        reader.getAttachmentEntityPaths(model, targetEntity);

                    // For each attachment path, create both the entity path and actual path
                    for (String attachmentPath : attachmentPaths) {
                      // Build the entity-based path (using entity name from target)
                      String entityPath = buildEntityPath(entity, targetEntity, attachmentPath);

                      // Build the actual property-based path (using composition property name)
                      String actualPath = buildActualPath(entity, compositionName, attachmentPath);

                      if (entityPath != null && actualPath != null) {
                        pathMapping.put(entityPath, actualPath);
                        System.out.println(
                            "Mapped entity path: " + entityPath + " -> actual path: " + actualPath);
                      }
                    }
                  }
                }
              });

      return pathMapping;
    } catch (Exception e) {
      logger.error("Error getting attachment path mapping", e);
      return new HashMap<>();
    }
  }

  private static String buildEntityPath(
      CdsEntity parentEntity, CdsEntity targetEntity, String attachmentPath) {
    try {
      String[] pathParts = attachmentPath.split("\\.");
      if (pathParts.length >= 3) {
        // Get the service name (first part)
        String serviceName = pathParts[0];

        // Get the target entity name (without service prefix)
        String targetEntityName = targetEntity.getName();

        // Get the attachment part (last part)
        String attachmentPart = pathParts[pathParts.length - 1];

        // Build the entity path: ServiceName.EntityName.attachments
        return serviceName + "." + targetEntityName + "." + attachmentPart;
      }
    } catch (Exception e) {
      logger.warn(
          "Failed to build entity path for target entity '{}' and attachment path '{}'",
          targetEntity.getName(),
          attachmentPath,
          e);
    }
    return null;
  }

  private static String buildActualPath(
      CdsEntity parentEntity, String compositionPropertyName, String attachmentPath) {
    try {
      String[] pathParts = attachmentPath.split("\\.");
      if (pathParts.length >= 3) {
        // Get the service name (first part)
        String serviceName = pathParts[0];

        // Replace the entity name with the composition property name
        // Keep the attachment part (last part)
        String attachmentPart = pathParts[pathParts.length - 1];

        // Build the new path: ServiceName.compositionPropertyName.attachments
        return serviceName + "." + compositionPropertyName + "." + attachmentPart;
      }
    } catch (Exception e) {
      logger.warn(
          "Failed to build actual path for composition '{}' and attachment path '{}'",
          compositionPropertyName,
          attachmentPath,
          e);
    }
    return null;
  }

  private static List<Map<String, Object>> findNestedAttachments(
      Map<String, Object> entity, String attachmentKey, String parentKey, String currentParentKey) {
    System.out.println(
        "Searching for attachments with key '"
            + attachmentKey
            + " under parent key "
            + parentKey
            + " Current parent key: "
            + currentParentKey);
    List<Map<String, Object>> result = new ArrayList<>();

    for (Map.Entry<String, Object> entry : entity.entrySet()) {
      String key = entry.getKey();
      Object value = entry.getValue();

      // If we found the attachment key
      if (attachmentKey.equals(key) && value instanceof List) {
        // Check if the parent matches (if parentKey is specified)
        if (parentKey == null || isCorrectParentContext(currentParentKey, parentKey)) {
          try {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> attachments = (List<Map<String, Object>>) value;
            result.addAll(attachments);
            System.out.println(
                "Found "
                    + attachments.size()
                    + " attachments under key '"
                    + key
                    + (currentParentKey != null
                        ? "' with parent '" + currentParentKey + "'"
                        : "'"));
          } catch (ClassCastException e) {
            logger.warn("Failed to cast attachments list for key '{}': {}", key, e.getMessage());
          }
        }
      }
      // Recursively search in nested objects
      else if (value instanceof Map) {
        try {
          @SuppressWarnings("unchecked")
          Map<String, Object> nestedMap = (Map<String, Object>) value;
          result.addAll(findNestedAttachments(nestedMap, attachmentKey, parentKey, key));
        } catch (ClassCastException e) {
          logger.warn("Failed to cast nested map for key '{}': {}", key, e.getMessage());
        }
      }
      // Recursively search in lists
      else if (value instanceof List) {
        try {
          List<?> list = (List<?>) value;
          for (Object item : list) {
            if (item instanceof Map) {
              @SuppressWarnings("unchecked")
              Map<String, Object> itemMap = (Map<String, Object>) item;
              result.addAll(findNestedAttachments(itemMap, attachmentKey, parentKey, key));
            }
          }
        } catch (ClassCastException e) {
          logger.warn("Failed to process list for key '{}': {}", key, e.getMessage());
        }
      }
    }

    return result;
  }

  /**
   * Checks if the current parent context matches the expected parent key. If no parent key is
   * expected (null), or if there's no current parent context, it's considered a match (root level
   * attachments).
   *
   * @param currentParentKey The current parent key in the traversal
   * @param expectedParentKey The expected parent key to match against
   * @return true if the parent context matches or if no specific parent is required
   */
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

  public static Map<String, Object> wrapEntityWithParent(
      Map<String, Object> root, String targetEntity) {
    Map<String, Object> wrapper = new HashMap<>();
    wrapper.put(targetEntity, root);
    return wrapper;
  }
}
