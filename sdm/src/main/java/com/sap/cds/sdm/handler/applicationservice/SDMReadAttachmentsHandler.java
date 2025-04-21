package com.sap.cds.sdm.handler.applicationservice;

import com.sap.cds.ql.CQL;
import com.sap.cds.ql.Predicate;
import com.sap.cds.ql.cqn.CqnSelect;
import com.sap.cds.ql.cqn.Modifier;
import com.sap.cds.reflect.CdsAssociationType;
import com.sap.cds.reflect.CdsElement;
import com.sap.cds.sdm.constants.SDMConstants;
import com.sap.cds.services.cds.ApplicationService;
import com.sap.cds.services.cds.CdsReadEventContext;
import com.sap.cds.services.handler.EventHandler;
import com.sap.cds.services.handler.annotations.Before;
import com.sap.cds.services.handler.annotations.HandlerOrder;
import com.sap.cds.services.handler.annotations.ServiceName;
import java.util.ArrayList;
import java.util.List;

@ServiceName(value = "*", type = ApplicationService.class)
public class SDMReadAttachmentsHandler implements EventHandler {

  public SDMReadAttachmentsHandler() {}

  @Before
  @HandlerOrder(HandlerOrder.DEFAULT)
  public void processBefore(CdsReadEventContext context) {
    String repositoryId = SDMConstants.REPOSITORY_ID;
    List<String> compositions = getEntityCompositions(context);
    for (String composition : compositions) {
      if (context.getTarget().getQualifiedName().contains(composition)) {
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

  private List<String> getEntityCompositions(CdsReadEventContext context) {
    List<CdsElement> compositions = context.getTarget().compositions().toList();
    List<String> attachmentsCompositionList = new ArrayList<>();
    for (CdsElement cdsElement : compositions) {
      if (cdsElement != null) {
        CdsAssociationType cdsAssociationType = cdsElement.getType();
        String targetAspect =
            cdsAssociationType.getTargetAspect().isPresent()
                ? cdsAssociationType.getTargetAspect().get().getQualifiedName()
                : null;
        if (targetAspect != null && targetAspect.equalsIgnoreCase("sap.attachments.Attachments")) {
          attachmentsCompositionList.add(cdsElement.getName());
        }
      }
    }
    return attachmentsCompositionList;
  }
}
