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
   * Checks if the entity is a media entity. A media entity is an entity that is annotated with the
   * annotation "_is_media_data".
   *
   * @param baseEntity The entity to check
   * @return <code>true</code> if the entity is a media entity, <code>false</code> otherwise
   */
  public static boolean isMediaEntity(CdsStructuredType baseEntity) {
    boolean isMedia = baseEntity.getAnnotationValue(ANNOTATION_IS_MEDIA_DATA, false);
    logger.debug("Entity {} isMediaEntity: {}", baseEntity.getQualifiedName(), isMedia);
    return isMedia;
  }

  private SDMApplicationHandlerHelper() {
    // avoid instantiation
  }
}
