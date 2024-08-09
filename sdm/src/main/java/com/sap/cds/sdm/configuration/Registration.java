
package com.sap.cds.sdm.configuration;


import com.sap.cds.sdm.caching.CacheConfig;
import com.sap.cds.sdm.handler.applicationservice.SDMCreateEventHandler;
import com.sap.cds.sdm.service.SDMAttachmentsService;
import com.sap.cds.sdm.service.handler.SDMAttachmentsServiceHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.Marker;

import com.sap.cds.feature.attachments.handler.applicationservice.helper.ThreadLocalDataStorage;
import com.sap.cds.feature.attachments.handler.applicationservice.processor.modifyevents.CreateAttachmentEvent;
import com.sap.cds.feature.attachments.handler.applicationservice.processor.modifyevents.DefaultModifyAttachmentEventFactory;
import com.sap.cds.feature.attachments.handler.applicationservice.processor.modifyevents.DoNothingAttachmentEvent;
import com.sap.cds.feature.attachments.handler.applicationservice.processor.modifyevents.MarkAsDeletedAttachmentEvent;
import com.sap.cds.feature.attachments.handler.applicationservice.processor.modifyevents.ModifyAttachmentEvent;
import com.sap.cds.feature.attachments.handler.applicationservice.processor.modifyevents.ModifyAttachmentEventFactory;
import com.sap.cds.feature.attachments.handler.applicationservice.processor.modifyevents.UpdateAttachmentEvent;
import com.sap.cds.feature.attachments.handler.applicationservice.processor.transaction.CreationChangeSetListener;
import com.sap.cds.feature.attachments.handler.applicationservice.processor.transaction.ListenerProvider;

import com.sap.cds.feature.attachments.service.AttachmentService;
import com.sap.cds.feature.attachments.utilities.LoggingMarker;
import com.sap.cds.services.handler.EventHandler;
import com.sap.cds.services.outbox.OutboxService;
import com.sap.cds.services.runtime.CdsRuntimeConfiguration;
import com.sap.cds.services.runtime.CdsRuntimeConfigurer;


/**
 * The class {@link Registration} is a configuration class that registers the
 * services and event handlers for the attachments feature.
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
		var attachmentService = configurer.getCdsRuntime().getServiceCatalog().getService(AttachmentService.class,
				AttachmentService.DEFAULT_NAME);
		var outbox = configurer.getCdsRuntime().getServiceCatalog().getService(OutboxService.class,
				OutboxService.PERSISTENT_UNORDERED_NAME);//need to check if required
		var outboxedAttachmentService = outbox.outboxed(attachmentService);

		configurer.eventHandler(new SDMAttachmentsServiceHandler());

		var deleteContentEvent = new MarkAsDeletedAttachmentEvent(outboxedAttachmentService);
		var eventFactory = buildAttachmentEventFactory(attachmentService, deleteContentEvent, outboxedAttachmentService);
		ThreadLocalDataStorage storage = new ThreadLocalDataStorage();

		configurer.eventHandler(buildCreateHandler(eventFactory, storage));
		configurer.eventHandler(buildUpdateHandler(eventFactory, attachmentsReader, outboxedAttachmentService, storage));

	}



	private AttachmentService buildAttachmentService() {
		logger.info(marker, "Registering SDM attachment service");

		return new SDMAttachmentsService();
	}

	protected DefaultModifyAttachmentEventFactory buildAttachmentEventFactory(AttachmentService attachmentService,
																			  ModifyAttachmentEvent deleteContentEvent, AttachmentService outboxedAttachmentService) {
		var creationChangeSetListener = createCreationFailedListener(outboxedAttachmentService);
		var createAttachmentEvent = new CreateAttachmentEvent(attachmentService, creationChangeSetListener);
		var updateAttachmentEvent = new UpdateAttachmentEvent(createAttachmentEvent, deleteContentEvent);

		var doNothingAttachmentEvent = new DoNothingAttachmentEvent();
		return new DefaultModifyAttachmentEventFactory(createAttachmentEvent, updateAttachmentEvent, deleteContentEvent,
				doNothingAttachmentEvent);
	}

	private ListenerProvider createCreationFailedListener(AttachmentService outboxedAttachmentService) {
		return (contentId, cdsRuntime) -> new CreationChangeSetListener(contentId, cdsRuntime, outboxedAttachmentService);
	}

	protected EventHandler buildCreateHandler(ModifyAttachmentEventFactory factory, ThreadLocalDataStorage storage) {
		return new SDMCreateEventHandler(factory, storage);
	}



}
