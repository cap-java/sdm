package com.sap.cds.sdm.handler.applicationservice.helper;

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
}
