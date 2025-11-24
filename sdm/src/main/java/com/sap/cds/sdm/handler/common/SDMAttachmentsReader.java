package com.sap.cds.sdm.handler.common;

import static java.util.Objects.requireNonNull;

import com.sap.cds.Result;
import com.sap.cds.feature.attachments.generated.cds4j.sap.attachments.Attachments;
import com.sap.cds.ql.CQL;
import com.sap.cds.ql.Expand;
import com.sap.cds.ql.Select;
import com.sap.cds.ql.StructuredType;
import com.sap.cds.ql.cqn.CqnFilterableStatement;
import com.sap.cds.reflect.CdsEntity;
import com.sap.cds.reflect.CdsModel;
import com.sap.cds.services.persistence.PersistenceService;
import java.util.ArrayList;
import java.util.List;

/**
 * The class {@link SDMAttachmentsReader} is used to deep read attachments from the database for a
 * determined path from the given entity to the media entity. The class uses the {@link
 * SDMAssociationCascader} to find the entity path.
 *
 * <p>The returned data is deep including the path structure to the media entity.
 */
public class SDMAttachmentsReader {

  private final SDMAssociationCascader cascader;
  private final PersistenceService persistence;

  public SDMAttachmentsReader(SDMAssociationCascader cascader, PersistenceService persistence) {
    this.cascader = requireNonNull(cascader, "cascader must not be null");
    this.persistence = requireNonNull(persistence, "persistence must not be null");
  }

  public List<Attachments> readAttachments(
      CdsModel model, CdsEntity entity, CqnFilterableStatement statement) {

    SDMNodeTree nodePath = cascader.findEntityPath(model, entity);
    List<Expand<?>> expandList = buildExpandList(nodePath);

    Select<?> select;
    if (!expandList.isEmpty()) {
      select = Select.from(statement.ref()).columns(expandList);
    } else {
      select = Select.from(statement.ref()).columns(StructuredType::_all);
    }

    if (statement.where().isPresent()) {
      select.where(statement.where().get());
    }

    Result result = persistence.run(select);
    return result.listOf(Attachments.class);
  }

  public List<String> getAttachmentEntityPaths(CdsModel model, CdsEntity entity) {
    SDMNodeTree nodePath = cascader.findEntityPath(model, entity);

    List<String> attachmentPaths = new ArrayList<>();

    if (nodePath != null) {
      collectAttachmentPaths(nodePath, attachmentPaths, model);
    }
    return attachmentPaths;
  }

  private void collectAttachmentPaths(
      SDMNodeTree node, List<String> attachmentPaths, CdsModel model) {
    String entityName = node.getIdentifier().fullEntityName();

    // Check if this entity is an attachment entity
    if (isAttachmentEntity(model, entityName)) {
      attachmentPaths.add(entityName);
    }

    // Recursively check children
    for (SDMNodeTree child : node.getChildren()) {
      collectAttachmentPaths(child, attachmentPaths, model);
    }
  }

  private boolean isAttachmentEntity(CdsModel model, String entityName) {
    var entityOpt = model.findEntity(entityName);
    if (entityOpt.isEmpty()) {
      return false;
    }

    CdsEntity entity = entityOpt.get();
    // Check if this entity has the @_is_media_data annotation (indicating attachment entity)
    return entity.getAnnotationValue("_is_media_data", false);
  }

  private List<Expand<?>> buildExpandList(SDMNodeTree root) {
    List<Expand<?>> expandResultList = new ArrayList<>();
    root.getChildren()
        .forEach(
            child -> {
              Expand<?> expand = buildExpandFromTree(child);
              expandResultList.add(expand);
            });

    return expandResultList;
  }

  private Expand<?> buildExpandFromTree(SDMNodeTree node) {
    if (node.getChildren().isEmpty()) {
      return CQL.to(node.getIdentifier().associationName()).expand();
    } else {
      return CQL.to(node.getIdentifier().associationName())
          .expand(node.getChildren().stream().map(this::buildExpandFromTree).toList());
    }
  }
}
