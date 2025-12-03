package unit.com.sap.cds.sdm.handler.common;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.sap.cds.sdm.handler.common.SDMAssociationCascader;
import com.sap.cds.sdm.handler.common.SDMAttachmentsReader;
import com.sap.cds.services.persistence.PersistenceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SDMAttachmentsReaderTest {

  @Mock private SDMAssociationCascader mockCascader;
  @Mock private PersistenceService mockPersistenceService;

  private SDMAttachmentsReader reader;

  @BeforeEach
  void setUp() {
    reader = new SDMAttachmentsReader(mockCascader, mockPersistenceService);
  }

  @Test
  void testConstructorWithNonNullValues() {
    // Test that reader can be created successfully
    assertNotNull(reader);
  }

  @Test
  void testConstructorWithNullCascader() {
    // Test null cascader throws NullPointerException
    assertThrows(
        NullPointerException.class,
        () -> {
          new SDMAttachmentsReader(null, mockPersistenceService);
        });
  }

  @Test
  void testConstructorWithNullPersistenceService() {
    // Test null persistence service throws NullPointerException
    assertThrows(
        NullPointerException.class,
        () -> {
          new SDMAttachmentsReader(mockCascader, null);
        });
  }
}
