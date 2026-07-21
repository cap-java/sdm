package com.sap.cds.sdm.model;

import com.sap.cds.services.EventContext;
import com.sap.cds.services.EventName;

@EventName("downloadSelectedAttachments")
public interface AttachmentDownloadContext extends EventContext {

  static AttachmentDownloadContext create() {
    return (AttachmentDownloadContext) EventContext.create(AttachmentDownloadContext.class, null);
  }

  void setResult(String res);

  String getResult();
}
