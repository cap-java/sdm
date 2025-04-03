package com.sap.cds.sdm.caching;

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
  private static Cache<RepoKey, String> versionedRepoCache;
  private static Cache<SecondaryTypesKey, List<String>> secondaryTypesCache;
  private static Cache<String, String> maxAllowedAttachmentsCache;
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
    cacheManager.init();

    userTokenCache =
        cacheManager.createCache(
            "userToken",
            CacheConfigurationBuilder.newCacheConfigurationBuilder(
                    CacheKey.class, String.class, ResourcePoolsBuilder.heap(HEAP_SIZE))
                .withExpiry(
                    Expirations.timeToLiveExpiration(
                        new Duration(USER_TOKEN_EXPIRY, TimeUnit.MINUTES))));
    clientCredentialsTokenCache =
        cacheManager.createCache(
            "clientCredentialsToken",
            CacheConfigurationBuilder.newCacheConfigurationBuilder(
                    CacheKey.class, String.class, ResourcePoolsBuilder.heap(HEAP_SIZE))
                .withExpiry(
                    Expirations.timeToLiveExpiration(
                        new Duration(ACCESS_TOKEN_EXPIRY, TimeUnit.MINUTES))));
    versionedRepoCache =
        cacheManager.createCache(
            "versionedRepo",
            CacheConfigurationBuilder.newCacheConfigurationBuilder(
                    RepoKey.class, String.class, ResourcePoolsBuilder.heap(HEAP_SIZE))
                .withExpiry(
                    Expirations.timeToLiveExpiration(
                        new Duration(ACCESS_TOKEN_EXPIRY, TimeUnit.MINUTES))));

    userAuthoritiesTokenCache =
        cacheManager.createCache(
            "userAuthoritiesToken",
            CacheConfigurationBuilder.newCacheConfigurationBuilder(
                    TokenCacheKey.class, String.class, ResourcePoolsBuilder.heap(HEAP_SIZE))
                .withExpiry(
                    Expirations.timeToLiveExpiration(
                        new Duration(USER_TOKEN_EXPIRY, TimeUnit.MINUTES))));

    secondaryTypesCache =
        cacheManager.createCache(
            "secondaryTypes",
            CacheConfigurationBuilder.newCacheConfigurationBuilder(
                    SecondaryTypesKey.class,
                    (Class<List<String>>) (Class<?>) List.class,
                    ResourcePoolsBuilder.heap(HEAP_SIZE))
                .withExpiry(Expirations.noExpiration()));

    maxAllowedAttachmentsCache =
        cacheManager.createCache(
            "maxAllowedAttachmentsCache",
            CacheConfigurationBuilder.newCacheConfigurationBuilder(
                    String.class, String.class, ResourcePoolsBuilder.heap(HEAP_SIZE))
                .withExpiry(Expirations.noExpiration()));
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

  public static Cache<RepoKey, String> getVersionedRepoCache() {
    return versionedRepoCache;
  }

  public static Cache<String, String> getMaxAllowedAttachmentsCache() {
    return maxAllowedAttachmentsCache;
  }

  public static Cache<SecondaryTypesKey, List<String>> getSecondaryTypesCache() {
    return secondaryTypesCache;
  }
}
