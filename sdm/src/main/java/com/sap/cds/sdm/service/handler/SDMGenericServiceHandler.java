package com.sap.cds.sdm.service.handler;

import com.sap.cds.services.EventContext;
import com.sap.cds.services.handler.EventHandler;
import com.sap.cds.services.handler.annotations.On;
import com.sap.cds.services.handler.annotations.ServiceName;
import java.io.IOException;

@ServiceName({"*"})
public class SDMGenericServiceHandler implements EventHandler {

  public SDMGenericServiceHandler() {}

  @On(event = "openLink")
  public void open(EventContext context) throws IOException {
    // Check the action name and handle accordingly
    String eventName = context.getEvent();
    System.out.println(
        "Handling event: " + eventName + ":" + context.getTarget() + ":" + context.get("cqn"));
    context.setCompleted();
  }
}
