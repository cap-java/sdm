package com.sap.cds.sdm.service;

import com.sap.cds.sdm.model.CopyAttachmentInput;
import com.sap.cds.services.Service;

public interface RegisterService extends Service {
  String SDM_NAME = "SDMAttachmentService$Default";
  String EVENT_COPY_ATTACHMENT = "COPY_ATTACHMENT";

  /**
   * Copies attachments using the new parent entity and composition approach. This method supports
   * both regular entities and projection entities.
   *
   * @param input The copy attachment input containing parent entity, composition name, and object
   *     IDs
   * @param isSystemUser Whether to use system user flow
   */
  public void copyAttachments(CopyAttachmentInput input, boolean isSystemUser);
}
