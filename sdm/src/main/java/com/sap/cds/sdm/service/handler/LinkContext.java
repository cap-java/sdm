package com.sap.cds.sdm.service.handler;

import com.sap.cds.services.EventContext;
import com.sap.cds.services.EventName;

@EventName(SDMActionsService.EVENT_CREATE_LINK)
public interface LinkContext extends EventContext {
  static LinkContext create() {
    return EventContext.create(LinkContext.class, null);
  }

  String getLinkName();

  void setLinkName(String linkName);

  String getUrl();

  void setUrl(String url);
}
