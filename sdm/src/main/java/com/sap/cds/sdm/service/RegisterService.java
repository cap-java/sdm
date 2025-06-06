package com.sap.cds.sdm.service;

import com.sap.cds.sdm.model.CopyAttachmentInput;
import com.sap.cds.services.Service;

public interface RegisterService extends Service {
  String SDM_NAME = "SDMAttachmentService$Default";
  String EVENT_COPY_ATTACHMENT = "COPY_ATTACHMENT";

  public void copyAttachments(CopyAttachmentInput input);
}
