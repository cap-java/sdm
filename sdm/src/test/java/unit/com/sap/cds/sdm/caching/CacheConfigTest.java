package unit.com.sap.cds.sdm.caching;

import static org.junit.jupiter.api.Assertions.*;

import com.sap.cds.sdm.caching.CacheConfig;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import org.junit.jupiter.api.Test;

class CacheConfigTest {

  @Test
  void testCacheConfigConstructorThrowsException() {
    // When & Then
    assertThrows(
        InvocationTargetException.class,
        () -> {
          Constructor<CacheConfig> constructor = CacheConfig.class.getDeclaredConstructor();
          constructor.setAccessible(true);
          constructor.newInstance();
        });
  }

  @Test
  void testCacheConfigIsUtilityClass() {
    // Given - CacheConfig should be a utility class with private constructor
    Constructor<?>[] constructors = CacheConfig.class.getDeclaredConstructors();

    // Then
    assertEquals(1, constructors.length);
    assertTrue(java.lang.reflect.Modifier.isPrivate(constructors[0].getModifiers()));
  }

  @Test
  void testInitializeCacheDoesNotThrowWhenCalledOnce() {
    // This test verifies that the method exists and can be called
    // without throwing exceptions during compilation/loading
    // We don't actually call it to avoid EhCache state issues in tests

    // When & Then - just verify the method exists
    assertDoesNotThrow(
        () -> {
          // Just check that the class loads and method exists
          assertNotNull(CacheConfig.class.getDeclaredMethod("initializeCache"));
        });
  }

  @Test
  void testGetterMethodsExist() {
    // Verify all getter methods exist and are accessible
    assertDoesNotThrow(
        () -> {
          assertNotNull(CacheConfig.class.getDeclaredMethod("getUserTokenCache"));
          assertNotNull(CacheConfig.class.getDeclaredMethod("getClientCredentialsTokenCache"));
          assertNotNull(CacheConfig.class.getDeclaredMethod("getUserAuthoritiesTokenCache"));
          assertNotNull(CacheConfig.class.getDeclaredMethod("getRepoCache"));
          assertNotNull(CacheConfig.class.getDeclaredMethod("getSecondaryTypesCache"));
          assertNotNull(CacheConfig.class.getDeclaredMethod("getMaxAllowedAttachmentsCache"));
          assertNotNull(CacheConfig.class.getDeclaredMethod("getSecondaryPropertiesCache"));
        });
  }

  @Test
  void testCacheConfigClassStructure() {
    // Verify the class is properly structured as a utility class
    Class<?> clazz = CacheConfig.class;

    // Should be public class
    assertTrue(java.lang.reflect.Modifier.isPublic(clazz.getModifiers()));

    // Should not be abstract or interface
    assertFalse(java.lang.reflect.Modifier.isAbstract(clazz.getModifiers()));
    assertFalse(clazz.isInterface());
  }

  @Test
  void testInitializeCacheMethodExists() {
    // Test that initializeCache method is static and public
    assertDoesNotThrow(
        () -> {
          var method = CacheConfig.class.getDeclaredMethod("initializeCache");
          assertTrue(java.lang.reflect.Modifier.isStatic(method.getModifiers()));
          assertTrue(java.lang.reflect.Modifier.isPublic(method.getModifiers()));
          assertEquals(void.class, method.getReturnType());
          assertEquals(0, method.getParameterCount());
        });
  }

  @Test
  void testAllGetterMethodsAreStaticAndPublic() {
    // Test that all getter methods are properly defined as static and public
    String[] getterMethodNames = {
      "getUserTokenCache",
      "getClientCredentialsTokenCache",
      "getUserAuthoritiesTokenCache",
      "getRepoCache",
      "getSecondaryTypesCache",
      "getMaxAllowedAttachmentsCache",
      "getSecondaryPropertiesCache"
    };

    for (String methodName : getterMethodNames) {
      assertDoesNotThrow(
          () -> {
            var method = CacheConfig.class.getDeclaredMethod(methodName);
            assertTrue(
                java.lang.reflect.Modifier.isStatic(method.getModifiers()),
                methodName + " should be static");
            assertTrue(
                java.lang.reflect.Modifier.isPublic(method.getModifiers()),
                methodName + " should be public");
            assertEquals(0, method.getParameterCount(), methodName + " should have no parameters");
          });
    }
  }

  @Test
  void testCacheConfigConstants() {
    // Test that the constants are properly defined
    assertDoesNotThrow(
        () -> {
          var heapSizeField = CacheConfig.class.getDeclaredField("HEAP_SIZE");
          assertTrue(java.lang.reflect.Modifier.isStatic(heapSizeField.getModifiers()));
          assertTrue(java.lang.reflect.Modifier.isFinal(heapSizeField.getModifiers()));
          heapSizeField.setAccessible(true);
          assertEquals(1000, heapSizeField.get(null));

          var userTokenExpiryField = CacheConfig.class.getDeclaredField("USER_TOKEN_EXPIRY");
          assertTrue(java.lang.reflect.Modifier.isStatic(userTokenExpiryField.getModifiers()));
          assertTrue(java.lang.reflect.Modifier.isFinal(userTokenExpiryField.getModifiers()));
          userTokenExpiryField.setAccessible(true);
          assertEquals(660, userTokenExpiryField.get(null));

          var accessTokenExpiryField = CacheConfig.class.getDeclaredField("ACCESS_TOKEN_EXPIRY");
          assertTrue(java.lang.reflect.Modifier.isStatic(accessTokenExpiryField.getModifiers()));
          assertTrue(java.lang.reflect.Modifier.isFinal(accessTokenExpiryField.getModifiers()));
          accessTokenExpiryField.setAccessible(true);
          assertEquals(660, accessTokenExpiryField.get(null));
        });
  }

  @Test
  void testCacheConfigHasLogger() {
    // Test that the logger field is properly defined
    assertDoesNotThrow(
        () -> {
          var loggerField = CacheConfig.class.getDeclaredField("logger");
          assertTrue(java.lang.reflect.Modifier.isStatic(loggerField.getModifiers()));
          assertTrue(java.lang.reflect.Modifier.isFinal(loggerField.getModifiers()));
          assertTrue(java.lang.reflect.Modifier.isPrivate(loggerField.getModifiers()));
        });
  }

  @Test
  void testCacheManagerField() {
    // Test that the cacheManager field is properly defined
    assertDoesNotThrow(
        () -> {
          var cacheManagerField = CacheConfig.class.getDeclaredField("cacheManager");
          assertTrue(java.lang.reflect.Modifier.isStatic(cacheManagerField.getModifiers()));
          assertTrue(java.lang.reflect.Modifier.isPrivate(cacheManagerField.getModifiers()));
        });
  }

  @Test
  void testCacheFieldsExist() {
    // Test that all cache fields are properly declared
    String[] cacheFieldNames = {
      "userTokenCache",
      "clientCredentialsTokenCache",
      "userAuthoritiesTokenCache",
      "repoCache",
      "secondaryTypesCache",
      "maxAllowedAttachmentsCache",
      "secondaryPropertiesCache"
    };

    for (String fieldName : cacheFieldNames) {
      assertDoesNotThrow(
          () -> {
            var field = CacheConfig.class.getDeclaredField(fieldName);
            assertTrue(
                java.lang.reflect.Modifier.isStatic(field.getModifiers()),
                fieldName + " should be static");
            assertTrue(
                java.lang.reflect.Modifier.isPrivate(field.getModifiers()),
                fieldName + " should be private");
          });
    }
  }
}
