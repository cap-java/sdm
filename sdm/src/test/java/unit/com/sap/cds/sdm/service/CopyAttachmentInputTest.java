package unit.com.sap.cds.sdm.service;

import static org.junit.jupiter.api.Assertions.*;

import com.sap.cds.sdm.model.CopyAttachmentInput;
import java.util.List;
import org.junit.jupiter.api.Test;

public class CopyAttachmentInputTest {

  @Test
  void testFacetBasedInput() {
    // Test the new 3-parameter approach
    String upId = "123";
    String facet = "SourcingEventService.TermDefinitionsServiceEntity.references";
    List<String> objectIds = List.of("obj1", "obj2", "obj3");

    CopyAttachmentInput input = new CopyAttachmentInput(upId, facet, objectIds);

    assertEquals("123", input.upId());
    assertEquals("SourcingEventService.TermDefinitionsServiceEntity.references", input.facet());
    assertEquals(List.of("obj1", "obj2", "obj3"), input.objectIds());
  }

  @Test
  void testFacetParsing() {
    // This test shows how the facet should be parsed
    String facet = "SourcingEventService.TermDefinitionsServiceEntity.references";
    String[] parts = facet.split("\\.");

    assertEquals(3, parts.length);
    assertEquals("SourcingEventService", parts[0]);
    assertEquals("TermDefinitionsServiceEntity", parts[1]);
    assertEquals("references", parts[2]);

    String parentEntity = parts[0] + "." + parts[1];
    String compositionName = parts[2];

    assertEquals("SourcingEventService.TermDefinitionsServiceEntity", parentEntity);
    assertEquals("references", compositionName);
  }

  @Test
  void testProjectionEntityFacet() {
    // Test with a projection entity facet - should parse the same way
    String projectionFacet = "MyService.MyProjectionEntity.attachments";
    String[] parts = projectionFacet.split("\\.");

    assertEquals(3, parts.length);
    String parentEntity = parts[0] + "." + parts[1];
    String compositionName = parts[2];

    assertEquals("MyService.MyProjectionEntity", parentEntity);
    assertEquals("attachments", compositionName);
  }

  @Test
  void testInvalidFacetFormat() {
    // Test error handling for invalid facet formats
    String invalidFacet = "Service.Entity"; // Missing composition
    String[] parts = invalidFacet.split("\\.");

    assertTrue(parts.length < 3, "Should detect invalid facet format");
  }
}
