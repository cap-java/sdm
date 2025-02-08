package com.sap.cds.sdm.caching;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CacheKey {
  private String key;
  private String expiration;
}
