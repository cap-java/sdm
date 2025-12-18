package com.sap.cds.sdm.model;

import com.sap.cds.services.EventContext;
import com.sap.cds.services.EventName;
import org.json.JSONObject;

@EventName("changelog")
public interface AttachmentLogContext extends EventContext {

  static AttachmentLogContext create() {
    return (AttachmentLogContext) EventContext.create(AttachmentLogContext.class, null);
  }

  void setResult(JSONObject res);

  JSONObject getResult();
}
