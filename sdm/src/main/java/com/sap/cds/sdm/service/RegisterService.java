package com.sap.cds.sdm.service;

import com.sap.cds.sdm.model.CopyAttachmentInput;
import com.sap.cds.services.Service;

public interface RegisterService extends Service {
  String SDM_NAME = "SDMAttachmentService$Default";
  String EVENT_COPY_ATTACHMENT = "COPY_ATTACHMENT";

  /**
   * Copies attachments using the facet-based approach. This method supports both regular entities
   * and projection entities by internally parsing the facet to determine parent entity and
   * composition name.
   *
   * @param input The copy attachment input containing facet and object IDs
   * @param isSystemUser Whether to use system user flow
   */
  public void copyAttachments(CopyAttachmentInput input, boolean isSystemUser);
}
