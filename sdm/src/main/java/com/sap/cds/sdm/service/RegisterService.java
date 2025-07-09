package com.sap.cds.sdm.service;

import com.sap.cds.feature.attachments.service.AttachmentService;
import java.io.InputStream;

public interface RegisterService extends AttachmentService {
  String SDM_NAME = "SDMAttachmentService$Default";

  InputStream readSDMAttachment(String contentId);
}
