package unit.com.sap.cds.sdm.utilities;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.sap.cds.CdsData;
import com.sap.cds.sdm.utilities.SDMUtils;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class SDMUtilsTest {

  @Test
  public void testIsFileNameDuplicateInDrafts() {
    List<CdsData> data = new ArrayList<>();
    CdsData mockCdsData = mock(CdsData.class);
    Map<String, Object> entity = new HashMap<>();
    List<Map<String, Object>> attachments = new ArrayList<>();
    Map<String, Object> attachment1 = new HashMap<>();
    attachment1.put("fileName", "file1.txt");
    Map<String, Object> attachment2 = new HashMap<>();
    attachment2.put("fileName", "file1.txt");
    attachments.add(attachment1);
    attachments.add(attachment2);
    entity.put("attachments", attachments);
    when(mockCdsData.get("attachments")).thenReturn(attachments); // Correctly mock get method
    data.add(mockCdsData);

    Set<String> duplicateFilenames = SDMUtils.isFileNameDuplicateInDrafts(data);

    assertTrue(duplicateFilenames.contains("file1.txt"));
  }
}
