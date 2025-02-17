package com.sap.cds.sdm.service.handler;

import com.sap.cds.CdsDataProcessor;
import com.sap.cds.ql.Select;
import com.sap.cds.reflect.CdsEntity;
import com.sap.cds.reflect.CdsService;
import com.sap.cds.sdm.constants.SDMConstants;
import com.sap.cds.sdm.handler.TokenHandler;
import com.sap.cds.sdm.model.CmisDocument;
import com.sap.cds.sdm.model.SDMCredentials;
import com.sap.cds.sdm.service.SDMService;
import com.sap.cds.services.EventContext;
import com.sap.cds.services.authentication.AuthenticationInfo;
import com.sap.cds.services.authentication.JwtTokenAuthenticationInfo;
import com.sap.cds.services.handler.EventHandler;
import com.sap.cds.services.handler.annotations.On;
import com.sap.cds.services.handler.annotations.ServiceName;
import com.sap.cds.services.persistence.PersistenceService;
import java.io.IOException;
import java.util.stream.Stream;
import org.json.JSONObject;

// Ensures this handler is registered for all services
@ServiceName({"*"})
public class SDMServiceGenericHandler implements EventHandler {
  private final PersistenceService persistenceService;
  private final SDMService sdmService;

  public SDMServiceGenericHandler(PersistenceService persistenceService, SDMService sdmService) {
    this.persistenceService = persistenceService;
    this.sdmService = sdmService;
  }

  @On // Listen to all events
  public void getActions(EventContext context) throws IOException {
    // Check the action name and handle accordingly
    String eventName = context.getEvent();
    System.out.println("Handling event: " + eventName);

    // ApplicationHandlerHelper.callProcessor(entity, data,
    // ApplicationHandlerHelper.MEDIA_CONTENT_FILTER, converter);
    switch (eventName) {
      case "createLink":
        createLink(context);
        break;
      case "editLink":
        editLink(context);
        break;
    }
  }

  private void createLink(EventContext context) throws IOException {
    System.out.println(
        "Parameters from createLink "
            + context.get("url").toString()
            + ":"
            + context.get("name").toString()
            + context.keySet().stream().findFirst()
            + ":");
    String entityName = "";

    Stream<CdsService> services = context.getModel().services();
    services.forEach(
        service -> {
          if (context.getService().getName().equals(service.getName())) {
            Stream<CdsEntity> entities = service.entities();
            entities.forEach(
                entity -> {
                  if (entity.getQualifiedName().contains("attachments_drafts")) {
                    var select = Select.from(entity);
                    var result = persistenceService.run(select);
                    System.out.println("Result " + result.first() + ":" + entity.getName());
                  }
                });
            //              Stream<CdsAction> actions = service.actions();
            //              actions.forEach(
            //                      action -> {
            //                          System.out.println(
            //                                  "Action Name: " + action.getName() + ":" +
            // service.getQualifiedName());
            //                          if (action.getName().equals("createLink")) {
            //                          }
            //                      });
          }
        });
    String subdomain = "";
    String repositoryId = SDMConstants.REPOSITORY_ID;
    AuthenticationInfo authInfo = context.getAuthenticationInfo();
    JwtTokenAuthenticationInfo jwtTokenInfo = authInfo.as(JwtTokenAuthenticationInfo.class);
    String jwtToken = jwtTokenInfo.getToken();
    System.out.println("Auth token " + jwtToken);
    CmisDocument cmisDocument = new CmisDocument();
    cmisDocument.setFileName(context.get("name").toString());
    cmisDocument.setMimeType("application/internet-shortcut");
    cmisDocument.setRepositoryId(repositoryId);
    cmisDocument.setUrl(context.get("url").toString());
    SDMCredentials sdmCredentials = TokenHandler.getSDMCredentials();
    JSONObject createResult =
        sdmService.createDocument(cmisDocument, sdmCredentials, jwtToken, "link");

    cmisDocument.setObjectId(createResult.get("objectId").toString());
    // add links to link table

    // DBQuery.addLinkToDraft(attachmentDraftEntity.get(), persistenceService, cmisDocument);
    context.setCompleted();
  }

  private void editLink(EventContext context) {
    System.out.println(
        "Parameters from edit "
            + context.get("url").toString()
            + ":"
            + context.get("name").toString());
  }
}
