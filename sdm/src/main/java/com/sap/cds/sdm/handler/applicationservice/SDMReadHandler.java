package com.sap.cds.sdm.handler.applicationservice;

import com.sap.cds.CdsData;
import com.sap.cds.CdsDataProcessor;
import com.sap.cds.CdsDataProcessor.Converter;
import com.sap.cds.feature.attachments.generated.cds4j.sap.attachments.Attachments;
import com.sap.cds.feature.attachments.handler.common.ApplicationHandlerHelper;
import com.sap.cds.feature.attachments.service.AttachmentService;
import com.sap.cds.reflect.CdsAssociationType;
import com.sap.cds.reflect.CdsElementDefinition;
import com.sap.cds.reflect.CdsEntity;
import com.sap.cds.reflect.CdsModel;
import com.sap.cds.services.cds.ApplicationService;
import com.sap.cds.services.cds.CdsReadEventContext;
import com.sap.cds.services.draft.Drafts;
import com.sap.cds.services.handler.EventHandler;
import com.sap.cds.services.handler.annotations.After;
import com.sap.cds.services.handler.annotations.HandlerOrder;
import com.sap.cds.services.handler.annotations.ServiceName;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ServiceName(value = "*", type = ApplicationService.class)
public class SDMReadHandler implements EventHandler {

  private static final Logger logger = LoggerFactory.getLogger(SDMReadHandler.class);

  private final AttachmentService attachmentService;

  public SDMReadHandler(AttachmentService attachmentService) {
    this.attachmentService = attachmentService;
  }

  @After
  @HandlerOrder(HandlerOrder.EARLY)
  public void processAfter(CdsReadEventContext context, List<CdsData> data) {
    if (ApplicationHandlerHelper.noContentFieldInData(context.getTarget(), data)) {
      return;
    }
    logger.debug("Processing after read event for entity {}", context.getTarget().getName());

    Converter converter =
        (path, element, value) -> {
          logger.info("Processing after read event for entity SDM {}", element.getName());
          var contentId = (String) path.target().values().get(Attachments.CONTENT_ID);
          var status = (String) path.target().values().get(Attachments.STATUS);
          var content = (InputStream) path.target().values().get(Attachments.CONTENT);
          var contentExists = Objects.nonNull(content);
          if (Objects.nonNull(contentId) || contentExists) {
            Supplier<InputStream> supplier =
                Objects.nonNull(content)
                    ? () -> content
                    : () -> attachmentService.readAttachment(contentId);
            return supplier.get();
          } else {
            return value;
          }
        };

    CdsDataProcessor.create()
        .addConverter(ApplicationHandlerHelper.MEDIA_CONTENT_FILTER, converter)
        .process(data, context.getTarget());
  }

  private List<String> getAttachmentAssociations(
      CdsModel model, CdsEntity entity, String associationName, List<String> processedEntities) {
    var associationNames = new ArrayList<String>();
    if (ApplicationHandlerHelper.isMediaEntity(entity)) {
      associationNames.add(associationName);
    }

    Map<String, CdsEntity> annotatedEntitiesMap =
        entity
            .associations()
            .collect(
                Collectors.toMap(
                    CdsElementDefinition::getName,
                    element -> element.getType().as(CdsAssociationType.class).getTarget()));

    if (annotatedEntitiesMap.isEmpty()) {
      return associationNames;
    }

    for (var associatedElement : annotatedEntitiesMap.entrySet()) {
      if (!associationNames.contains(associatedElement.getKey())
          && !processedEntities.contains(associatedElement.getKey())
          && !Drafts.SIBLING_ENTITY.equals(associatedElement.getKey())) {
        processedEntities.add(associatedElement.getKey());
        var result =
            getAttachmentAssociations(
                model, associatedElement.getValue(), associatedElement.getKey(), processedEntities);
        associationNames.addAll(result);
      }
    }
    return associationNames;
  }
}
