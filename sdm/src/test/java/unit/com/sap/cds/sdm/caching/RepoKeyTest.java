package unit.com.sap.cds.sdm.caching;

import static org.junit.jupiter.api.Assertions.*;

import com.sap.cds.sdm.caching.RepoKey;
import org.junit.jupiter.api.Test;

class RepoKeyTest {

  @Test
  void testRepoKeyNoArgsConstructor() {
    // When
    RepoKey repoKey = new RepoKey();

    // Then
    assertNotNull(repoKey);
    assertNull(repoKey.getRepoId());
    assertNull(repoKey.getSubdomain());
  }

  @Test
  void testRepoKeyAllArgsConstructor() {
    // Given
    String repoId = "repo123";
    String subdomain = "test-subdomain";

    // When
    RepoKey repoKey = new RepoKey(repoId, subdomain);

    // Then
    assertNotNull(repoKey);
    assertEquals(repoId, repoKey.getRepoId());
    assertEquals(subdomain, repoKey.getSubdomain());
  }

  @Test
  void testRepoKeySettersAndGetters() {
    // Given
    RepoKey repoKey = new RepoKey();
    String repoId = "repo456";
    String subdomain = "prod-subdomain";

    // When
    repoKey.setRepoId(repoId);
    repoKey.setSubdomain(subdomain);

    // Then
    assertEquals(repoId, repoKey.getRepoId());
    assertEquals(subdomain, repoKey.getSubdomain());
  }

  @Test
  void testRepoKeyWithNullValues() {
    // When
    RepoKey repoKey = new RepoKey(null, null);

    // Then
    assertNotNull(repoKey);
    assertNull(repoKey.getRepoId());
    assertNull(repoKey.getSubdomain());
  }

  @Test
  void testRepoKeyEqualsAndHashCode() {
    // Given
    String repoId = "repo789";
    String subdomain = "dev-subdomain";
    RepoKey repoKey1 = new RepoKey(repoId, subdomain);
    RepoKey repoKey2 = new RepoKey(repoId, subdomain);
    RepoKey repoKey3 = new RepoKey("different-repo", subdomain);

    // Then
    assertEquals(repoKey1, repoKey2);
    assertEquals(repoKey1.hashCode(), repoKey2.hashCode());
    assertNotEquals(repoKey1, repoKey3);
    assertNotEquals(repoKey1.hashCode(), repoKey3.hashCode());
  }

  @Test
  void testRepoKeyToString() {
    // Given
    String repoId = "repo-test";
    String subdomain = "test-sub";
    RepoKey repoKey = new RepoKey(repoId, subdomain);

    // When
    String toString = repoKey.toString();

    // Then
    assertNotNull(toString);
    assertTrue(toString.contains(repoId));
    assertTrue(toString.contains(subdomain));
    assertTrue(toString.contains("RepoKey"));
  }

  @Test
  void testRepoKeyWithEmptyStrings() {
    // Given
    String repoId = "";
    String subdomain = "";

    // When
    RepoKey repoKey = new RepoKey(repoId, subdomain);

    // Then
    assertNotNull(repoKey);
    assertEquals(repoId, repoKey.getRepoId());
    assertEquals(subdomain, repoKey.getSubdomain());
    assertTrue(repoKey.getRepoId().isEmpty());
    assertTrue(repoKey.getSubdomain().isEmpty());
  }

  @Test
  void testRepoKeyWithSpecialCharacters() {
    // Given
    String repoId = "repo-123_456@domain.com";
    String subdomain = "sub-domain_123";

    // When
    RepoKey repoKey = new RepoKey(repoId, subdomain);

    // Then
    assertNotNull(repoKey);
    assertEquals(repoId, repoKey.getRepoId());
    assertEquals(subdomain, repoKey.getSubdomain());
  }

  @Test
  void testRepoKeyEqualsWithSelf() {
    // Given
    RepoKey repoKey = new RepoKey("repo123", "subdomain");

    // Then
    assertEquals(repoKey, repoKey);
    assertEquals(repoKey.hashCode(), repoKey.hashCode());
  }

  @Test
  void testRepoKeyEqualsWithNull() {
    // Given
    RepoKey repoKey = new RepoKey("repo123", "subdomain");

    // Then
    assertNotEquals(repoKey, null);
  }

  @Test
  void testRepoKeyEqualsWithDifferentClass() {
    // Given
    RepoKey repoKey = new RepoKey("repo123", "subdomain");
    String otherObject = "repo123";

    // Then
    assertNotEquals(repoKey, otherObject);
  }

  @Test
  void testRepoKeyHashCodeConsistency() {
    // Given
    String repoId = "repo-consistency";
    String subdomain = "sub-consistency";
    RepoKey repoKey = new RepoKey(repoId, subdomain);

    // When/Then - Hash code should be consistent across multiple calls
    int hashCode1 = repoKey.hashCode();
    int hashCode2 = repoKey.hashCode();
    assertEquals(hashCode1, hashCode2);
  }

  @Test
  void testRepoKeyWithLongValues() {
    // Given
    String repoId = "a".repeat(1000); // Very long repoId
    String subdomain = "b".repeat(1000); // Very long subdomain

    // When
    RepoKey repoKey = new RepoKey(repoId, subdomain);

    // Then
    assertNotNull(repoKey);
    assertEquals(repoId, repoKey.getRepoId());
    assertEquals(subdomain, repoKey.getSubdomain());
    assertEquals(1000, repoKey.getRepoId().length());
    assertEquals(1000, repoKey.getSubdomain().length());
  }

  @Test
  void testRepoKeyEqualsWithDifferentSubdomain() {
    // Given
    String repoId = "same-repo";
    RepoKey repoKey1 = new RepoKey(repoId, "subdomain1");
    RepoKey repoKey2 = new RepoKey(repoId, "subdomain2");

    // Then
    assertNotEquals(repoKey1, repoKey2);
    assertNotEquals(repoKey1.hashCode(), repoKey2.hashCode());
  }

  @Test
  void testRepoKeyEqualsWithBothNulls() {
    // Given
    RepoKey repoKey1 = new RepoKey(null, null);
    RepoKey repoKey2 = new RepoKey(null, null);

    // Then
    assertEquals(repoKey1, repoKey2);
    assertEquals(repoKey1.hashCode(), repoKey2.hashCode());
  }

  @Test
  void testRepoKeyEqualsWithMixedNulls() {
    // Given
    RepoKey repoKey1 = new RepoKey("repo", null);
    RepoKey repoKey2 = new RepoKey(null, "subdomain");
    RepoKey repoKey3 = new RepoKey(null, null);

    // Then
    assertNotEquals(repoKey1, repoKey2);
    assertNotEquals(repoKey1, repoKey3);
    assertNotEquals(repoKey2, repoKey3);
  }

  @Test
  void testRepoKeySettersWithNullValues() {
    // Given
    RepoKey repoKey = new RepoKey("initial-repo", "initial-subdomain");

    // When
    repoKey.setRepoId(null);
    repoKey.setSubdomain(null);

    // Then
    assertNull(repoKey.getRepoId());
    assertNull(repoKey.getSubdomain());
  }

  @Test
  void testRepoKeyMultipleSetterCalls() {
    // Given
    RepoKey repoKey = new RepoKey();

    // When/Then - Test multiple setter calls
    repoKey.setRepoId("repo1");
    assertEquals("repo1", repoKey.getRepoId());

    repoKey.setRepoId("repo2");
    assertEquals("repo2", repoKey.getRepoId());

    repoKey.setSubdomain("sub1");
    assertEquals("sub1", repoKey.getSubdomain());

    repoKey.setSubdomain("sub2");
    assertEquals("sub2", repoKey.getSubdomain());
  }
}
