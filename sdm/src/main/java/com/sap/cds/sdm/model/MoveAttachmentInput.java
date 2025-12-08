package com.sap.cds.sdm.model;

import java.util.List;
import java.util.Optional;

/**
 * The class {@link MoveAttachmentInput} is used to store the input for moving attachments. This
 * model supports both regular entities and projection entities by using facet-based navigation.
 *
 * @param sourceFolderId The folder ID in SDM from which attachments should be moved
 * @param targetUpId The key of the target parent entity instance
 * @param targetFacet The qualified name of the target facet/entity (e.g., "Service.Attachments")
 * @param objectIds List of attachment object IDs to move
 * @param sourceFacet Optional full facet path of the source entity (e.g.,
 *     "Service.Entity.composition") that will be internally parsed to determine source parent
 *     entity and composition name for cleanup. If not provided, no source cleanup will be
 *     performed.
 */
public record MoveAttachmentInput(
    String sourceFolderId,
    String targetUpId,
    String targetFacet,
    List<String> objectIds,
    Optional<String> sourceFacet) {

  /** Constructor for when sourceFacet is omitted entirely. Defaults to Optional.empty(). */
  public MoveAttachmentInput(
      String sourceFolderId, String targetUpId, String targetFacet, List<String> objectIds) {
    this(sourceFolderId, targetUpId, targetFacet, objectIds, Optional.empty());
  }

  /**
   * Constructor that accepts a plain String for sourceFacet and wraps it in Optional. This allows
   * UI/OData callers to pass a simple string value (or null) which will be automatically converted
   * to Optional.
   */
  public MoveAttachmentInput(
      String sourceFolderId,
      String targetUpId,
      String targetFacet,
      List<String> objectIds,
      String sourceFacetString) {
    this(
        sourceFolderId, targetUpId, targetFacet, objectIds, Optional.ofNullable(sourceFacetString));
  }
}
