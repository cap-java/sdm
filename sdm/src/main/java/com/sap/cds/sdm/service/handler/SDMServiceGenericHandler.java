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

  // @On(event = "copyAttachments")
  // public void copyAttachments(EventContext context) throws IOException {
  //   String upID = context.get("up__ID").toString();
  //   String objectIdsString = context.get("objectIds").toString();
  //   List<String> objectIds =
  // Arrays.stream(objectIdsString.split(",")).map(String::trim).toList();
  //   var copyEventInput =
  //       new CopyAttachmentInput(upID, context.getTarget().getQualifiedName(), objectIds);
  //   attachmentService.copyAttachments(copyEventInput, context.getUserInfo().isSystemUser());
  //   context.setCompleted();
  // }

  @On(event = "copyAttachments")
  public void copyAttachments(EventContext context) throws IOException {
    String upID = context.get("up__ID").toString();
    System.out.println("upID: " + upID);
    String objectIdsString = context.get("objectIds").toString();
    System.out.println("objectIdsString: " + objectIdsString);
    List<String> objectIds = Arrays.stream(objectIdsString.split(",")).map(String::trim).toList();

    // Use the full target qualified name as the facet
    String facet = context.getTarget().getQualifiedName();
    System.out.println("facet: " + facet);

    var copyEventInput = new CopyAttachmentInput(upID, facet, objectIds);

    attachmentService.copyAttachments(copyEventInput, context.getUserInfo().isSystemUser());
    context.setCompleted();
  }
}
