package com.sap.cds.sdm.handler.common;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The class {@link SDMNodeTree} is a tree data structure that holds the SDM association identifier
 * and its children for attachment processing.
 */
class SDMNodeTree {

  private static final Logger logger = LoggerFactory.getLogger(SDMNodeTree.class);

  private final SDMAssociationIdentifier identifier;
  private final List<SDMNodeTree> children = new ArrayList<>();

  SDMNodeTree(SDMAssociationIdentifier identifier) {
    this.identifier = identifier;
  }

  void addPath(List<SDMAssociationIdentifier> path) {
    logger.debug("Adding path with {} identifiers to node: {}", path.size(), identifier);
    var currentIdentifierOptional =
        path.stream()
            .filter(entry -> entry.fullEntityName().equals(identifier.fullEntityName()))
            .findAny();
    if (currentIdentifierOptional.isEmpty()) {
      logger.debug("Current identifier not found in path, skipping");
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
      logger.debug("Found existing child node: {}", identifier.fullEntityName());
      return childOptional.get();
    } else {
      logger.debug("Creating new child node: {}", identifier.fullEntityName());
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
