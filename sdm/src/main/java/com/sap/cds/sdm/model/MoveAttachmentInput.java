package com.sap.cds.sdm.model;

import java.util.List;

/**
 * The class {@link MoveAttachmentInput} is used to store the input for moving attachments. This
 * model supports both regular entities and projection entities by using facet-based navigation.
 *
 * @param sourceFolderId The folder ID in SDM from which attachments should be moved
 * @param sourceFacet The full facet path of the source entity (e.g., "Service.Entity.composition")
 *     that will be internally parsed to determine source parent entity and composition name for
 *     cleanup
 * @param upId The key of the target parent entity instance
 * @param targetFacet The qualified name of the target facet/entity (e.g., "Service.Attachments")
 * @param objectIds List of attachment object IDs to move
 */
public record MoveAttachmentInput(
    String sourceFolderId,
    String sourceFacet,
    String targetUpId,
    String targetFacet,
    List<String> objectIds) {}
