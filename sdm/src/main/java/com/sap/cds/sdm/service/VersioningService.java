package com.sap.cds.sdm.service;

import com.sap.cds.sdm.model.CmisDocument;
import com.sap.cds.sdm.model.SDMCredentials;
import org.json.JSONObject;

public interface VersioningService {
  public String checkInDocument(
      String repositoryId, SDMCredentials sdmCredentials, String jwtToken, String objectId);

  public String checkOutDocument(
      String repositoryId, SDMCredentials sdmCredentials, String jwtToken, String objectId);

  public JSONObject cancelCheckOut();

  public int setContentStream(
      SDMCredentials sdmCredentials, String jwtToken, CmisDocument cmisDocument);
}
