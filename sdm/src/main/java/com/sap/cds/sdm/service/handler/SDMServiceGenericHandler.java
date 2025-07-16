package com.sap.cds.sdm.service.handler;

import com.sap.cds.sdm.model.CopyAttachmentInput;
import com.sap.cds.sdm.service.RegisterService;
import com.sap.cds.services.EventContext;
import com.sap.cds.services.handler.EventHandler;
import com.sap.cds.services.handler.annotations.On;
import com.sap.cds.services.handler.annotations.ServiceName;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;

@ServiceName({"*"})
public class SDMServiceGenericHandler implements EventHandler {
  private final RegisterService attachmentService;

  public SDMServiceGenericHandler(RegisterService attachmentService) {
    this.attachmentService = attachmentService;
  }

  @On(event = "copyAttachments")
  public void copyAttachments(EventContext context) throws IOException {
    String up__ID = context.get("up__ID").toString();
    String objectIdsString = context.get("objectIds").toString();
    List<String> objectIds = Arrays.asList(objectIdsString.split(" "));
    var copyEventInput =
        new CopyAttachmentInput(up__ID, context.getTarget().getQualifiedName(), objectIds);
    attachmentService.copyAttachments(copyEventInput, context.getUserInfo().isSystemUser());
    context.setCompleted();
  }
}
