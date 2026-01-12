package com.sap.cds.sdm.handler.applicationservice.helper;

import com.sap.cds.feature.attachments.generated.cds4j.sap.attachments.Attachments;
import com.sap.cds.feature.attachments.generated.cds4j.sap.attachments.MediaData;
import com.sap.cds.ql.CQL;
import com.sap.cds.ql.Expand;
import com.sap.cds.ql.cqn.CqnSelectListItem;
import com.sap.cds.ql.cqn.Modifier;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The class {@link SDMBeforeReadItemsModifier} is a modifier that adds the repository id filter and
 * ensures proper handling of expanded associations like statusNav/uploadStatusNav.
 */
public class SDMBeforeReadItemsModifier implements Modifier {

  private static final Logger logger = LoggerFactory.getLogger(SDMBeforeReadItemsModifier.class);

  private static final String ROOT_ASSOCIATION = "";
  private final List<String> mediaAssociations;

  public SDMBeforeReadItemsModifier(List<String> mediaAssociations) {
    this.mediaAssociations = mediaAssociations;
  }

  @Override
  public List<CqnSelectListItem> items(List<CqnSelectListItem> items) {
    List<CqnSelectListItem> newItems =
        new ArrayList<>(items.stream().filter(item -> !item.isExpand()).toList());
    List<CqnSelectListItem> result = addRequiredFields(items);
    newItems.addAll(result);

    return newItems;
  }

  private List<CqnSelectListItem> addRequiredFields(List<CqnSelectListItem> list) {
    List<CqnSelectListItem> newItems = new ArrayList<>();
    enhanceWithRequiredFieldsForMediaAssociation(ROOT_ASSOCIATION, list, newItems);

    List<CqnSelectListItem> expandedItems =
        list.stream().filter(CqnSelectListItem::isExpand).toList();
    newItems.addAll(processExpandedEntities(expandedItems));
    return newItems;
  }

  private List<CqnSelectListItem> processExpandedEntities(List<CqnSelectListItem> expandedItems) {
    List<CqnSelectListItem> newItems = new ArrayList<>();

    expandedItems.forEach(
        item -> {
          List<CqnSelectListItem> newItemsFromExpand =
              new ArrayList<>(item.asExpand().items().stream().filter(i -> !i.isExpand()).toList());
          enhanceWithRequiredFieldsForMediaAssociation(
              item.asExpand().displayName(), newItemsFromExpand, newItemsFromExpand);
          List<CqnSelectListItem> expandedSubItems =
              item.asExpand().items().stream().filter(CqnSelectListItem::isExpand).toList();
          List<CqnSelectListItem> result = processExpandedEntities(expandedSubItems);
          newItemsFromExpand.addAll(result);
          Expand<?> copy = CQL.copy(item.asExpand());
          copy.items(newItemsFromExpand);
          newItems.add(copy);
        });

    return newItems;
  }

  private void enhanceWithRequiredFieldsForMediaAssociation(
      String association, List<CqnSelectListItem> list, List<CqnSelectListItem> listToEnhance) {
    if (isMediaAssociationAndNeedRequiredFields(association, list)) {
      logger.debug(
          "Adding required fields (contentId, status, repositoryId, uploadStatus) to select items");
      if (list.stream().noneMatch(item -> isItemRefFieldWithName(item, Attachments.CONTENT_ID))) {
        listToEnhance.add(CQL.get(Attachments.CONTENT_ID));
      }
      if (list.stream().noneMatch(item -> isItemRefFieldWithName(item, Attachments.STATUS))) {
        listToEnhance.add(CQL.get(Attachments.STATUS));
      }
      if (list.stream().noneMatch(item -> isItemRefFieldWithName(item, "repositoryId"))) {
        listToEnhance.add(CQL.get("repositoryId"));
      }
      if (list.stream().noneMatch(item -> isItemRefFieldWithName(item, "uploadStatus"))) {
        listToEnhance.add(CQL.get("uploadStatus"));
      }
    }
  }

  private boolean isMediaAssociationAndNeedRequiredFields(
      String association, List<CqnSelectListItem> list) {
    // Only add fields for actual media associations, not the root entity (empty string)
    return !association.equals(ROOT_ASSOCIATION)
        && mediaAssociations.contains(association)
        && list.stream().anyMatch(item -> isItemRefFieldWithName(item, MediaData.CONTENT));
  }

  private boolean isItemRefFieldWithName(CqnSelectListItem item, String fieldName) {
    return item.isRef() && item.asRef().displayName().equals(fieldName);
  }
}
