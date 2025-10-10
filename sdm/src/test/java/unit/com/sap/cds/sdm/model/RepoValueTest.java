package unit.com.sap.cds.sdm.model;

import static org.junit.jupiter.api.Assertions.*;

import com.sap.cds.sdm.model.RepoValue;
import org.junit.jupiter.api.Test;

class RepoValueTest {

  @Test
  void testRepoValueNoArgsConstructor() {
    // When
    RepoValue repoValue = new RepoValue();

    // Then
    assertNotNull(repoValue);
    assertNull(repoValue.getVirusScanEnabled());
    assertNull(repoValue.getVersionEnabled());
    assertNull(repoValue.getDisableVirusScannerForLargeFile());
  }

  @Test
  void testRepoValueAllArgsConstructor() {
    // Given
    Boolean virusScanEnabled = true;
    Boolean versionEnabled = false;
    Boolean disableVirusScannerForLargeFile = true;

    // When
    RepoValue repoValue =
        new RepoValue(virusScanEnabled, versionEnabled, disableVirusScannerForLargeFile);

    // Then
    assertNotNull(repoValue);
    assertEquals(virusScanEnabled, repoValue.getVirusScanEnabled());
    assertEquals(versionEnabled, repoValue.getVersionEnabled());
    assertEquals(disableVirusScannerForLargeFile, repoValue.getDisableVirusScannerForLargeFile());
  }

  @Test
  void testRepoValueSettersAndGetters() {
    // Given
    RepoValue repoValue = new RepoValue();
    Boolean virusScanEnabled = false;
    Boolean versionEnabled = true;
    Boolean disableVirusScannerForLargeFile = false;

    // When
    repoValue.setVirusScanEnabled(virusScanEnabled);
    repoValue.setVersionEnabled(versionEnabled);
    repoValue.setDisableVirusScannerForLargeFile(disableVirusScannerForLargeFile);

    // Then
    assertEquals(virusScanEnabled, repoValue.getVirusScanEnabled());
    assertEquals(versionEnabled, repoValue.getVersionEnabled());
    assertEquals(disableVirusScannerForLargeFile, repoValue.getDisableVirusScannerForLargeFile());
  }

  @Test
  void testRepoValueWithNullValues() {
    // When
    RepoValue repoValue = new RepoValue(null, null, null);

    // Then
    assertNotNull(repoValue);
    assertNull(repoValue.getVirusScanEnabled());
    assertNull(repoValue.getVersionEnabled());
    assertNull(repoValue.getDisableVirusScannerForLargeFile());
  }

  @Test
  void testRepoValueEqualsAndHashCode() {
    // Given
    Boolean virusScanEnabled = true;
    Boolean versionEnabled = true;
    Boolean disableVirusScannerForLargeFile = false;

    RepoValue repoValue1 =
        new RepoValue(virusScanEnabled, versionEnabled, disableVirusScannerForLargeFile);
    RepoValue repoValue2 =
        new RepoValue(virusScanEnabled, versionEnabled, disableVirusScannerForLargeFile);
    RepoValue repoValue3 = new RepoValue(false, versionEnabled, disableVirusScannerForLargeFile);

    // Then
    assertEquals(repoValue1, repoValue2);
    assertEquals(repoValue1.hashCode(), repoValue2.hashCode());
    assertNotEquals(repoValue1, repoValue3);
    assertNotEquals(repoValue1.hashCode(), repoValue3.hashCode());
  }

  @Test
  void testRepoValueToString() {
    // Given
    Boolean virusScanEnabled = true;
    Boolean versionEnabled = false;
    Boolean disableVirusScannerForLargeFile = true;
    RepoValue repoValue =
        new RepoValue(virusScanEnabled, versionEnabled, disableVirusScannerForLargeFile);

    // When
    String toString = repoValue.toString();

    // Then
    assertNotNull(toString);
    assertTrue(toString.contains("RepoValue"));
    assertTrue(toString.contains("true") || toString.contains("false"));
  }
}
