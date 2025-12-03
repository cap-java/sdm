package unit.com.sap.cds.sdm.caching;

import static org.junit.jupiter.api.Assertions.*;

import com.sap.cds.sdm.caching.CacheKey;
import org.junit.jupiter.api.Test;

class CacheKeyTest {

  @Test
  void testCacheKeyNoArgsConstructor() {
    // When
    CacheKey cacheKey = new CacheKey();

    // Then
    assertNotNull(cacheKey);
    assertNull(cacheKey.getKey());
    assertNull(cacheKey.getExpiration());
  }

  @Test
  void testCacheKeyAllArgsConstructor() {
    // Given
    String key = "test-key";
    String expiration = "2025-12-31";

    // When
    CacheKey cacheKey = new CacheKey(key, expiration);

    // Then
    assertNotNull(cacheKey);
    assertEquals(key, cacheKey.getKey());
    assertEquals(expiration, cacheKey.getExpiration());
  }

  @Test
  void testCacheKeySettersAndGetters() {
    // Given
    CacheKey cacheKey = new CacheKey();
    String key = "user-token";
    String expiration = "2025-10-10T10:00:00Z";

    // When
    cacheKey.setKey(key);
    cacheKey.setExpiration(expiration);

    // Then
    assertEquals(key, cacheKey.getKey());
    assertEquals(expiration, cacheKey.getExpiration());
  }

  @Test
  void testCacheKeyWithNullValues() {
    // When
    CacheKey cacheKey = new CacheKey(null, null);

    // Then
    assertNotNull(cacheKey);
    assertNull(cacheKey.getKey());
    assertNull(cacheKey.getExpiration());
  }

  @Test
  void testCacheKeyEqualsAndHashCode() {
    // Given
    String key = "cache-key";
    String expiration = "2025-12-31";
    CacheKey cacheKey1 = new CacheKey(key, expiration);
    CacheKey cacheKey2 = new CacheKey(key, expiration);
    CacheKey cacheKey3 = new CacheKey("different-key", expiration);

    // Then
    assertEquals(cacheKey1, cacheKey2);
    assertEquals(cacheKey1.hashCode(), cacheKey2.hashCode());
    assertNotEquals(cacheKey1, cacheKey3);
    assertNotEquals(cacheKey1.hashCode(), cacheKey3.hashCode());
  }

  @Test
  void testCacheKeyToString() {
    // Given
    String key = "test-key";
    String expiration = "2025-12-31";
    CacheKey cacheKey = new CacheKey(key, expiration);

    // When
    String toString = cacheKey.toString();

    // Then
    assertNotNull(toString);
    assertTrue(toString.contains(key));
    assertTrue(toString.contains(expiration));
    assertTrue(toString.contains("CacheKey"));
  }

  @Test
  void testCacheKeyWithEmptyStrings() {
    // Given
    String key = "";
    String expiration = "";

    // When
    CacheKey cacheKey = new CacheKey(key, expiration);

    // Then
    assertNotNull(cacheKey);
    assertEquals(key, cacheKey.getKey());
    assertEquals(expiration, cacheKey.getExpiration());
    assertTrue(cacheKey.getKey().isEmpty());
    assertTrue(cacheKey.getExpiration().isEmpty());
  }

  @Test
  void testCacheKeyWithSpecialCharacters() {
    // Given
    String key = "user-123_456@domain.com:8080/path?param=value";
    String expiration = "2025-12-31T23:59:59.999Z";

    // When
    CacheKey cacheKey = new CacheKey(key, expiration);

    // Then
    assertNotNull(cacheKey);
    assertEquals(key, cacheKey.getKey());
    assertEquals(expiration, cacheKey.getExpiration());
  }

  @Test
  void testCacheKeyEqualsWithSelf() {
    // Given
    CacheKey cacheKey = new CacheKey("key", "expiration");

    // Then
    assertEquals(cacheKey, cacheKey);
    assertEquals(cacheKey.hashCode(), cacheKey.hashCode());
  }

  @Test
  void testCacheKeyEqualsWithNull() {
    // Given
    CacheKey cacheKey = new CacheKey("key", "expiration");

    // Then
    assertNotEquals(cacheKey, null);
  }

  @Test
  void testCacheKeyEqualsWithDifferentClass() {
    // Given
    CacheKey cacheKey = new CacheKey("key", "expiration");
    String otherObject = "key";

    // Then
    assertNotEquals(cacheKey, otherObject);
  }

  @Test
  void testCacheKeyHashCodeConsistency() {
    // Given
    String key = "consistent-key";
    String expiration = "2025-12-31";
    CacheKey cacheKey = new CacheKey(key, expiration);

    // When/Then - Hash code should be consistent across multiple calls
    int hashCode1 = cacheKey.hashCode();
    int hashCode2 = cacheKey.hashCode();
    assertEquals(hashCode1, hashCode2);
  }

  @Test
  void testCacheKeyWithLongValues() {
    // Given
    String key = "a".repeat(1000); // Very long key
    String expiration = "b".repeat(1000); // Very long expiration

    // When
    CacheKey cacheKey = new CacheKey(key, expiration);

    // Then
    assertNotNull(cacheKey);
    assertEquals(key, cacheKey.getKey());
    assertEquals(expiration, cacheKey.getExpiration());
    assertEquals(1000, cacheKey.getKey().length());
    assertEquals(1000, cacheKey.getExpiration().length());
  }

  @Test
  void testCacheKeyEqualsWithDifferentExpiration() {
    // Given
    String key = "same-key";
    CacheKey cacheKey1 = new CacheKey(key, "2025-12-31");
    CacheKey cacheKey2 = new CacheKey(key, "2026-01-01");

    // Then
    assertNotEquals(cacheKey1, cacheKey2);
    assertNotEquals(cacheKey1.hashCode(), cacheKey2.hashCode());
  }

  @Test
  void testCacheKeyEqualsWithBothNulls() {
    // Given
    CacheKey cacheKey1 = new CacheKey(null, null);
    CacheKey cacheKey2 = new CacheKey(null, null);

    // Then
    assertEquals(cacheKey1, cacheKey2);
    assertEquals(cacheKey1.hashCode(), cacheKey2.hashCode());
  }

  @Test
  void testCacheKeyEqualsWithMixedNulls() {
    // Given
    CacheKey cacheKey1 = new CacheKey("key", null);
    CacheKey cacheKey2 = new CacheKey(null, "expiration");
    CacheKey cacheKey3 = new CacheKey(null, null);

    // Then
    assertNotEquals(cacheKey1, cacheKey2);
    assertNotEquals(cacheKey1, cacheKey3);
    assertNotEquals(cacheKey2, cacheKey3);
  }

  @Test
  void testCacheKeySettersWithNullValues() {
    // Given
    CacheKey cacheKey = new CacheKey("initial-key", "initial-expiration");

    // When
    cacheKey.setKey(null);
    cacheKey.setExpiration(null);

    // Then
    assertNull(cacheKey.getKey());
    assertNull(cacheKey.getExpiration());
  }

  @Test
  void testCacheKeyMultipleSetterCalls() {
    // Given
    CacheKey cacheKey = new CacheKey();

    // When/Then - Test multiple setter calls
    cacheKey.setKey("key1");
    assertEquals("key1", cacheKey.getKey());

    cacheKey.setKey("key2");
    assertEquals("key2", cacheKey.getKey());

    cacheKey.setExpiration("exp1");
    assertEquals("exp1", cacheKey.getExpiration());

    cacheKey.setExpiration("exp2");
    assertEquals("exp2", cacheKey.getExpiration());
  }

  @Test
  void testCacheKeyWithUnicodeCharacters() {
    // Given
    String unicodeKey = "测试键";
    String unicodeExpiration = "过期时间-2025";

    // When
    CacheKey cacheKey = new CacheKey(unicodeKey, unicodeExpiration);

    // Then
    assertEquals(unicodeKey, cacheKey.getKey());
    assertEquals(unicodeExpiration, cacheKey.getExpiration());
  }

  @Test
  void testCacheKeyWithExtendedSpecialCharacters() {
    // Given
    String keyWithSpecial = "key@#$%^&*()";
    String expirationWithSpecial = "exp!@#$%^&*()";

    // When
    CacheKey cacheKey = new CacheKey(keyWithSpecial, expirationWithSpecial);

    // Then
    assertEquals(keyWithSpecial, cacheKey.getKey());
    assertEquals(expirationWithSpecial, cacheKey.getExpiration());
  }

  @Test
  void testCacheKeyWithNewlineCharacters() {
    // Given
    String keyWithNewline = "key\nwith\nnewlines";
    String expirationWithNewline = "exp\rwith\r\nreturns";

    // When
    CacheKey cacheKey = new CacheKey(keyWithNewline, expirationWithNewline);

    // Then
    assertEquals(keyWithNewline, cacheKey.getKey());
    assertEquals(expirationWithNewline, cacheKey.getExpiration());
  }

  @Test
  void testCacheKeyWithTabCharacters() {
    // Given
    String keyWithTab = "key\twith\ttabs";
    String expirationWithTab = "exp\twith\ttabs";

    // When
    CacheKey cacheKey = new CacheKey(keyWithTab, expirationWithTab);

    // Then
    assertEquals(keyWithTab, cacheKey.getKey());
    assertEquals(expirationWithTab, cacheKey.getExpiration());
  }

  @Test
  void testCacheKeyLombokDataAnnotation() {
    // Test that Lombok @Data annotation generates proper methods
    // Given
    CacheKey key1 = new CacheKey("test", "2025");
    CacheKey key2 = new CacheKey("test", "2025");
    CacheKey key3 = new CacheKey("different", "2025");

    // Then - Test equals method generated by Lombok
    assertEquals(key1, key2);
    assertNotEquals(key1, key3);

    // Test hashCode method generated by Lombok
    assertEquals(key1.hashCode(), key2.hashCode());
    assertNotEquals(key1.hashCode(), key3.hashCode());

    // Test toString method generated by Lombok
    String toString = key1.toString();
    assertNotNull(toString);
    assertTrue(toString.contains("CacheKey"));
    assertTrue(toString.contains("test"));
    assertTrue(toString.contains("2025"));
  }

  @Test
  void testCacheKeyEqualsEdgeCases() {
    // Test various edge cases for equals method
    CacheKey key1 = new CacheKey("key", "exp");
    CacheKey key2 = new CacheKey("key", "exp");
    CacheKey key3 = new CacheKey("key", null);
    CacheKey key4 = new CacheKey(null, "exp");
    CacheKey key5 = new CacheKey(null, null);

    // Self equality
    assertEquals(key1, key1);

    // Null comparison
    assertNotEquals(key1, null);

    // Different class comparison
    assertNotEquals(key1, "string");

    // Same values
    assertEquals(key1, key2);

    // One field null
    assertNotEquals(key1, key3);
    assertNotEquals(key1, key4);

    // Both fields null
    CacheKey key6 = new CacheKey(null, null);
    assertEquals(key5, key6);
  }

  @Test
  void testCacheKeyFieldModificationAfterCreation() {
    // Test modifying fields after object creation
    // Given
    CacheKey key = new CacheKey("original-key", "original-exp");

    // When
    key.setKey("modified-key");
    key.setExpiration("modified-exp");

    // Then
    assertEquals("modified-key", key.getKey());
    assertEquals("modified-exp", key.getExpiration());
  }

  @Test
  void testCacheKeyHashCodeWithNulls() {
    // Test hashCode behavior with null values
    CacheKey key1 = new CacheKey(null, "exp");
    CacheKey key2 = new CacheKey("key", null);
    CacheKey key3 = new CacheKey(null, null);
    CacheKey key4 = new CacheKey(null, "exp");

    // Should not throw and should be consistent
    assertDoesNotThrow(() -> key1.hashCode());
    assertDoesNotThrow(() -> key2.hashCode());
    assertDoesNotThrow(() -> key3.hashCode());

    // Equal objects should have equal hash codes
    assertEquals(key1.hashCode(), key4.hashCode());
  }

  @Test
  void testCacheKeyImmutabilityBehavior() {
    // While CacheKey is mutable, test that changes affect equality correctly
    CacheKey key1 = new CacheKey("key", "exp");
    CacheKey key2 = new CacheKey("key", "exp");

    // Initially equal
    assertEquals(key1, key2);

    // Modify one
    key1.setKey("different-key");

    // Should no longer be equal
    assertNotEquals(key1, key2);
  }
}
