package com.sap.cds.sdm.handler.applicationservice;

import com.sap.cds.ql.CQL;
import com.sap.cds.ql.Predicate;
import com.sap.cds.ql.cqn.CqnSelect;
import com.sap.cds.ql.cqn.Modifier;
import com.sap.cds.sdm.constants.SDMConstants;
import com.sap.cds.sdm.persistence.DBQuery;
import com.sap.cds.services.cds.ApplicationService;
import com.sap.cds.services.cds.CdsReadEventContext;
import com.sap.cds.services.handler.EventHandler;
import com.sap.cds.services.handler.annotations.Before;
import com.sap.cds.services.handler.annotations.HandlerOrder;
import com.sap.cds.services.handler.annotations.ServiceName;
import com.sap.cds.services.persistence.PersistenceService;

@ServiceName(value = "*", type = ApplicationService.class)
public class SDMReadAttachmentsHandler implements EventHandler {
  private final PersistenceService persistenceService;
  private final DBQuery dbQuery;

  public SDMReadAttachmentsHandler(PersistenceService persistenceService, DBQuery dbQuery) {
    this.persistenceService = persistenceService;
    this.dbQuery = dbQuery;
  }

  @Before
  @HandlerOrder(HandlerOrder.DEFAULT)
  public void processBefore(CdsReadEventContext context) {
    String repositoryId = SDMConstants.REPOSITORY_ID;
    if (context.getTarget().getAnnotationValue(SDMConstants.ANNOTATION_IS_MEDIA_DATA, false)) {
      // Fetch all the mimetypes where type is  and the update type based on mimetye
      System.out.println("MIGRATION...");
      dbQuery.updateTypeForMimeType(context.getTarget(), persistenceService);
      CqnSelect copy =
          CQL.copy(
              context.getCqn(),
              new Modifier() {
                @Override
                public Predicate where(Predicate where) {
                  return CQL.and(where, CQL.get("repositoryId").eq(repositoryId));
                }
              });
      context.setCqn(copy);

    } else {
      context.setCqn(context.getCqn());
    }
  }
}
