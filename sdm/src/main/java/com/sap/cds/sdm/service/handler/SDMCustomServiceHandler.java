package com.sap.cds.sdm.service.handler;

import com.sap.cds.sdm.service.RegisterService;
import com.sap.cds.services.handler.annotations.On;
import com.sap.cds.services.handler.annotations.ServiceName;
import java.io.IOException;

@ServiceName(value = "*", type = RegisterService.class)
public class SDMCustomServiceHandler {

  @On(event = RegisterService.EVENT_COPY_ATTACHMENT)
  public void copyAttachments(AttachmentCopyEventContext context) throws IOException {
    System.out.println("Inside correct method - Nexus");
    // Check the action name and handle accordingly
    String eventName = context.getEvent();
    System.out.println(
        "Handling event: " + eventName + ":" + context.getUpId() + ":" + context.getObjectIds());
    // copyAttachmentsImpl(context);
    context.setCompleted();
  }
}
