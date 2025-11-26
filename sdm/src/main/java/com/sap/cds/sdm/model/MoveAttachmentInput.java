package com.sap.cds.sdm.model;

import java.util.List;

/**
 * The class {@link MoveAttachmentInput} is used to store the input for moving attachments. This
 * model supports both regular entities and projection entities by using facet-based navigation.
 *
 * @param sourceFolderId The folder ID in SDM from which attachments should be moved
 * @param upId The key of the target parent entity instance
 * @param targetFacet The full facet path (e.g., "Service.Entity.composition") that will be
 *     internally parsed to determine target parent entity and composition name
 * @param objectIds The list of attachment object IDs to be moved
 */
public record MoveAttachmentInput(
    String sourceFolderId, String upId, String targetFacet, List<String> objectIds) {}
