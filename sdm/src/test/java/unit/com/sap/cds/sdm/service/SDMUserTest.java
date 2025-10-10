package unit.com.sap.cds.sdm.service;

import static org.junit.jupiter.api.Assertions.*;

import com.sap.cds.sdm.service.SDMUser;
import com.sap.cloud.sdk.cloudplatform.connectivity.ServiceBindingDestinationOptions;
import org.junit.jupiter.api.Test;

class SDMUserTest {

  @Test
  void testSDMUserCreation() {
    // Given
    String testUser = "testuser@example.com";

    // When
    SDMUser sdmUser = SDMUser.of(testUser);

    // Then
    assertNotNull(sdmUser);
    assertEquals(testUser, sdmUser.getValue());
  }

  @Test
  void testSDMUserWithNullValue() {
    // When
    SDMUser sdmUser = SDMUser.of(null);

    // Then
    assertNotNull(sdmUser);
    assertNull(sdmUser.getValue());
  }

  @Test
  void testSDMUserWithEmptyString() {
    // Given
    String emptyUser = "";

    // When
    SDMUser sdmUser = SDMUser.of(emptyUser);

    // Then
    assertNotNull(sdmUser);
    assertEquals(emptyUser, sdmUser.getValue());
  }

  @Test
  void testSDMUserImplementsOptionsEnhancer() {
    // Given
    String testUser = "user123";
    SDMUser sdmUser = SDMUser.of(testUser);

    // Then
    assertTrue(sdmUser instanceof ServiceBindingDestinationOptions.OptionsEnhancer);
  }

  @Test
  void testSDMUserWithLongUserName() {
    // Given
    String longUser = "very.long.username.with.many.parts@example.com";

    // When
    SDMUser sdmUser = SDMUser.of(longUser);

    // Then
    assertNotNull(sdmUser);
    assertEquals(longUser, sdmUser.getValue());
  }

  @Test
  void testSDMUserWithSpecialCharacters() {
    // Given
    String userWithSpecialChars = "user+123@test-domain.co.uk";

    // When
    SDMUser sdmUser = SDMUser.of(userWithSpecialChars);

    // Then
    assertNotNull(sdmUser);
    assertEquals(userWithSpecialChars, sdmUser.getValue());
  }

  @Test
  void testMultipleSDMUserInstances() {
    // Given
    String user1 = "user1@example.com";
    String user2 = "user2@example.com";

    // When
    SDMUser sdmUser1 = SDMUser.of(user1);
    SDMUser sdmUser2 = SDMUser.of(user2);

    // Then
    assertNotNull(sdmUser1);
    assertNotNull(sdmUser2);
    assertEquals(user1, sdmUser1.getValue());
    assertEquals(user2, sdmUser2.getValue());
    assertNotEquals(sdmUser1.getValue(), sdmUser2.getValue());
  }
}
