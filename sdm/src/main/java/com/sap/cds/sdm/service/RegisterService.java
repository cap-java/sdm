package com.sap.cds.sdm.service;

import com.sap.cds.sdm.model.CopyAttachmentInput;
import com.sap.cds.sdm.model.MoveAttachmentInput;
import com.sap.cds.services.Service;
import java.util.List;

public interface RegisterService extends Service {
  String SDM_NAME = "SDMAttachmentService$Default";
  String EVENT_COPY_ATTACHMENT = "COPY_ATTACHMENT";
  String EVENT_MOVE_ATTACHMENT = "MOVE_ATTACHMENT";

  /**
   * Copies attachments using the facet-based approach. This method supports both regular entities
   * and projection entities by internally parsing the facet to determine parent entity and
   * composition name.
   *
   * @param input The copy attachment input containing facet and object IDs
   * @param isSystemUser Whether to use system user flow
   */
  public void copyAttachments(CopyAttachmentInput input, boolean isSystemUser);

  /**
   * Moves attachments from a source folder to a target entity using the facet-based approach. This
   * method supports both regular entities and projection entities by internally parsing the facet
   * to determine parent entity and composition name.
   *
   * @param input The move attachment input containing source folder ID, target facet, and object
   *     IDs
   * @param isSystemUser Whether to use system user flow
   * @return A list of object IDs for which the move operation failed (empty if all succeeded)
   */
  public List<String> moveAttachments(MoveAttachmentInput input, boolean isSystemUser);
}
