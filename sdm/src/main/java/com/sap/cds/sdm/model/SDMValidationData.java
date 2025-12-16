package com.sap.cds.sdm.model;

import com.sap.cds.reflect.CdsEntity;
import java.util.List;
import java.util.Map;

/** Helper class to hold SDM validation data. */
public class SDMValidationData {
  private final List<String> validSecondaryProperties;
  private final Map<String, String> entityAnnotations;
  private final CdsEntity targetEntity;

  public SDMValidationData(
      List<String> validSecondaryProperties,
      Map<String, String> entityAnnotations,
      CdsEntity targetEntity) {
    this.validSecondaryProperties = validSecondaryProperties;
    this.entityAnnotations = entityAnnotations;
    this.targetEntity = targetEntity;
  }

  public List<String> getValidSecondaryProperties() {
    return validSecondaryProperties;
  }

  public Map<String, String> getEntityAnnotations() {
    return entityAnnotations;
  }

  public CdsEntity getTargetEntity() {
    return targetEntity;
  }
}
