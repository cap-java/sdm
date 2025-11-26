package com.sap.cds.sdm.service.handler;

import com.sap.cds.feature.attachments.service.model.servicehandler.AttachmentCreateEventContext;
import com.sap.cds.sdm.service.RegisterService;
import com.sap.cds.services.EventContext;
import com.sap.cds.services.EventName;
import java.util.List;

/**
 * The {@link AttachmentMoveEventContext} is used to store the context of the move attachment event.
 * This interface provides methods to handle attachment moving for both regular entities and
 * projection entities by working with parent entities and their composition relationships.
 *
 * <p>For projection entities, the API uses the parent entity that defines the attachments
 * composition and the composition name to properly navigate the relationship hierarchy.
 */
@EventName(RegisterService.EVENT_MOVE_ATTACHMENT)
public interface AttachmentMoveEventContext extends AttachmentCreateEventContext {

  /**
   * Creates an {@link EventContext} already overlay with this interface. The event is set to be
   * {@link RegisterService#EVENT_MOVE_ATTACHMENT}
   *
   * @return the {@link AttachmentMoveEventContext}
   */
  static AttachmentMoveEventContext create() {
    return EventContext.create(AttachmentMoveEventContext.class, null);
  }

  /**
   * Gets the source folder ID in SDM from which attachments should be moved.
   *
   * @return The source folder ID or {@code null} if not specified
   */
  String getSourceFolderId();

  /**
   * Sets the source folder ID in SDM from which attachments should be moved.
   *
   * @param sourceFolderId The source folder ID in SDM
   */
  void setSourceFolderId(String sourceFolderId);

  /**
   * Gets the qualified name of the source parent entity from which attachments are being moved.
   * This is used to clean up the attachment metadata after successful moves.
   *
   * @return The qualified name of the source parent entity or {@code null} if not specified
   */
  String getSourceParentEntity();

  /**
   * Sets the qualified name of the source parent entity from which attachments are being moved.
   *
   * @param sourceParentEntity The qualified name of the source parent entity (e.g.,
   *     "Service.Entity")
   */
  void setSourceParentEntity(String sourceParentEntity);

  /**
   * Gets the name of the composition property in the source entity that links to the attachments.
   * This is used to clean up the attachment metadata after successful moves.
   *
   * @return The name of the source composition property or {@code null} if not specified
   */
  String getSourceCompositionName();

  /**
   * Sets the name of the composition property in the source entity that links to the attachments.
   *
   * @param sourceCompositionName The name of the source composition property
   */
  void setSourceCompositionName(String sourceCompositionName);

  /**
   * Gets the ID of the target parent entity instance for which attachments are being moved. This
   * represents the key values of the entity that contains the attachment composition.
   *
   * @return The id of the target parent entity instance or {@code null} if no id was specified
   */
  String getUpId();

  /**
   * Sets the ID of the target parent entity instance for which attachments are being moved. This
   * should be the key value of the entity that contains the attachment composition.
   *
   * @param upId The key of the target parent entity instance
   */
  void setUpId(String upId);

  /**
   * Gets the qualified name of the target parent entity that defines the attachments composition.
   * This is the entity that contains the composition relationship to the attachment entity.
   *
   * @return The qualified name of the target parent entity or {@code null} if not specified
   */
  String getParentEntity();

  /**
   * Sets the qualified name of the target parent entity that defines the attachments composition.
   * This entity should contain the composition relationship to the attachment entity.
   *
   * @param parentEntity The qualified name of the target parent entity (e.g., "Service.Entity")
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
   * Gets the list of object IDs representing the attachments to be moved. These are the IDs of
   * existing attachment records in the source folder.
   *
   * @return The list of attachment object IDs or {@code Collections.emptyList()} if no IDs were
   *     specified
   */
  List<String> getObjectIds();

  /**
   * Sets the list of object IDs representing the attachments to be moved. Each ID should correspond
   * to an existing attachment record in the source folder.
   *
   * @param ids The list of attachment object IDs to be moved
   */
  void setObjectIds(List<String> ids);

  /**
   * Gets whether the system user flow is being used for the move operation. System user flow
   * typically bypasses certain authorization checks and user-specific logic.
   *
   * @return {@code true} if the system user flow is used, {@code false} for regular user flow
   */
  Boolean getSystemUser();

  /**
   * Sets whether the system user flow should be used for the move operation. Use system user flow
   * when the operation should bypass user-specific authorization or logic.
   *
   * @param systemUser {@code true} to use system user flow, {@code false} for regular user flow
   */
  void setSystemUser(boolean systemUser);

  /**
   * Gets the list of object IDs for which the move operation failed. This is populated by the
   * handler after attempting to move all attachments.
   *
   * @return The list of failed object IDs or {@code Collections.emptyList()} if all moves succeeded
   */
  List<String> getFailedObjectIds();

  /**
   * Sets the list of object IDs for which the move operation failed. This should be set by the
   * handler after processing all move operations.
   *
   * @param failedObjectIds The list of object IDs that failed to move
   */
  void setFailedObjectIds(List<String> failedObjectIds);
}
