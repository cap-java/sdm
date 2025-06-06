package com.sap.cds.sdm.service.handler;

import com.sap.cds.sdm.model.CopyAttachmentInput;
import com.sap.cds.sdm.service.RegisterService;
import com.sap.cds.sdm.service.SDMService;
import com.sap.cds.services.EventContext;
import com.sap.cds.services.handler.EventHandler;
import com.sap.cds.services.handler.annotations.On;
import com.sap.cds.services.handler.annotations.ServiceName;
import com.sap.cds.services.persistence.PersistenceService;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;

@ServiceName({"*"})
public class SDMServiceGenericHandler implements EventHandler {
  private final PersistenceService persistenceService;
  private final SDMService sdmService;
  private final RegisterService attachmentService;

  public SDMServiceGenericHandler(
      PersistenceService persistenceService,
      SDMService sdmService,
      RegisterService attachmentService) {
    this.persistenceService = persistenceService;
    this.sdmService = sdmService;
    this.attachmentService = attachmentService;
  }

  @On(event = "copyAttachments")
  public void copyAttachments(EventContext context) throws IOException {
    System.out.println("Inside our button correct method");

    // Fetching the new UP ID and object ID
    String up__ID = context.get("up__ID").toString();
    String objectIdsString = context.get("objectIds").toString();
    List<String> objectIds = Arrays.asList(objectIdsString.split(" "));
    String facet = context.getTarget().getQualifiedName().split("\\.")[2];

    // CdsModel cdsModel = context.getModel();
    // CqnAnalyzer cqnAnalyzer = CqnAnalyzer.create(cdsModel);
    // Optional<CdsEntity> attachmentDraftEntity =
    // cdsModel.findEntity(context.getTarget().getQualifiedName() + "_drafts");
    // Optional<CdsElement> upAssociation = attachmentDraftEntity.get().findAssociation("up_");

    // // if association is found, try to get foreign key to parent entity
    // if (upAssociation.isPresent()) {
    //     CdsElement association = upAssociation.get();
    //     // get association type
    //     CdsAssociationType assocType = association.getType();
    //     // get the refs of the association
    //     //List<String> fkElements = assocType.refs().map(ref -> "up__" + ref.path()).toList();
    //     // Map<String, Object> targetKeys = cqnAnalyzer.analyze((CqnSelect)
    // context.get("cqn")).targetKeyValues();
    //     // String upIdKey = "";
    //     // upIdKey = fkElements.get(0);
    // }
    // //Fetching the current UP ID

    // Fetch the list of object ids using the ID (upid on line 74)
    // copyattachmentinput model needs to be created by setting values
    System.out.println("UP ID : " + up__ID);
    System.out.println("Facet : " + facet);
    System.out.println("Object IDs : " + objectIds);
    // RegisterService s = new SDMAttachmentsService();
    var copyEventInput = new CopyAttachmentInput(up__ID, facet, objectIds);
    attachmentService.copyAttachments(copyEventInput);
    context.setCompleted();
  }
}

// First second method in this file is called from our button which nexus people will implement
// this will call SDMAttachmentsService.copyAttachments method
// That method will emit context which calls the first copyAttachments method in this file
// Then in implementation we have to create all the documents in source and update in the db
