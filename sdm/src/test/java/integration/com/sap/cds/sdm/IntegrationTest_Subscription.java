package integration.com.sap.cds.sdm;

import static org.junit.jupiter.api.Assertions.*;

import integration.com.sap.cds.sdm.utils.ShellScriptRunner;
import java.io.IOException;
import java.util.Properties;
import org.junit.jupiter.api.*;

/**
 * Integration tests for subscription lifecycle — verifies that subscribing and unsubscribing
 * correctly onboards/offboards SDM repositories.
 *
 * <p>Test scenarios:
 *
 * <ol>
 *   <li>Create subscription without existing repo → repo gets onboarded
 *   <li>Subscribe when already subscribed → handled gracefully, repo intact
 *   <li>Delete subscription with other repos → only subscription repo is offboarded
 *   <li>Delete subscription with only the correct repo → repo is offboarded
 *   <li>Delete subscription when repo doesn't exist → logs indicate 404 from DI
 * </ol>
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class IntegrationTest_Subscription {

  private static final String SUBSCRIBE_SCRIPT =
      "src/test/java/integration/com/sap/cds/sdm/utils/cf-subscribe.sh";
  private static final String UNSUBSCRIBE_SCRIPT =
      "src/test/java/integration/com/sap/cds/sdm/utils/cf-unsubscribe.sh";
  private static final String REPO_MANAGE_SCRIPT =
      "src/test/java/integration/com/sap/cds/sdm/utils/sdm-repo-manage.sh";
  private static final String CF_LOGS_SCRIPT =
      "src/test/java/integration/com/sap/cds/sdm/utils/cf-logs.sh";

  private static final String SUBSCRIPTION_REPO_EXTERNAL_ID = "MULTITENANT-TEST-REPO";
  private static final String MT_APP_NAME = "bookshop-mt-srv";

  private static Properties credentials;
  private static String consumerSubdomain;

  @BeforeAll
  static void setup() throws IOException {
    credentials = Credentials.getCredentials();
    consumerSubdomain = credentials.getProperty("CONSUMER_SUBDOMAIN");
    assertNotNull(consumerSubdomain, "CONSUMER_SUBDOMAIN must be set in credentials.properties");
  }

  /** Check if a repo exists in the consumer scope. Returns the Result. */
  private ShellScriptRunner.Result repoCheck(String externalId) throws Exception {
    return ShellScriptRunner.runAndCaptureAll(
        REPO_MANAGE_SCRIPT, "check", "--externalId", externalId, "--subdomain", consumerSubdomain);
  }

  /** Onboard a repo in the consumer scope. Returns exit code. */
  private int repoOnboard(String externalId) throws Exception {
    return ShellScriptRunner.run(
        REPO_MANAGE_SCRIPT,
        "onboard",
        "--externalId",
        externalId,
        "--subdomain",
        consumerSubdomain);
  }

  /** Offboard a repo in the consumer scope. Returns the Result. */
  private ShellScriptRunner.Result repoOffboard(String externalId) throws Exception {
    return ShellScriptRunner.runAndCaptureAll(
        REPO_MANAGE_SCRIPT,
        "offboard",
        "--externalId",
        externalId,
        "--subdomain",
        consumerSubdomain);
  }

  /** Check if a repo exists in provider scope (no --subdomain). Returns the Result. */
  private ShellScriptRunner.Result repoCheckProviderScope(String externalId) throws Exception {
    return ShellScriptRunner.runAndCaptureAll(
        REPO_MANAGE_SCRIPT, "check", "--externalId", externalId);
  }

  /** Onboard a repo in provider scope (no --subdomain). Returns exit code. */
  private int repoOnboardProviderScope(String externalId) throws Exception {
    return ShellScriptRunner.run(REPO_MANAGE_SCRIPT, "onboard", "--externalId", externalId);
  }

  // ───────────────────────────────────────────────────────────────────────────
  // Test 1 — Subscribe when already subscribed → handled gracefully, repo intact
  // ───────────────────────────────────────────────────────────────────────────
  @Test
  @Order(1)
  void testCreateSubscription_ExistingRepo_OnboardingSkipped() throws Exception {
    System.out.println("Test (1) : Subscribe when already subscribed — expect graceful handling");

    // Pre-condition: test 1 left us subscribed with the repo onboarded.
    // Verify repo exists in consumer scope.
    System.out.println("  Verifying repo exists from previous subscription...");
    ShellScriptRunner.Result checkResult = repoCheck(SUBSCRIPTION_REPO_EXTERNAL_ID);
    assertEquals(0, checkResult.getExitCode(), "Repo should exist in consumer scope from test 1");

    // Act: Subscribe again (should detect 'Already subscribed' and exit 0)
    System.out.println("  Re-subscribing...");
    ShellScriptRunner.Result subscribeResult = ShellScriptRunner.runAndCaptureAll(SUBSCRIBE_SCRIPT);
    assertEquals(0, subscribeResult.getExitCode(), "Re-subscription should succeed");
    assertTrue(
        subscribeResult.containsIgnoreCase("Already subscribed")
            || subscribeResult.containsIgnoreCase("Subscription is active"),
        "Subscribe output should indicate already subscribed or active. Output:\n"
            + subscribeResult.getOutput());

    // Verify: repo should still exist (subscription didn't break anything)
    ShellScriptRunner.Result verifyResult = repoCheck(SUBSCRIPTION_REPO_EXTERNAL_ID);
    assertEquals(0, verifyResult.getExitCode(), "Repository should still exist");
  }

  // ───────────────────────────────────────────────────────────────────────────
  // Test 2 — Delete subscription with other repos → only subscription repo offboarded
  // ───────────────────────────────────────────────────────────────────────────
  @Test
  @Order(2)
  void testDeleteSubscription_MultipleRepos_OnlyCorrectRepoOffboarded() throws Exception {
    System.out.println(
        "Test (2) : Unsubscribe with multiple repos — only correct repo should be offboarded");

    // Pre-condition: subscription is active (from test 2), subscription repo exists.
    // Ensure a second repo exists in provider scope (not tied to consumer subscription).
    String otherRepo = credentials.getProperty("repo1");
    assertNotNull(otherRepo, "repo1 should be defined in credentials.properties");
    ShellScriptRunner.Result checkOther = repoCheckProviderScope(otherRepo);
    if (checkOther.getExitCode() != 0) {
      System.out.println("  Onboarding other repo '" + otherRepo + "' in provider scope...");
      int onboardExit = repoOnboardProviderScope(otherRepo);
      assertEquals(0, onboardExit, "Provider-scope onboard of other repo should succeed");
    }

    // Act: Unsubscribe
    System.out.println("  Unsubscribing...");
    int unsubscribeExit = ShellScriptRunner.run(UNSUBSCRIBE_SCRIPT);
    assertEquals(0, unsubscribeExit, "Unsubscription should succeed");

    // Allow time for async offboarding
    Thread.sleep(15_000);

    // After unsubscribing, consumer-scoped token is no longer valid, so we
    // verify the offboard via CF logs instead of checking consumer scope.
    System.out.println("  Fetching CF logs to verify repo offboard...");
    ShellScriptRunner.Result logResult =
        ShellScriptRunner.runAndCaptureAll(CF_LOGS_SCRIPT, "--app", MT_APP_NAME);
    String logOutput = logResult.getOutput();
    boolean offboarded =
        logResult.containsIgnoreCase("Offboarded") || logResult.containsIgnoreCase("offboard");
    assertTrue(
        offboarded,
        "CF logs should indicate repo was offboarded. Logs:\n"
            + logOutput.substring(0, Math.min(logOutput.length(), 2000)));

    // Verify: The other repo (provider scope) should still exist
    ShellScriptRunner.Result verifyOther = repoCheckProviderScope(otherRepo);
    assertEquals(
        0,
        verifyOther.getExitCode(),
        "Other repo '" + otherRepo + "' should still exist after unsubscription");
  }

  // ───────────────────────────────────────────────────────────────────────────
  // Test 3 — Delete subscription with only correct repo → repo offboarded
  // ───────────────────────────────────────────────────────────────────────────
  @Test
  @Order(3)
  void testDeleteSubscription_OnlyCorrectRepo_RepoOffboarded() throws Exception {
    System.out.println(
        "Test (3) : Unsubscribe with only the subscription repo — expect repo offboarded");

    // Pre-condition: Subscribe and ensure only the subscription repo exists
    System.out.println("  Subscribing to set up precondition...");
    int subscribeExit = ShellScriptRunner.run(SUBSCRIBE_SCRIPT);
    assertEquals(0, subscribeExit, "Subscription should succeed");

    // Wait for repo to be onboarded
    Thread.sleep(15_000);

    // Verify precondition — repo exists in consumer scope
    ShellScriptRunner.Result checkResult = repoCheck(SUBSCRIPTION_REPO_EXTERNAL_ID);
    assertEquals(0, checkResult.getExitCode(), "Subscription repo should exist before unsubscribe");

    // Act: Unsubscribe
    System.out.println("  Unsubscribing...");
    int unsubscribeExit = ShellScriptRunner.run(UNSUBSCRIBE_SCRIPT);
    assertEquals(0, unsubscribeExit, "Unsubscription should succeed");

    // Allow time for offboarding
    Thread.sleep(15_000);

    // After unsubscribing, consumer-scoped token is no longer valid.
    // Verify offboard via CF logs.
    System.out.println("  Fetching CF logs to verify repo offboard...");
    ShellScriptRunner.Result logResult =
        ShellScriptRunner.runAndCaptureAll(CF_LOGS_SCRIPT, "--app", MT_APP_NAME);
    String logOutput = logResult.getOutput();
    boolean offboarded =
        logResult.containsIgnoreCase("Offboarded") || logResult.containsIgnoreCase("offboard");
    assertTrue(
        offboarded,
        "CF logs should confirm repo was offboarded. Logs:\n"
            + logOutput.substring(0, Math.min(logOutput.length(), 2000)));
  }

  // ───────────────────────────────────────────────────────────────────────────
  // Test 4 — Delete subscription when repo doesn't exist → logs indicate 404
  // ───────────────────────────────────────────────────────────────────────────
  @Test
  @Order(4)
  void testDeleteSubscription_RepoDoesNotExist_Logs404() throws Exception {
    System.out.println(
        "Test (4) : Unsubscribe when repo doesn't exist — expect logs to indicate 404 from DI");

    // Pre-condition: Ensure subscribed but repo does NOT exist
    // Wait extra time for test 4's unsubscribe to fully complete
    Thread.sleep(30_000);

    System.out.println("  Subscribing...");
    int subscribeExit = ShellScriptRunner.run(SUBSCRIBE_SCRIPT);
    if (subscribeExit != 0) {
      // Retry once after waiting — previous unsubscribe may still be processing
      System.out.println(
          "  First subscribe attempt failed (exit " + subscribeExit + ") — retrying after 30s...");
      Thread.sleep(30_000);
      subscribeExit = ShellScriptRunner.run(SUBSCRIBE_SCRIPT);
    }
    assertEquals(0, subscribeExit, "Subscription should succeed");

    // Wait for subscription callback to complete
    Thread.sleep(15_000);

    // Verify repo was onboarded by the subscription callback
    System.out.println("  Verifying repo was onboarded after subscription...");
    ShellScriptRunner.Result repoResult = repoCheck(SUBSCRIPTION_REPO_EXTERNAL_ID);
    assertEquals(
        0,
        repoResult.getExitCode(),
        "Repository should exist after subscription before manual offboard");

    // Manually offboard the repo so it doesn't exist when we unsubscribe
    System.out.println("  Manually offboarding repo to set up precondition...");
    ShellScriptRunner.Result offboardResult = repoOffboard(SUBSCRIPTION_REPO_EXTERNAL_ID);
    // It's OK if offboard fails because the repo might not exist
    if (offboardResult.getExitCode() == 0) {
      System.out.println("  Repo offboarded successfully.");
    } else {
      System.out.println(
          "  Repo was already not present (exit code: " + offboardResult.getExitCode() + ")");
    }

    // Verify precondition — repo should NOT exist
    ShellScriptRunner.Result checkResult = repoCheck(SUBSCRIPTION_REPO_EXTERNAL_ID);
    assertEquals(
        1, checkResult.getExitCode(), "Repo should NOT exist before unsubscribe for this test");

    // Act: Unsubscribe (the app will try to offboard a non-existent repo)
    System.out.println("  Unsubscribing...");
    int unsubscribeExit = ShellScriptRunner.run(UNSUBSCRIBE_SCRIPT);
    assertEquals(0, unsubscribeExit, "Unsubscription itself should succeed");

    // Allow time for the unsubscribe callback to process
    Thread.sleep(15_000);

    // Verify: Check CF logs for 404 indication from DI/SDM
    System.out.println("  Fetching CF logs to verify 404 handling...");
    ShellScriptRunner.Result logResult =
        ShellScriptRunner.runAndCaptureAll(CF_LOGS_SCRIPT, "--app", MT_APP_NAME);
    String logOutput = logResult.getOutput();

    boolean has404Indication =
        logResult.containsIgnoreCase("not found")
            || logResult.containsIgnoreCase("Repository with ID")
            || logResult.containsIgnoreCase("404")
            || logResult.containsIgnoreCase("does not exist");
    assertTrue(
        has404Indication,
        "CF logs should indicate a 404 or 'not found' when offboarding non-existent repo. Logs:\n"
            + logOutput.substring(0, Math.min(logOutput.length(), 2000)));
  }

  // ───────────────────────────────────────────────────────────────────────────
  // Test 5 — Create subscription without existing repo → repo gets onboarded
  // ───────────────────────────────────────────────────────────────────────────
  @Test
  @Order(5)
  void testCreateSubscription_NoExistingRepo_RepoOnboarded() throws Exception {
    System.out.println("Test (5) : Subscribe without existing repo — expect repo to be onboarded");

    // Pre-condition: ensure the repo does NOT exist (offboard if present)
    System.out.println("  Ensuring repo '" + SUBSCRIPTION_REPO_EXTERNAL_ID + "' does not exist...");
    ShellScriptRunner.Result checkResult = repoCheck(SUBSCRIPTION_REPO_EXTERNAL_ID);
    if (checkResult.getExitCode() == 0) {
      // Repo exists — offboard it first
      System.out.println("  Repo exists — offboarding to set up precondition...");
      ShellScriptRunner.Result offResult = repoOffboard(SUBSCRIPTION_REPO_EXTERNAL_ID);
      assertEquals(0, offResult.getExitCode(), "Pre-condition offboard should succeed");
    }

    // Also ensure NOT subscribed
    System.out.println("  Ensuring consumer is unsubscribed...");
    ShellScriptRunner.run(UNSUBSCRIBE_SCRIPT);

    // Act: Subscribe
    System.out.println("  Subscribing...");
    int subscribeExit = ShellScriptRunner.run(SUBSCRIBE_SCRIPT);
    assertEquals(0, subscribeExit, "Subscription should succeed");

    // Allow time for async repo onboarding
    Thread.sleep(15_000);

    // Verify: repo should now exist
    System.out.println("  Verifying repo was onboarded...");
    ShellScriptRunner.Result verifyResult = repoCheck(SUBSCRIPTION_REPO_EXTERNAL_ID);
    assertEquals(
        0,
        verifyResult.getExitCode(),
        "Repository '" + SUBSCRIPTION_REPO_EXTERNAL_ID + "' should exist after subscription");
    assertTrue(
        verifyResult.containsIgnoreCase("FOUND"), "Check output should confirm repo was found");
  }
}
