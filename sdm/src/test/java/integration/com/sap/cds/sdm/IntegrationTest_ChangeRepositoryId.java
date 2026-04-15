package integration.com.sap.cds.sdm;

import static org.junit.jupiter.api.Assertions.*;

import integration.com.sap.cds.sdm.utils.ShellScriptRunner;
import org.junit.jupiter.api.*;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class IntegrationTest_ChangeRepositoryId {

  private static final String UPDATE_ENV_SCRIPT =
      "src/test/java/integration/com/sap/cds/sdm/utils/cf-update-env.sh";

  @Test
  @Order(1)
  void testChangeRepositoryId() throws Exception {
    System.out.println(
        "Test (1) : Run cf-update-env.sh to change REPOSITORY_ID and verify it succeeds");
    int exitCode = ShellScriptRunner.run(UPDATE_ENV_SCRIPT);
    assertEquals(0, exitCode, "cf-update-env.sh should exit with code 0");
  }
}
