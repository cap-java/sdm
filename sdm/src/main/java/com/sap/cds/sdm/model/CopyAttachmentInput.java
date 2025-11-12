package com.sap.cds.sdm.model;

import java.util.List;

/**
 * The class {@link CopyAttachmentInput} is used to store the input for copying attachments. This
 * model supports both regular entities and projection entities by using facet-based navigation.
 *
 * @param upId The key of the parent entity instance
 * @param facet The full facet path (e.g., "Service.Entity.composition") that will be internally
 *     parsed to determine parent entity and composition name
 * @param objectIds The list of attachment object IDs to be copied
 */
public record CopyAttachmentInput(String upId, String facet, List<String> objectIds) {}
