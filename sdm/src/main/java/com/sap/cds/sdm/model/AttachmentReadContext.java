package com.sap.cds.sdm.model;

import com.sap.cds.feature.attachments.generated.cds4j.sap.attachments.MediaData;
import com.sap.cds.services.EventContext;
import com.sap.cds.services.EventName;

@EventName("openAttachment")
public interface AttachmentReadContext extends EventContext {

  static AttachmentReadContext create() {
    return (AttachmentReadContext) EventContext.create(AttachmentReadContext.class, null);
  }

  MediaData getData();

  void setData(MediaData var1);

  String getContentId();

  void setContentId(String var1);
}
