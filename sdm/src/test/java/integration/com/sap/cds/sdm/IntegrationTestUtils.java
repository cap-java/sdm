package integration.com.sap.cds.sdm;

import static org.junit.jupiter.api.Assertions.fail;

import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class IntegrationTestUtils {

  public String getDropDownValue() {
    ClassLoader classLoader = getClass().getClassLoader();
    File csvFile = new File(classLoader.getResource("WDIRSCodeList.csv").getFile());

    List<String> codes = new ArrayList<>();

    try (BufferedReader br = new BufferedReader(new FileReader(csvFile))) {
      String line;
      boolean firstLine = true;
      while ((line = br.readLine()) != null) {
        if (firstLine) {
          firstLine = false; // Skip header
          continue;
        }
        String[] parts = line.split(";");
        if (parts.length >= 1 && !parts[0].trim().isEmpty()) {
          codes.add(parts[0].trim()); // Add the code (A, B, C) to list
        }
      }
    } catch (FileNotFoundException e) {
      throw new RuntimeException(e);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }

    if (codes.isEmpty()) {
      fail("No valid dropdown code found in WDIRSCodeList.csv");
    }

    // Return a random value from the list
    Random random = new Random();
    return codes.get(random.nextInt(codes.size()));
  }
}
