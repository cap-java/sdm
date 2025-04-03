package com.sap.cds.sdm.service.handler;

import com.sap.cds.CdsData;
import com.sap.cds.feature.attachments.generated.cds4j.sap.attachments.Attachments;
import com.sap.cds.feature.attachments.handler.common.ApplicationHandlerHelper;
import com.sap.cds.reflect.CdsEntity;
import com.sap.cds.services.draft.DraftCreateEventContext;
import com.sap.cds.services.draft.DraftPatchEventContext;
import com.sap.cds.services.draft.DraftService;
import com.sap.cds.services.handler.EventHandler;
import com.sap.cds.services.handler.annotations.Before;
import com.sap.cds.services.handler.annotations.HandlerOrder;
import com.sap.cds.services.handler.annotations.ServiceName;
import com.sap.cds.services.persistence.PersistenceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ServiceName(value = "*", type = DraftService.class)
public class DraftAttachmentsHandler implements EventHandler {
  private static final Logger logger = LoggerFactory.getLogger(DraftAttachmentsHandler.class);
  private final PersistenceService persistenceService;

  public DraftAttachmentsHandler(PersistenceService persistenceService) {
    this.persistenceService = persistenceService;
  }

  @Before
  @HandlerOrder(HandlerOrder.LATE)
  void onCreateDraftAttachment(DraftCreateEventContext context, CdsData data) {
    CdsEntity target = context.getTarget();
    // check if target entity contains aspect Attachments
    if (ApplicationHandlerHelper.isMediaEntity(target)) {
      String fileName = (String) data.get(Attachments.FILE_NAME);
      // get unique identifier of attachment's parent entity, e.g. the Books entity
      // Parent parent = getParentId(target, data);
      logger.info("Creating draft attachment '{}'", fileName);

      // do something with the data of the draft attachments entity
    }
  }

  @Before
  @HandlerOrder(HandlerOrder.LATE)
  void patchDraftAttachment(DraftPatchEventContext context, CdsData data) {
    CdsEntity target = context.getTarget();

    // check if target entity contains aspect Attachments
    if (ApplicationHandlerHelper.isMediaEntity(target)) {
      // remove wrong mime type from data
      // TODO: remove this once the SAPUI5 sets the correct MIME type
      // data.remove(Attachments.MIME_TYPE);
    }
  }

  //  private static Parent getParentId(CdsEntity target, CdsData data) {
  //    Optional upAssociation = target.findAssociation("up_");
  //    // if association is found, try to get foreign key to parent entity
  //    if (upAssociation.isPresent()) {
  //      // get association type
  //      CdsAssociationType assocType = upAssociation.get().getType();
  //      // get the refs of the association
  //      List fkElements = assocType.refs().map(ref -> "up__" + ref.path()).toList();
  //      return new Parent(assocType.getTarget(), data.get(fkElements.get(0)));
  //    }
  //    return null;
  //  }
  record Parent(CdsEntity entity, Object id) {}
}
