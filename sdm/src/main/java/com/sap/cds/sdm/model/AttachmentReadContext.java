package com.sap.cds.sdm.model;

import com.sap.cds.services.EventContext;
import com.sap.cds.services.EventName;

@EventName("openAttachment")
public interface AttachmentReadContext extends EventContext {

  static AttachmentReadContext create() {
    return (AttachmentReadContext) EventContext.create(AttachmentReadContext.class, null);
  }

  String getUrl();

  void setUrl(String var1);

  void setResult(String res);

  String getResult();
}
