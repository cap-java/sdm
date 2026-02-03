package com.sap.cds.sdm.model;

import com.sap.cds.services.EventContext;
import com.sap.cds.services.EventName;
import java.util.Map;

@EventName("moveAttachments")
public interface AttachmentMoveRequestContext extends EventContext {

  static AttachmentMoveRequestContext create() {
    return (AttachmentMoveRequestContext)
        EventContext.create(AttachmentMoveRequestContext.class, null);
  }

  void setResult(Map<String, Object> res);

  Map<String, Object> getResult();
}
