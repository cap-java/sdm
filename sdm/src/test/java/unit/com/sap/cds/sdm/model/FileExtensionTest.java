package unit.com.sap.cds.sdm.model;

import static org.junit.jupiter.api.Assertions.*;

import com.sap.cds.sdm.model.FileExtension;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class FileExtensionTest {

  @Test
  void testFileExtensionCreationWithBuilder() {
    // Given
    String type = "image";
    List<String> extensions = Arrays.asList("jpg", "png", "gif");

    // When
    FileExtension fileExtension = FileExtension.builder().type(type).list(extensions).build();

    // Then
    assertNotNull(fileExtension);
    assertEquals(type, fileExtension.getType());
    assertEquals(extensions, fileExtension.getList());
  }

  @Test
  void testFileExtensionNoArgsConstructor() {
    // When
    FileExtension fileExtension = new FileExtension();

    // Then
    assertNotNull(fileExtension);
    assertNull(fileExtension.getType());
    assertNull(fileExtension.getList());
  }

  @Test
  void testFileExtensionAllArgsConstructor() {
    // Given
    String type = "document";
    List<String> extensions = Arrays.asList("pdf", "doc", "docx");

    // When
    FileExtension fileExtension = new FileExtension(type, extensions);

    // Then
    assertNotNull(fileExtension);
    assertEquals(type, fileExtension.getType());
    assertEquals(extensions, fileExtension.getList());
  }

  @Test
  void testFileExtensionSettersAndGetters() {
    // Given
    FileExtension fileExtension = new FileExtension();
    String type = "video";
    List<String> extensions = Arrays.asList("mp4", "avi", "mov");

    // When
    fileExtension.setType(type);
    fileExtension.setList(extensions);

    // Then
    assertEquals(type, fileExtension.getType());
    assertEquals(extensions, fileExtension.getList());
  }

  @Test
  void testFileExtensionWithNullValues() {
    // When
    FileExtension fileExtension = FileExtension.builder().type(null).list(null).build();

    // Then
    assertNotNull(fileExtension);
    assertNull(fileExtension.getType());
    assertNull(fileExtension.getList());
  }

  @Test
  void testFileExtensionWithEmptyList() {
    // Given
    String type = "empty";
    List<String> emptyList = Arrays.asList();

    // When
    FileExtension fileExtension = FileExtension.builder().type(type).list(emptyList).build();

    // Then
    assertNotNull(fileExtension);
    assertEquals(type, fileExtension.getType());
    assertEquals(emptyList, fileExtension.getList());
    assertTrue(fileExtension.getList().isEmpty());
  }

  @Test
  void testFileExtensionEqualsAndHashCode() {
    // Given
    String type = "audio";
    List<String> extensions = Arrays.asList("mp3", "wav", "flac");

    FileExtension fileExtension1 = FileExtension.builder().type(type).list(extensions).build();
    FileExtension fileExtension2 = FileExtension.builder().type(type).list(extensions).build();

    // Then
    assertEquals(fileExtension1, fileExtension2);
    assertEquals(fileExtension1.hashCode(), fileExtension2.hashCode());
  }

  @Test
  void testFileExtensionToString() {
    // Given
    String type = "text";
    List<String> extensions = Arrays.asList("txt", "md", "csv");
    FileExtension fileExtension = FileExtension.builder().type(type).list(extensions).build();

    // When
    String toString = fileExtension.toString();

    // Then
    assertNotNull(toString);
    assertTrue(toString.contains(type));
    assertTrue(toString.contains("FileExtension"));
  }
}
