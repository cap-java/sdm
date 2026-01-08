package com.sap.cds.sdm.caching;

import com.sap.cds.sdm.model.RepoValue;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.ehcache.Cache;
import org.ehcache.CacheManager;
import org.ehcache.config.builders.CacheConfigurationBuilder;
import org.ehcache.config.builders.CacheManagerBuilder;
import org.ehcache.config.builders.ResourcePoolsBuilder;
import org.ehcache.expiry.Duration;
import org.ehcache.expiry.Expirations;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CacheConfig {

  private static CacheManager cacheManager = CacheManagerBuilder.newCacheManagerBuilder().build();
  private static Cache<CacheKey, String> userTokenCache;
  private static Cache<CacheKey, String> clientCredentialsTokenCache;
  private static Cache<TokenCacheKey, String> userAuthoritiesTokenCache;
  private static Cache<RepoKey, RepoValue> repoCache;
  private static Cache<SecondaryTypesKey, List<String>> secondaryTypesCache;
  private static Cache<String, Long> maxAllowedAttachmentsCache;
  private static Cache<SecondaryPropertiesKey, List<String>> secondaryPropertiesCache;
  private static Cache<ErrorMessageKey, String> errorMessageCache;
  private static final int HEAP_SIZE = 1000;
  private static final int USER_TOKEN_EXPIRY = 660;
  private static final int ACCESS_TOKEN_EXPIRY = 660;
  private static final Logger logger = LoggerFactory.getLogger(CacheConfig.class);

  private CacheConfig() {
    throw new IllegalStateException("CacheConfig class");
  }

  public static void initializeCache() {
    // Expiring the cache after 11 hours
    logger.info("Cache for user token and access token initialized");

    try {
      cacheManager.init();
    } catch (IllegalStateException e) {
      // Cache manager already initialized
      logger.warn("Cache manager already initialized: {}", e.getMessage());
    }

    // Initialize caches with defensive error handling
    try {
      userTokenCache =
          cacheManager.createCache(
              "userToken",
              CacheConfigurationBuilder.newCacheConfigurationBuilder(
                      CacheKey.class, String.class, ResourcePoolsBuilder.heap(HEAP_SIZE))
                  .withExpiry(
                      Expirations.timeToLiveExpiration(
                          new Duration(USER_TOKEN_EXPIRY, TimeUnit.MINUTES))));
    } catch (Exception e) {
      logger.warn("userTokenCache already exists or failed to create: {}", e.getMessage());
      // Try to get existing cache
      try {
        userTokenCache = cacheManager.getCache("userToken", CacheKey.class, String.class);
      } catch (Exception ex) {
        logger.error("Failed to retrieve existing userTokenCache: {}", ex.getMessage());
      }
    }

    try {
      clientCredentialsTokenCache =
          cacheManager.createCache(
              "clientCredentialsToken",
              CacheConfigurationBuilder.newCacheConfigurationBuilder(
                      CacheKey.class, String.class, ResourcePoolsBuilder.heap(HEAP_SIZE))
                  .withExpiry(
                      Expirations.timeToLiveExpiration(
                          new Duration(ACCESS_TOKEN_EXPIRY, TimeUnit.MINUTES))));
    } catch (Exception e) {
      logger.warn(
          "clientCredentialsTokenCache already exists or failed to create: {}", e.getMessage());
      try {
        clientCredentialsTokenCache =
            cacheManager.getCache("clientCredentialsToken", CacheKey.class, String.class);
      } catch (Exception ex) {
        logger.error(
            "Failed to retrieve existing clientCredentialsTokenCache: {}", ex.getMessage());
      }
    }

    try {
      repoCache =
          cacheManager.createCache(
              "versionedRepo",
              CacheConfigurationBuilder.newCacheConfigurationBuilder(
                      RepoKey.class, RepoValue.class, ResourcePoolsBuilder.heap(HEAP_SIZE))
                  .withExpiry(
                      Expirations.timeToLiveExpiration(
                          new Duration(ACCESS_TOKEN_EXPIRY, TimeUnit.MINUTES))));
    } catch (Exception e) {
      logger.warn("repoCache already exists or failed to create: {}", e.getMessage());
      try {
        repoCache = cacheManager.getCache("versionedRepo", RepoKey.class, RepoValue.class);
      } catch (Exception ex) {
        logger.error("Failed to retrieve existing repoCache: {}", ex.getMessage());
      }
    }

    try {
      userAuthoritiesTokenCache =
          cacheManager.createCache(
              "userAuthoritiesToken",
              CacheConfigurationBuilder.newCacheConfigurationBuilder(
                      TokenCacheKey.class, String.class, ResourcePoolsBuilder.heap(HEAP_SIZE))
                  .withExpiry(
                      Expirations.timeToLiveExpiration(
                          new Duration(USER_TOKEN_EXPIRY, TimeUnit.MINUTES))));
    } catch (Exception e) {
      logger.warn(
          "userAuthoritiesTokenCache already exists or failed to create: {}", e.getMessage());
      try {
        userAuthoritiesTokenCache =
            cacheManager.getCache("userAuthoritiesToken", TokenCacheKey.class, String.class);
      } catch (Exception ex) {
        logger.error("Failed to retrieve existing userAuthoritiesTokenCache: {}", ex.getMessage());
      }
    }

    try {
      secondaryTypesCache =
          cacheManager.createCache(
              "secondaryTypes",
              CacheConfigurationBuilder.newCacheConfigurationBuilder(
                      SecondaryTypesKey.class,
                      (Class<List<String>>) (Class<?>) List.class,
                      ResourcePoolsBuilder.heap(HEAP_SIZE))
                  .withExpiry(Expirations.noExpiration()));
    } catch (Exception e) {
      logger.warn("secondaryTypesCache already exists or failed to create: {}", e.getMessage());
      try {
        secondaryTypesCache =
            cacheManager.getCache(
                "secondaryTypes",
                SecondaryTypesKey.class,
                (Class<List<String>>) (Class<?>) List.class);
      } catch (Exception ex) {
        logger.error("Failed to retrieve existing secondaryTypesCache: {}", ex.getMessage());
      }
    }

    try {
      maxAllowedAttachmentsCache =
          cacheManager.createCache(
              "maxAllowedAttachmentsCache",
              CacheConfigurationBuilder.newCacheConfigurationBuilder(
                      String.class, Long.class, ResourcePoolsBuilder.heap(HEAP_SIZE))
                  .withExpiry(Expirations.noExpiration()));
    } catch (Exception e) {
      logger.warn(
          "maxAllowedAttachmentsCache already exists or failed to create: {}", e.getMessage());
      try {
        maxAllowedAttachmentsCache =
            cacheManager.getCache("maxAllowedAttachmentsCache", String.class, Long.class);
      } catch (Exception ex) {
        logger.error("Failed to retrieve existing maxAllowedAttachmentsCache: {}", ex.getMessage());
      }
    }

    try {
      secondaryPropertiesCache =
          cacheManager.createCache(
              "secondaryProperties",
              CacheConfigurationBuilder.newCacheConfigurationBuilder(
                      SecondaryPropertiesKey.class,
                      (Class<List<String>>) (Class<?>) List.class,
                      ResourcePoolsBuilder.heap(HEAP_SIZE))
                  .withExpiry(Expirations.noExpiration()));
    } catch (Exception e) {
      logger.warn(
          "secondaryPropertiesCache already exists or failed to create: {}", e.getMessage());
      try {
        secondaryPropertiesCache =
            cacheManager.getCache(
                "secondaryProperties",
                SecondaryPropertiesKey.class,
                (Class<List<String>>) (Class<?>) List.class);
      } catch (Exception ex) {
        logger.error("Failed to retrieve existing secondaryPropertiesCache: {}", ex.getMessage());
      }
    }

    try {
      errorMessageCache =
          cacheManager.createCache(
              "errorMessages",
              CacheConfigurationBuilder.newCacheConfigurationBuilder(
                      ErrorMessageKey.class,
                      (Class<String>) (Class<?>) String.class,
                      ResourcePoolsBuilder.heap(HEAP_SIZE))
                  .withExpiry(Expirations.noExpiration()));
    } catch (Exception e) {
      logger.warn("errorMessageCache already exists or failed to create: {}", e.getMessage());
      try {
        errorMessageCache =
            cacheManager.getCache(
                "errorMessages", ErrorMessageKey.class, (Class<String>) (Class<?>) String.class);
      } catch (Exception ex) {
        logger.error("Failed to retrieve existing errorMessageCache: {}", ex.getMessage());
      }
    }
  }

  public static Cache<CacheKey, String> getUserTokenCache() {
    return userTokenCache;
  }

  public static Cache<TokenCacheKey, String> getUserAuthoritiesTokenCache() {
    return userAuthoritiesTokenCache;
  }

  public static Cache<CacheKey, String> getClientCredentialsTokenCache() {
    return clientCredentialsTokenCache;
  }

  public static Cache<RepoKey, RepoValue> getRepoCache() {
    return repoCache;
  }

  public static Cache<String, Long> getMaxAllowedAttachmentsCache() {
    return maxAllowedAttachmentsCache;
  }

  public static Cache<SecondaryTypesKey, List<String>> getSecondaryTypesCache() {
    return secondaryTypesCache;
  }

  public static Cache<SecondaryPropertiesKey, List<String>> getSecondaryPropertiesCache() {
    return secondaryPropertiesCache;
  }

  public static Cache<ErrorMessageKey, String> getErrorMessageCache() {
    return errorMessageCache;
  }
}
