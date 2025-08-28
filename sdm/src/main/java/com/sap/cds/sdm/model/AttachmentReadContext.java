package com.sap.cds.sdm.model;

import com.sap.cds.services.EventContext;
import com.sap.cds.services.EventName;

@EventName("openAttachment")
public interface AttachmentReadContext extends EventContext {

  static AttachmentReadContext create() {
    return (AttachmentReadContext) EventContext.create(AttachmentReadContext.class, null);
  }

  void setResult(String res);

  String getResult();
}
