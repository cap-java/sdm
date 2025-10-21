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

  public static Map<String, String> getAttachmentPathMapping(
      CdsModel model, CdsEntity entity, PersistenceService persistenceService) {
    try {
      Map<String, String> pathMapping = new HashMap<>();

      // First, check for direct attachments on the root entity itself
      SDMAssociationCascader cascader = new SDMAssociationCascader();
      SDMAttachmentsReader reader = new SDMAttachmentsReader(cascader, persistenceService);

      // Check each composition to see if it's a direct attachment
      entity
          .compositions()
          .forEach(
              composition -> {
                String compositionName = composition.getName();
                if (composition.getType().isAssociation()) {
                  CdsAssociationType assocType = (CdsAssociationType) composition.getType();
                  String targetAspect =
                      assocType.getTargetAspect().isPresent()
                          ? assocType.getTargetAspect().get().getQualifiedName()
                          : null;

                  // Check if this is a direct attachment composition
                  if (targetAspect != null
                      && targetAspect.equalsIgnoreCase("sap.attachments.Attachments")) {
                    String serviceName = entity.getQualifiedName().split("\\.")[0];
                    String entityName = entity.getName();
                    String directPath = serviceName + "." + entityName + "." + compositionName;

                    // For direct attachments, entity path and actual path are the same
                    pathMapping.put(directPath, directPath);
                    System.out.println(
                        "Mapped direct attachment path: " + directPath + " -> " + directPath);
                  }
                }
              });

      // Then, get all compositions from the target entity for nested attachments
      entity
          .compositions()
          .forEach(
              composition -> {
                String compositionName = composition.getName();
                String compositionTargetEntityName = "";
                if (composition.getType().isAssociation()) {
                  CdsAssociationType assocType = (CdsAssociationType) composition.getType();
                  String targetAspect =
                      assocType.getTargetAspect().isPresent()
                          ? assocType.getTargetAspect().get().getQualifiedName()
                          : null;

                  // Skip direct attachment compositions (already handled above)
                  if (targetAspect != null
                      && targetAspect.equalsIgnoreCase("sap.attachments.Attachments")) {
                    return; // Skip this composition as it's a direct attachment
                  }

                  compositionTargetEntityName = assocType.getTarget().getQualifiedName();
                }

                System.out.println(
                    "Processing nested composition: "
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
                            "Mapped nested entity path: "
                                + entityPath
                                + " -> actual path: "
                                + actualPath);
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
    List<Map<String, Object>> attachments =
        AttachmentsHandlerUtils.findNestedAttachments(
            entity, attachmentKeyFromComposition, parentKeyFromComposition);
    return attachments;
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
