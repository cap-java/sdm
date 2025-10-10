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
  void testSecondaryPropertiesKeyWithLongValue() {
    // Given
    String repositoryId = "a".repeat(1000); // Very long repositoryId

    // When
    SecondaryPropertiesKey key = new SecondaryPropertiesKey(repositoryId);

    // Then
    assertNotNull(key);
    assertEquals(repositoryId, key.getRepositoryId());
    assertEquals(1000, key.getRepositoryId().length());
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
