package unit.com.sap.cds.sdm.caching;

import static org.junit.jupiter.api.Assertions.*;

import com.sap.cds.sdm.caching.SecondaryPropertiesKey;
import org.junit.jupiter.api.Test;

class SecondaryPropertiesKeyTest {

  @Test
  void testSecondaryPropertiesKeyNoArgsConstructor() {
    // When
    SecondaryPropertiesKey key = new SecondaryPropertiesKey();

    // Then
    assertNotNull(key);
    assertNull(key.getRepositoryId());
  }

  @Test
  void testSecondaryPropertiesKeyAllArgsConstructor() {
    // Given
    String repositoryId = "repo123";

    // When
    SecondaryPropertiesKey key = new SecondaryPropertiesKey(repositoryId);

    // Then
    assertNotNull(key);
    assertEquals(repositoryId, key.getRepositoryId());
  }

  @Test
  void testSecondaryPropertiesKeySettersAndGetters() {
    // Given
    SecondaryPropertiesKey key = new SecondaryPropertiesKey();
    String repositoryId = "repo456";

    // When
    key.setRepositoryId(repositoryId);

    // Then
    assertEquals(repositoryId, key.getRepositoryId());
  }

  @Test
  void testSecondaryPropertiesKeyWithNullValue() {
    // When
    SecondaryPropertiesKey key = new SecondaryPropertiesKey(null);

    // Then
    assertNotNull(key);
    assertNull(key.getRepositoryId());
  }

  @Test
  void testSecondaryPropertiesKeyEqualsAndHashCode() {
    // Given
    String repositoryId = "repo789";
    SecondaryPropertiesKey key1 = new SecondaryPropertiesKey(repositoryId);
    SecondaryPropertiesKey key2 = new SecondaryPropertiesKey(repositoryId);
    SecondaryPropertiesKey key3 = new SecondaryPropertiesKey("different-repo");

    // Then
    assertEquals(key1, key2);
    assertEquals(key1.hashCode(), key2.hashCode());
    assertNotEquals(key1, key3);
    assertNotEquals(key1.hashCode(), key3.hashCode());
  }

  @Test
  void testSecondaryPropertiesKeyToString() {
    // Given
    String repositoryId = "repo-test";
    SecondaryPropertiesKey key = new SecondaryPropertiesKey(repositoryId);

    // When
    String toString = key.toString();

    // Then
    assertNotNull(toString);
    assertTrue(toString.contains(repositoryId));
    assertTrue(toString.contains("SecondaryPropertiesKey"));
  }

  @Test
  void testSecondaryPropertiesKeyWithEmptyString() {
    // Given
    String repositoryId = "";

    // When
    SecondaryPropertiesKey key = new SecondaryPropertiesKey(repositoryId);

    // Then
    assertNotNull(key);
    assertEquals(repositoryId, key.getRepositoryId());
    assertTrue(key.getRepositoryId().isEmpty());
  }

  @Test
  void testSecondaryPropertiesKeyWithSpecialCharacters() {
    // Given
    String repositoryId = "repo-123_456@domain.com:8080/path?param=value";

    // When
    SecondaryPropertiesKey key = new SecondaryPropertiesKey(repositoryId);

    // Then
    assertNotNull(key);
    assertEquals(repositoryId, key.getRepositoryId());
  }

  @Test
  void testSecondaryPropertiesKeyEqualsWithSelf() {
    // Given
    SecondaryPropertiesKey key = new SecondaryPropertiesKey("repo123");

    // Then
    assertEquals(key, key);
    assertEquals(key.hashCode(), key.hashCode());
  }

  @Test
  void testSecondaryPropertiesKeyEqualsWithNull() {
    // Given
    SecondaryPropertiesKey key = new SecondaryPropertiesKey("repo123");

    // Then
    assertNotEquals(key, null);
  }

  @Test
  void testSecondaryPropertiesKeyEqualsWithDifferentClass() {
    // Given
    SecondaryPropertiesKey key = new SecondaryPropertiesKey("repo123");
    String otherObject = "repo123";

    // Then
    assertNotEquals(key, otherObject);
  }

  @Test
  void testSecondaryPropertiesKeyHashCodeConsistency() {
    // Given
    String repositoryId = "repo-consistency";
    SecondaryPropertiesKey key = new SecondaryPropertiesKey(repositoryId);

    // When/Then - Hash code should be consistent across multiple calls
    int hashCode1 = key.hashCode();
    int hashCode2 = key.hashCode();
    assertEquals(hashCode1, hashCode2);
  }

  @Test
  void testSecondaryPropertiesKeyWithLongRepositoryId() {
    // Given
    String longRepositoryId = "a".repeat(1000);

    // When
    SecondaryPropertiesKey key = new SecondaryPropertiesKey(longRepositoryId);

    // Then
    assertNotNull(key);
    assertEquals(longRepositoryId, key.getRepositoryId());
    assertEquals(1000, key.getRepositoryId().length());
  }

  @Test
  void testSecondaryPropertiesKeyWithExtendedSpecialCharacters() {
    // Given
    String repositoryId = "repo-id@#$%^&*()_+{}[]|\\:;<>?,.";

    // When
    SecondaryPropertiesKey key = new SecondaryPropertiesKey(repositoryId);

    // Then
    assertNotNull(key);
    assertEquals(repositoryId, key.getRepositoryId());
  }

  @Test
  void testSecondaryPropertiesKeyWithUnicodeCharacters() {
    // Given
    String repositoryId = "仓库标识符-测试";

    // When
    SecondaryPropertiesKey key = new SecondaryPropertiesKey(repositoryId);

    // Then
    assertNotNull(key);
    assertEquals(repositoryId, key.getRepositoryId());
  }

  @Test
  void testSecondaryPropertiesKeyEqualsWithDifferentObjects() {
    // Given
    SecondaryPropertiesKey key = new SecondaryPropertiesKey("repo123");
    String differentObject = "repo123";
    Object nullObject = null;

    // Then
    assertNotEquals(key, differentObject);
    assertNotEquals(key, nullObject);
    assertNotEquals(nullObject, key);
  }

  @Test
  void testSecondaryPropertiesKeyEqualsReflexive() {
    // Given
    SecondaryPropertiesKey key = new SecondaryPropertiesKey("repo456");

    // Then - Reflexive property: x.equals(x) should be true
    assertEquals(key, key);
  }

  @Test
  void testSecondaryPropertiesKeyEqualsSymmetric() {
    // Given
    String repositoryId = "symmetric-test";
    SecondaryPropertiesKey key1 = new SecondaryPropertiesKey(repositoryId);
    SecondaryPropertiesKey key2 = new SecondaryPropertiesKey(repositoryId);

    // Then - Symmetric property: if x.equals(y), then y.equals(x)
    assertEquals(key1, key2);
    assertEquals(key2, key1);
  }

  @Test
  void testSecondaryPropertiesKeyEqualsTransitive() {
    // Given
    String repositoryId = "transitive-test";
    SecondaryPropertiesKey key1 = new SecondaryPropertiesKey(repositoryId);
    SecondaryPropertiesKey key2 = new SecondaryPropertiesKey(repositoryId);
    SecondaryPropertiesKey key3 = new SecondaryPropertiesKey(repositoryId);

    // Then - Transitive property: if x.equals(y) and y.equals(z), then x.equals(z)
    assertEquals(key1, key2);
    assertEquals(key2, key3);
    assertEquals(key1, key3);
  }

  @Test
  void testSecondaryPropertiesKeyHashCodeConsistencyMultipleCalls() {
    // Given
    SecondaryPropertiesKey key = new SecondaryPropertiesKey("consistency-test");

    // When
    int hashCode1 = key.hashCode();
    int hashCode2 = key.hashCode();

    // Then - Hash code should be consistent across multiple calls
    assertEquals(hashCode1, hashCode2);
  }

  @Test
  void testSecondaryPropertiesKeyHashCodeWithNullValue() {
    // Given
    SecondaryPropertiesKey keyWithNull = new SecondaryPropertiesKey(null);

    // Then - Should not throw exception
    assertDoesNotThrow(() -> keyWithNull.hashCode());
  }

  @Test
  void testSecondaryPropertiesKeyModificationAfterCreation() {
    // Given
    SecondaryPropertiesKey key = new SecondaryPropertiesKey("original-repo");

    // When
    key.setRepositoryId("modified-repo");

    // Then
    assertEquals("modified-repo", key.getRepositoryId());
  }

  @Test
  void testSecondaryPropertiesKeySetterWithNullAfterValue() {
    // Given
    SecondaryPropertiesKey key = new SecondaryPropertiesKey("initial-value");

    // When
    key.setRepositoryId(null);

    // Then
    assertNull(key.getRepositoryId());
  }

  @Test
  void testSecondaryPropertiesKeyWithWhitespaceValues() {
    // Given
    String whitespaceRepo = "  repo with spaces  ";
    String tabRepo = "\trepo\twith\ttabs\t";
    String newlineRepo = "repo\nwith\nnewlines";

    // When
    SecondaryPropertiesKey key1 = new SecondaryPropertiesKey(whitespaceRepo);
    SecondaryPropertiesKey key2 = new SecondaryPropertiesKey(tabRepo);
    SecondaryPropertiesKey key3 = new SecondaryPropertiesKey(newlineRepo);

    // Then
    assertEquals(whitespaceRepo, key1.getRepositoryId());
    assertEquals(tabRepo, key2.getRepositoryId());
    assertEquals(newlineRepo, key3.getRepositoryId());
  }

  @Test
  void testSecondaryPropertiesKeyLombokGeneratedMethods() {
    // Test that Lombok @Data annotation is working correctly
    // Given
    String repositoryId = "lombok-test";
    SecondaryPropertiesKey key1 = new SecondaryPropertiesKey(repositoryId);
    SecondaryPropertiesKey key2 = new SecondaryPropertiesKey(repositoryId);
    SecondaryPropertiesKey key3 = new SecondaryPropertiesKey("different");

    // Then - Test equals method
    assertEquals(key1, key2);
    assertNotEquals(key1, key3);

    // Test hashCode method
    assertEquals(key1.hashCode(), key2.hashCode());
    assertNotEquals(key1.hashCode(), key3.hashCode());

    // Test toString method
    String toString = key1.toString();
    assertNotNull(toString);
    assertTrue(toString.contains("SecondaryPropertiesKey"));
    assertTrue(toString.contains(repositoryId));
  }

  @Test
  void testSecondaryPropertiesKeySetterWithNullValue() {
    // Given
    SecondaryPropertiesKey key = new SecondaryPropertiesKey("initial-repo");

    // When
    key.setRepositoryId(null);

    // Then
    assertNull(key.getRepositoryId());
  }

  @Test
  void testSecondaryPropertiesKeyMultipleSetterCalls() {
    // Given
    SecondaryPropertiesKey key = new SecondaryPropertiesKey();

    // When/Then - Test multiple setter calls
    key.setRepositoryId("repo1");
    assertEquals("repo1", key.getRepositoryId());

    key.setRepositoryId("repo2");
    assertEquals("repo2", key.getRepositoryId());

    key.setRepositoryId("");
    assertEquals("", key.getRepositoryId());
    assertTrue(key.getRepositoryId().isEmpty());
  }

  @Test
  void testSecondaryPropertiesKeyEqualsWithNullRepositoryId() {
    // Given
    SecondaryPropertiesKey key1 = new SecondaryPropertiesKey(null);
    SecondaryPropertiesKey key2 = new SecondaryPropertiesKey(null);
    SecondaryPropertiesKey key3 = new SecondaryPropertiesKey("repo");

    // Then
    assertEquals(key1, key2);
    assertEquals(key1.hashCode(), key2.hashCode());
    assertNotEquals(key1, key3);
    assertNotEquals(key2, key3);
  }
}
