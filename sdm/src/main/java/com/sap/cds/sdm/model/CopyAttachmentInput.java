package com.sap.cds.sdm.model;

import java.util.List;

/**
 * The class {@link CopyAttachmentInput} is used to store the input for creating an attachment.
 *
 * @param upId The keys for the attachment entity
 * @param facet
 * @param objectIds
 */
public record CopyAttachmentInput(String upId, String facet, List<String> objectIds) {}
