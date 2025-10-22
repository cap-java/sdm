package com.sap.cds.sdm.model;

import java.util.List;

/**
 * The class {@link CopyAttachmentInput} is used to store the input for copying attachments. This
 * model supports both regular entities and projection entities by using parent entity and
 * composition navigation patterns.
 *
 * @param upId The key of the parent entity instance
 * @param parentEntity The qualified name of the parent entity that defines the attachments
 *     composition
 * @param compositionName The name of the composition property linking parent to attachment entity
 * @param objectIds The list of attachment object IDs to be copied
 */
public record CopyAttachmentInput(
    String upId, String parentEntity, String compositionName, List<String> objectIds) {}
