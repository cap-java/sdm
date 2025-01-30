package com.sap.cds.sdm.service.handler;

import com.sap.cds.feature.attachments.service.AttachmentService;

public interface SDMActionsService extends AttachmentService {
  String EVENT_CREATE_LINK = "createLink";
  String EVENT_EDIT_LINK = "editLink";

  void createLink(String linkName, String url);

  void editLink(String linkName, String url);
}
