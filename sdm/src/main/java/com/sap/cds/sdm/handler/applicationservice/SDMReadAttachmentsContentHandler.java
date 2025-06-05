package com.sap.cds.sdm.handler.applicationservice;

import com.sap.cds.CdsData;
import com.sap.cds.CdsDataProcessor.Converter;
import com.sap.cds.feature.attachments.generated.cds4j.sap.attachments.Attachments;
import com.sap.cds.feature.attachments.handler.applicationservice.processor.readhelper.stream.LazyProxyInputStream;
import com.sap.cds.feature.attachments.handler.applicationservice.processor.readhelper.validator.AttachmentStatusValidator;
import com.sap.cds.feature.attachments.handler.common.ApplicationHandlerHelper;
import com.sap.cds.sdm.service.SDMAttachmentsService;
import com.sap.cds.services.cds.ApplicationService;
import com.sap.cds.services.cds.CdsReadEventContext;
import com.sap.cds.services.handler.EventHandler;
import com.sap.cds.services.handler.annotations.After;
import com.sap.cds.services.handler.annotations.HandlerOrder;
import com.sap.cds.services.handler.annotations.ServiceName;
import java.io.InputStream;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The class {@link SDMReadAttachmentsContentHandler} is an event handler that is responsible for
 * reading attachments for entities. In the before read event, it modifies the CQN to include the
 * content ID and status. In the after read event, it adds a proxy for the stream of the attachments
 * service to the data. Only if the data are read the proxy forwards the request to the attachment
 * service to read the attachment. This is needed to have a filled stream in the data to enable the
 * OData V4 adapter to enrich the data that a link to the content can be shown on the UI.
 */
@ServiceName(value = "*", type = ApplicationService.class)
public class SDMReadAttachmentsContentHandler implements EventHandler {

  private static final Logger logger =
      LoggerFactory.getLogger(SDMReadAttachmentsContentHandler.class);

  private final SDMAttachmentsService attachmentService;
  private final AttachmentStatusValidator attachmentStatusValidator;

  public SDMReadAttachmentsContentHandler(
      SDMAttachmentsService attachmentService,
      AttachmentStatusValidator attachmentStatusValidator) {
    this.attachmentService = attachmentService;
    this.attachmentStatusValidator = attachmentStatusValidator;
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
          logger.info("Processing after read event for entity {}", element.getName());
          var contentId = (String) path.target().values().get(Attachments.CONTENT_ID);
          var status = (String) path.target().values().get(Attachments.STATUS);
          var content = (InputStream) path.target().values().get(Attachments.CONTENT);
          var contentExists = Objects.nonNull(content);
          if (Objects.nonNull(contentId) || contentExists) {
            System.out.println("CONTENT ID in handler SDM" + contentId);
            String[] contentParts = contentId.split(":");
            System.out.println("contentParts" + contentParts.length);
            if (contentParts.length > 4) {
              return null;
            }
            Supplier<InputStream> supplier =
                Objects.nonNull(content)
                    ? () -> content
                    : () -> attachmentService.readAttachment(contentId);
            return new LazyProxyInputStream(supplier, attachmentStatusValidator, status);
          } else {
            return value;
          }
        };

    ApplicationHandlerHelper.callProcessor(
        context.getTarget(), data, ApplicationHandlerHelper.MEDIA_CONTENT_FILTER, converter);
  }
}
