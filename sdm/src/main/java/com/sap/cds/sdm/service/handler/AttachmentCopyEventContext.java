/**************************************************************************
 * (C) 2019-2025 SAP SE or an SAP affiliate company. All rights reserved. *
 **************************************************************************/
package com.sap.cds.sdm.service.handler;

import com.sap.cds.feature.attachments.service.model.servicehandler.AttachmentCreateEventContext;
import com.sap.cds.sdm.service.RegisterService;
import com.sap.cds.services.EventContext;
import com.sap.cds.services.EventName;
import java.util.List;

/**
 * The {@link AttachmentCopyEventContext} is used to store the context of the create attachment
 * event.
 */
@EventName(RegisterService.EVENT_COPY_ATTACHMENT)
public interface AttachmentCopyEventContext extends AttachmentCreateEventContext {

  /**
   * Creates an {@link EventContext} already overlay with this interface. The event is set to be
   * {@link RegisterService#EVENT_COPY_ATTACHMENT}
   *
   * @return the {@link AttachmentCopyEventContext}
   */
  static AttachmentCopyEventContext create() {
    return EventContext.create(AttachmentCopyEventContext.class, null);
  }

  /**
   * @return The id of the attachment storage entity or {@code null} if no id was specified
   */
  String getUpId();

  /**
   * Sets the ID of the content for the attachment storage
   *
   * @param upId The key of the content
   */
  void setUpId(String upId);

  String getFacet();

  /**
   * Sets the ID of the content for the attachment storage
   *
   * @param facet The key of the content
   */
  void setFacet(String facet);

  /**
   * @return The IDs of the attachment storage entity or {@code Collections.emptyMap} if no id was
   *     specified
   */
  List<String> getObjectIds();

  /**
   * Sets the id af the attachment entity for the attachment storage
   *
   * @param ids The key of the attachment entity which defines the content field
   */
  void setObjectIds(List<String> ids);

  /**
   * @return {@code true} if the user flow is used, {@code false} otherwise
   */
  Boolean getSystemUser();

  /**
   * Sets whether the system user flow is used.
   *
   * @param systemUser {@code true} if the system user flow is used, {@code false} otherwise
   */
  void setSystemUser(boolean systemUser);
}
