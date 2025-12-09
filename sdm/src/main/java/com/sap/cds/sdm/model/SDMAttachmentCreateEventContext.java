package com.sap.cds.sdm.model;

import com.sap.cds.feature.attachments.service.AttachmentService;
import com.sap.cds.feature.attachments.service.model.servicehandler.AttachmentCreateEventContext;
import com.sap.cds.services.EventContext;
import com.sap.cds.services.EventName;

@EventName(AttachmentService.EVENT_CREATE_ATTACHMENT)
public interface SDMAttachmentCreateEventContext extends AttachmentCreateEventContext {

  static SDMAttachmentCreateEventContext create() {
    return (SDMAttachmentCreateEventContext)
        EventContext.create(SDMAttachmentCreateEventContext.class, null);
  }

  void setResult(String res);

  String getResult();

  void setUploadStatus(String uploadStatus);

  String getUploadStatus();
}
