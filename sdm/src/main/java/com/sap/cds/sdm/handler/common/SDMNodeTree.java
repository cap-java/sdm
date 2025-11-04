package com.sap.cds.sdm.handler.common;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The class {@link SDMNodeTree} is a tree data structure that holds the SDM association identifier
 * and its children for attachment processing.
 */
class SDMNodeTree {

  private final SDMAssociationIdentifier identifier;
  private final List<SDMNodeTree> children = new ArrayList<>();

  SDMNodeTree(SDMAssociationIdentifier identifier) {
    this.identifier = identifier;
  }

  void addPath(List<SDMAssociationIdentifier> path) {
    var currentIdentifierOptional =
        path.stream()
            .filter(entry -> entry.fullEntityName().equals(identifier.fullEntityName()))
            .findAny();
    if (currentIdentifierOptional.isEmpty()) {
      return;
    }
    var currentNode = this;
    var index = path.indexOf(currentIdentifierOptional.get());
    if (index == path.size() - 1) {
      return;
    }
    for (var i = index + 1; i < path.size(); i++) {
      var pathEntry = path.get(i);
      currentNode = currentNode.getChildOrNew(pathEntry);
    }
  }

  private SDMNodeTree getChildOrNew(SDMAssociationIdentifier identifier) {
    var childOptional =
        children.stream()
            .filter(child -> child.identifier.fullEntityName().equals(identifier.fullEntityName()))
            .findAny();
    if (childOptional.isPresent()) {
      return childOptional.get();
    } else {
      SDMNodeTree child = new SDMNodeTree(identifier);
      children.add(child);
      return child;
    }
  }

  SDMAssociationIdentifier getIdentifier() {
    return identifier;
  }

  List<SDMNodeTree> getChildren() {
    return Collections.unmodifiableList(children);
  }

  @Override
  public String toString() {
    return "SDMNodeTree{" + "identifier=" + identifier + ", children=" + children + '}';
  }
}
