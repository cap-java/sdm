package com.sap.cds.sdm.service.handler;

import com.sap.cds.feature.attachments.service.model.servicehandler.AttachmentCreateEventContext;
import com.sap.cds.sdm.service.RegisterService;
import com.sap.cds.services.EventContext;
import com.sap.cds.services.EventName;
import java.util.List;

/**
 * The {@link AttachmentCopyEventContext} is used to store the context of the copy attachment event.
 * This interface provides methods to handle attachment copying for both regular entities and
 * projection entities by working with parent entities and their composition relationships.
 *
 * <p>For projection entities, the API uses the parent entity that defines the attachments
 * composition and the composition name to properly navigate the relationship hierarchy.
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
   * Gets the ID of the parent entity instance for which attachments are being copied. This
   * represents the key values of the entity that contains the attachment composition.
   *
   * @return The id of the parent entity instance or {@code null} if no id was specified
   */
  String getUpId();

  /**
   * Sets the ID of the parent entity instance for which attachments are being copied. This should
   * be the key value of the entity that contains the attachment composition.
   *
   * @param upId The key of the parent entity instance
   */
  void setUpId(String upId);

  /**
   * Gets the qualified name of the parent entity that defines the attachments composition. This is
   * the entity that contains the composition relationship to the attachment entity.
   *
   * @return The qualified name of the parent entity or {@code null} if not specified
   */
  String getParentEntity();

  /**
   * Sets the qualified name of the parent entity that defines the attachments composition. This
   * entity should contain the composition relationship to the attachment entity.
   *
   * @param parentEntity The qualified name of the parent entity (e.g., "Service.Entity")
   */
  void setParentEntity(String parentEntity);

  /**
   * Gets the name of the composition property that links the parent entity to the attachment
   * entity. This is the property name used in the composition relationship.
   *
   * <p>Examples: "attachments", "references"
   *
   * @return The name of the composition property or {@code null} if not specified
   */
  String getCompositionName();

  /**
   * Sets the name of the composition property that links the parent entity to the attachment
   * entity. This should match the property name defined in the CDS model.
   *
   * @param compositionName The name of the composition property (e.g., "references")
   */
  void setCompositionName(String compositionName);

  /**
   * Gets the list of object IDs representing the attachments to be copied. These are typically the
   * IDs of existing attachment records that should be duplicated.
   *
   * @return The list of attachment object IDs or {@code Collections.emptyList()} if no IDs were
   *     specified
   */
  List<String> getObjectIds();

  /**
   * Sets the list of object IDs representing the attachments to be copied. Each ID should
   * correspond to an existing attachment record.
   *
   * @param ids The list of attachment object IDs to be copied
   */
  void setObjectIds(List<String> ids);

  /**
   * Gets whether the system user flow is being used for the copy operation. System user flow
   * typically bypasses certain authorization checks and user-specific logic.
   *
   * @return {@code true} if the system user flow is used, {@code false} for regular user flow
   */
  Boolean getSystemUser();

  /**
   * Sets whether the system user flow should be used for the copy operation. Use system user flow
   * when the operation should bypass user-specific authorization or logic.
   *
   * @param systemUser {@code true} to use system user flow, {@code false} for regular user flow
   */
  void setSystemUser(boolean systemUser);
}
