package com.sap.cds.sdm.configuration;

import com.sap.cds.feature.attachments.handler.applicationservice.processor.modifyevents.CreateAttachmentEvent;
import com.sap.cds.feature.attachments.handler.applicationservice.processor.modifyevents.DefaultModifyAttachmentEventFactory;
import com.sap.cds.feature.attachments.handler.applicationservice.processor.modifyevents.DoNothingAttachmentEvent;
import com.sap.cds.feature.attachments.handler.applicationservice.processor.modifyevents.ModifyAttachmentEvent;
import com.sap.cds.feature.attachments.handler.applicationservice.processor.modifyevents.UpdateAttachmentEvent;
import com.sap.cds.feature.attachments.handler.applicationservice.processor.transaction.CreationChangeSetListener;
import com.sap.cds.feature.attachments.handler.applicationservice.processor.transaction.ListenerProvider;
import com.sap.cds.feature.attachments.handler.common.AttachmentsReader;
import com.sap.cds.feature.attachments.handler.common.DefaultAssociationCascader;
import com.sap.cds.feature.attachments.handler.common.DefaultAttachmentsReader;
import com.sap.cds.feature.attachments.service.AttachmentService;
import com.sap.cds.feature.attachments.utilities.LoggingMarker;
import com.sap.cds.sdm.caching.CacheConfig;
import com.sap.cds.sdm.service.SDMAttachmentsService;
import com.sap.cds.sdm.service.SDMService;
import com.sap.cds.sdm.service.SDMServiceImpl;
import com.sap.cds.sdm.service.handler.SDMAttachmentsServiceHandler;
import com.sap.cds.services.outbox.OutboxService;
import com.sap.cds.services.persistence.PersistenceService;
import com.sap.cds.services.runtime.CdsRuntimeConfiguration;
import com.sap.cds.services.runtime.CdsRuntimeConfigurer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.Marker;

/**
 * The class {@link Registration} is a configuration class that registers the services and event
 * handlers for the attachments feature.
 */
public class Registration implements CdsRuntimeConfiguration {

  private static final Logger logger = LoggerFactory.getLogger(Registration.class);
  private static final Marker marker = LoggingMarker.ATTACHMENT_SERVICE_REGISTRATION.getMarker();

  @Override
  public void services(CdsRuntimeConfigurer configurer) {
    configurer.service(buildAttachmentService());
  }

  @Override
  public void eventHandlers(CdsRuntimeConfigurer configurer) {
    logger.info(marker, "Registering event handler for attachment service");
    CacheConfig.initializeCache();
    var persistenceService =
        configurer
            .getCdsRuntime()
            .getServiceCatalog()
            .getService(PersistenceService.class, PersistenceService.DEFAULT_NAME);
    var attachmentService =
        configurer
            .getCdsRuntime()
            .getServiceCatalog()
            .getService(AttachmentService.class, AttachmentService.DEFAULT_NAME);
    var outbox =
        configurer
            .getCdsRuntime()
            .getServiceCatalog()
            .getService(
                OutboxService.class,
                OutboxService.PERSISTENT_UNORDERED_NAME); // need to check if required
    var outboxedAttachmentService = outbox.outboxed(attachmentService);
    System.out.println("Registration");
    SDMService sdmService = new SDMServiceImpl();
    configurer.eventHandler(new SDMAttachmentsServiceHandler(persistenceService, sdmService));
    //		var attachmentsReader = buildAttachmentsReader(persistenceService);
    //		var deleteContentEvent = new MarkAsDeletedAttachmentEvent(outboxedAttachmentService);
    //		var eventFactory = buildAttachmentEventFactory(attachmentService, deleteContentEvent,
    // outboxedAttachmentService);
    //		ThreadLocalDataStorage storage = new ThreadLocalDataStorage();
    //		configurer.eventHandler(buildCreateHandler(eventFactory, storage,persistenceService));
    //		configurer.eventHandler(buildUpdateHandler(eventFactory,attachmentsReader,
    // storage,persistenceService));
  }

  private AttachmentService buildAttachmentService() {
    logger.info(marker, "Registering SDM attachment service");
    return new SDMAttachmentsService();
  }

  protected DefaultModifyAttachmentEventFactory buildAttachmentEventFactory(
      AttachmentService attachmentService,
      ModifyAttachmentEvent deleteContentEvent,
      AttachmentService outboxedAttachmentService) {
    var creationChangeSetListener = createCreationFailedListener(outboxedAttachmentService);
    var createAttachmentEvent =
        new CreateAttachmentEvent(attachmentService, creationChangeSetListener);
    var updateAttachmentEvent =
        new UpdateAttachmentEvent(createAttachmentEvent, deleteContentEvent);

    var doNothingAttachmentEvent = new DoNothingAttachmentEvent();
    return new DefaultModifyAttachmentEventFactory(
        createAttachmentEvent, updateAttachmentEvent, deleteContentEvent, doNothingAttachmentEvent);
  }

  private ListenerProvider createCreationFailedListener(
      AttachmentService outboxedAttachmentService) {
    return (contentId, cdsRuntime) ->
        new CreationChangeSetListener(contentId, cdsRuntime, outboxedAttachmentService);
  }

  //	protected EventHandler buildCreateHandler(ModifyAttachmentEventFactory factory,
  // ThreadLocalDataStorage storage,PersistenceService persistenceService) {
  //		SDMService sdmService = new SDMServiceImpl();
  //		return new SDMCreateEventHandler(factory, storage, persistenceService, sdmService);
  //	}
  //
  //	protected EventHandler buildUpdateHandler(ModifyAttachmentEventFactory factory,
  // AttachmentsReader attachmentsReader,
  //											  ThreadLocalDataStorage storage,PersistenceService persistenceService) {
  //		SDMService sdmService = new SDMServiceImpl();
  //		return new SDMUpdateEventHandler(factory,attachmentsReader, storage, persistenceService,
  // sdmService);
  //	}
  protected AttachmentsReader buildAttachmentsReader(PersistenceService persistenceService) {
    var cascader = new DefaultAssociationCascader();
    return new DefaultAttachmentsReader(cascader, persistenceService);
  }
}
