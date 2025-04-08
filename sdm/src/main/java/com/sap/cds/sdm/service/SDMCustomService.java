package com.sap.cds.sdm.service;

import com.sap.cds.feature.attachments.service.AttachmentService;

public interface SDMCustomService extends AttachmentService {
  String EVENT_CREATE_LINK = "createLink";
  String EVENT_EDIT_LINK = "editLink";
}
