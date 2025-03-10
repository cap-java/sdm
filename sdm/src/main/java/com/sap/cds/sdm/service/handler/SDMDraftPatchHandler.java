package com.sap.cds.sdm.service.handler;

import com.sap.cds.services.draft.DraftService;
import com.sap.cds.services.handler.annotations.ServiceName;
import com.sap.cds.services.persistence.PersistenceService;

@ServiceName(value = "*", type = DraftService.class)
public class SDMDraftPatchHandler {
  private final PersistenceService persistence;

  public SDMDraftPatchHandler(PersistenceService persistence) {
    this.persistence = persistence;
  }

  //  @On(event = DraftService.EVENT_DRAFT_PATCH)
  //  @HandlerOrder(HandlerOrder.LATE)
  public void processOnDraftPatch() {
    // System.out.println("During Draft Patch " + data + "::::" + context.getEvent());
    //        CdsDataProcessor.Converter converter = (path, element, value) -> {
    //            var draftElement = path.target().entity().getQualifiedName().endsWith(
    //                    DraftConstants.DRAFT_TABLE_POSTFIX) ? path.target().entity() :
    // path.target().entity().getTargetOf(
    //                    DraftConstants.SIBLING_ENTITY);
    //            var select = Update.entity(draftElement.getQualifiedName());
    //            var result = persistence.run(select);
    //
    //            //return
    // ModifyApplicationHandlerHelper.handleAttachmentForEntity(result.listOf(CdsData.class),
    // eventFactory, context,
    //                   // path, value);
    //        };
    //
    //       ApplicationHandlerHelper.callProcessor(context.getTarget(), data, filter, converter);

  }
}
