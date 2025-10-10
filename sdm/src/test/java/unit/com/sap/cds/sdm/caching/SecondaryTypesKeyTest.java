package unit.com.sap.cds.sdm.caching;

import static org.junit.jupiter.api.Assertions.*;

import com.sap.cds.sdm.caching.SecondaryTypesKey;
import org.junit.jupiter.api.Test;

class SecondaryTypesKeyTest {

  @Test
  void testSecondaryTypesKeyNoArgsConstructor() {
    // When
    SecondaryTypesKey key = new SecondaryTypesKey();

    // Then
    assertNotNull(key);
    assertNull(key.getRepositoryId());
  }

  @Test
  void testSecondaryTypesKeyAllArgsConstructor() {
    // Given
    String repositoryId = "repo123";

    // When
    SecondaryTypesKey key = new SecondaryTypesKey(repositoryId);

    // Then
    assertNotNull(key);
    assertEquals(repositoryId, key.getRepositoryId());
  }

  @Test
  void testSecondaryTypesKeySettersAndGetters() {
    // Given
    SecondaryTypesKey key = new SecondaryTypesKey();
    String repositoryId = "repo456";

    // When
    key.setRepositoryId(repositoryId);

    // Then
    assertEquals(repositoryId, key.getRepositoryId());
  }

  @Test
  void testSecondaryTypesKeyWithNullValue() {
    // When
    SecondaryTypesKey key = new SecondaryTypesKey(null);

    // Then
    assertNotNull(key);
    assertNull(key.getRepositoryId());
  }

  @Test
  void testSecondaryTypesKeyEqualsAndHashCode() {
    // Given
    String repositoryId = "repo789";
    SecondaryTypesKey key1 = new SecondaryTypesKey(repositoryId);
    SecondaryTypesKey key2 = new SecondaryTypesKey(repositoryId);
    SecondaryTypesKey key3 = new SecondaryTypesKey("different-repo");

    // Then
    assertEquals(key1, key2);
    assertEquals(key1.hashCode(), key2.hashCode());
    assertNotEquals(key1, key3);
    assertNotEquals(key1.hashCode(), key3.hashCode());
  }

  @Test
  void testSecondaryTypesKeyToString() {
    // Given
    String repositoryId = "repo-test";
    SecondaryTypesKey key = new SecondaryTypesKey(repositoryId);

    // When
    String toString = key.toString();

    // Then
    assertNotNull(toString);
    assertTrue(toString.contains(repositoryId));
    assertTrue(toString.contains("SecondaryTypesKey"));
  }

  @Test
  void testSecondaryTypesKeyWithEmptyString() {
    // Given
    String repositoryId = "";

    // When
    SecondaryTypesKey key = new SecondaryTypesKey(repositoryId);

    // Then
    assertNotNull(key);
    assertEquals(repositoryId, key.getRepositoryId());
    assertTrue(key.getRepositoryId().isEmpty());
  }

  @Test
  void testSecondaryTypesKeyWithSpecialCharacters() {
    // Given
    String repositoryId = "repo-123_456@domain.com";

    // When
    SecondaryTypesKey key = new SecondaryTypesKey(repositoryId);

    // Then
    assertNotNull(key);
    assertEquals(repositoryId, key.getRepositoryId());
  }

  @Test
  void testSecondaryTypesKeyEqualsWithSelf() {
    // Given
    SecondaryTypesKey key = new SecondaryTypesKey("repo123");

    // Then
    assertEquals(key, key);
    assertEquals(key.hashCode(), key.hashCode());
  }

  @Test
  void testSecondaryTypesKeyEqualsWithNull() {
    // Given
    SecondaryTypesKey key = new SecondaryTypesKey("repo123");

    // Then
    assertNotEquals(key, null);
  }

  @Test
  void testSecondaryTypesKeyEqualsWithDifferentClass() {
    // Given
    SecondaryTypesKey key = new SecondaryTypesKey("repo123");
    String otherObject = "repo123";

    // Then
    assertNotEquals(key, otherObject);
  }

  @Test
  void testSecondaryTypesKeyHashCodeConsistency() {
    // Given
    String repositoryId = "repo-consistency";
    SecondaryTypesKey key = new SecondaryTypesKey(repositoryId);

    // When/Then - Hash code should be consistent across multiple calls
    int hashCode1 = key.hashCode();
    int hashCode2 = key.hashCode();
    assertEquals(hashCode1, hashCode2);
  }
}
