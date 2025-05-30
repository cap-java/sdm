package com.sap.cds.sdm.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.sap.cds.sdm.model.Repository;
import java.io.UnsupportedEncodingException;

public interface SDMAdminService {
  public String onboardRepository(Repository repository)
      throws JsonProcessingException, UnsupportedEncodingException;

  public String offboardRepository(String subdomain);
}
