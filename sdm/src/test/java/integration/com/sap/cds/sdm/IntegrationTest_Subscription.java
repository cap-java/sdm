package integration.com.sap.cds.sdm;

import static org.junit.jupiter.api.Assertions.*;

import integration.com.sap.cds.sdm.utils.ShellScriptRunner;
import org.junit.jupiter.api.*;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class IntegrationTest_Subscription {

  private static final String SUBSCRIBE_SCRIPT =
      "src/test/java/integration/com/sap/cds/sdm/utils/cf-subscribe.sh";
  private static final String UNSUBSCRIBE_SCRIPT =
      "src/test/java/integration/com/sap/cds/sdm/utils/cf-unsubscribe.sh";

  @Test
  @Order(1)
  void testCfUnsubscribe() throws Exception {
    System.out.println("Test (1) : Run cf-unsubscribe.sh and verify it succeeds");
    int exitCode = ShellScriptRunner.run(UNSUBSCRIBE_SCRIPT);
    assertEquals(0, exitCode, "cf-unsubscribe.sh should exit with code 0");
  }

  @Test
  @Order(2)
  void testCfSubscribe() throws Exception {
    System.out.println("Test (2) : Run cf-subscribe.sh and verify it succeeds");
    int exitCode = ShellScriptRunner.run(SUBSCRIBE_SCRIPT);
    assertEquals(0, exitCode, "cf-subscribe.sh should exit with code 0");
  }
}
