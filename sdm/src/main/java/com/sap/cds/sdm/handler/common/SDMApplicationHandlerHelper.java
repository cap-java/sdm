package com.sap.cds.sdm.handler.common;

import com.sap.cds.reflect.CdsStructuredType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The class {@link SDMApplicationHandlerHelper} provides helper methods for the SDM attachment
 * application handlers.
 */
public final class SDMApplicationHandlerHelper {
  private static final Logger logger = LoggerFactory.getLogger(SDMApplicationHandlerHelper.class);
  private static final String ANNOTATION_IS_MEDIA_DATA = "_is_media_data";

  /**
   * Checks if the entity is a media entity. A media entity is one that carries the "_is_media_data"
   * annotation (set by the CAP attachments plugin on DB-backed attachment entities), or — as a
   * fallback for service-layer-only draft entities whose annotation may not be propagated — one
   * that has both the SDM-specific "objectId" element and the "content" element that is
   * characteristic of the sap.attachments.Attachments aspect.
   *
   * @param baseEntity The entity to check
   * @return <code>true</code> if the entity is a media entity, <code>false</code> otherwise
   */
  public static boolean isMediaEntity(CdsStructuredType baseEntity) {
    boolean isMedia = baseEntity.getAnnotationValue(ANNOTATION_IS_MEDIA_DATA, false);
    if (!isMedia) {
      // Fallback for service-layer-only entities (e.g. SupplierBidTermValuesServiceEntity
      // attachments) whose inline Composition of Attachments does not receive the
      // _is_media_data annotation at runtime. Presence of both "objectId" (SDM-specific
      // extension) and "content" (core MediaData field) is a reliable structural signal.
      isMedia =
          baseEntity.findElement("objectId").isPresent()
              && baseEntity.findElement("content").isPresent();
      if (isMedia) {
        logger.debug(
            "Entity {} identified as media entity via structural fallback (objectId + content)",
            baseEntity.getQualifiedName());
      }
    }
    logger.debug("Entity {} isMediaEntity: {}", baseEntity.getQualifiedName(), isMedia);
    return isMedia;
  }

  private SDMApplicationHandlerHelper() {
    // avoid instantiation
  }
}
