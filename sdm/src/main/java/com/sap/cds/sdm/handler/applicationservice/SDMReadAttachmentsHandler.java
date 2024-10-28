package com.sap.cds.sdm.handler.applicationservice;

import com.sap.cds.feature.attachments.handler.applicationservice.processor.readhelper.modifier.ItemModifierProvider;
import com.sap.cds.feature.attachments.service.AttachmentService;
import com.sap.cds.ql.CQL;
import com.sap.cds.ql.Predicate;
import com.sap.cds.ql.cqn.CqnSelect;
import com.sap.cds.ql.cqn.Modifier;
import com.sap.cds.reflect.CdsEntity;
import com.sap.cds.sdm.constants.SDMConstants;
import com.sap.cds.services.cds.ApplicationService;
import com.sap.cds.services.cds.CdsReadEventContext;
import com.sap.cds.services.handler.EventHandler;
import com.sap.cds.services.handler.annotations.Before;
import com.sap.cds.services.handler.annotations.HandlerOrder;
import com.sap.cds.services.handler.annotations.ServiceName;
import com.sap.cds.services.persistence.PersistenceService;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ServiceName(value = "*", type = ApplicationService.class)
public class SDMReadAttachmentsHandler implements EventHandler {
  private static final Logger logger = LoggerFactory.getLogger(SDMReadAttachmentsHandler.class);

  private final AttachmentService attachmentService;
  private final PersistenceService persistenceService;

  public SDMReadAttachmentsHandler(
      AttachmentService attachmentService,
      ItemModifierProvider provider,
      PersistenceService persistenceService) {
    this.attachmentService = attachmentService;
    this.persistenceService = persistenceService;
  }

  @Before
  @HandlerOrder(HandlerOrder.DEFAULT)
  public void processBefore(CdsReadEventContext context) {
    String repositoryId = SDMConstants.REPOSITORY_ID;
    if (context.getTarget().getQualifiedName().contains("attachments")) {
      Optional<CdsEntity> attachmentEntity =
          context.getModel().findEntity(context.getTarget().getQualifiedName());
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
