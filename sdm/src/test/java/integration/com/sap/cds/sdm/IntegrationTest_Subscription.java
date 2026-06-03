package integration.com.sap.cds.sdm;

import static org.junit.jupiter.api.Assertions.*;

import integration.com.sap.cds.sdm.utils.ShellScriptRunner;
import java.util.Map;
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
  private static final Map<String, String> TENANT_ENV =
      Map.of("ACTIVE_TENANT", System.getProperty("tenant", "TENANT1").replace("TENANT", ""));

  private static Properties credentials;
  private static String consumerSubdomain;

  /** Cached OAuth2 token for the consumer subdomain — fetched once in @BeforeAll. */
  private static Map<String, String> cmisEnv;

  @BeforeAll
  static void setup() throws Exception {
    credentials = Credentials.getCredentials(System.getProperty("tenant", "TENANT1"));
    consumerSubdomain = credentials.getProperty("consumerSubdomainMT");
    assertNotNull(consumerSubdomain, "consumerSubdomainMT must be set in credentials.properties");

    // Fetch OAuth2 token once for all CMIS calls in this test run.
    // Stored in cmisEnv and passed via CMIS_ACCESS_TOKEN env var to sdm-repo-manage.sh,
    // which short-circuits the per-call HTTP token fetch in get_token().
    System.out.println("BeforeAll: Fetching CMIS access token...");
    String token =
        ShellScriptRunner.runAndCaptureOutput(
            REPO_MANAGE_SCRIPT, "get-token", "--subdomain", consumerSubdomain);
    assertNotNull(token, "CMIS access token must not be null");
    cmisEnv = Map.of("CMIS_ACCESS_TOKEN", token);

    // Ensure subscription is active before tests run
    System.out.println("BeforeAll: Ensuring app is subscribed...");
    int subscribeExit = ShellScriptRunner.run(TENANT_ENV, SUBSCRIBE_SCRIPT);
    assertEquals(0, subscribeExit, "Initial subscription should succeed");
    Thread.sleep(15_000);

    // Verify repo exists after subscription; if not, onboard it
    System.out.println("BeforeAll: Checking if repo exists...");
    ShellScriptRunner.Result repoResult = repoCheck(SUBSCRIPTION_REPO_EXTERNAL_ID);
    if (repoResult.getExitCode() != 0) {
      System.out.println("BeforeAll: Repo not found — onboarding...");
      assertEquals(0, repoOnboard(SUBSCRIPTION_REPO_EXTERNAL_ID), "Repo onboard should succeed");
      Thread.sleep(10_000);
    }
    System.out.println("BeforeAll: Subscription active and repo verified.");
  }

  /** Check if a repo exists in the consumer scope. Returns the Result. */
  private static ShellScriptRunner.Result repoCheck(String externalId) throws Exception {
    assertNotNull(cmisEnv, "cmisEnv is null — CMIS token was not fetched in @BeforeAll");
    return ShellScriptRunner.runAndCaptureAll(
        cmisEnv,
        REPO_MANAGE_SCRIPT,
        "check",
        "--externalId",
        externalId,
        "--subdomain",
        consumerSubdomain);
  }

  /** Onboard a repo in the consumer scope. Returns exit code. */
  private static int repoOnboard(String externalId) throws Exception {
    assertNotNull(cmisEnv, "cmisEnv is null — CMIS token was not fetched in @BeforeAll");
    return ShellScriptRunner.run(
        cmisEnv,
        REPO_MANAGE_SCRIPT,
        "onboard",
        "--externalId",
        externalId,
        "--subdomain",
        consumerSubdomain);
  }

  /** Offboard a repo in the consumer scope. Returns the Result. */
  private static ShellScriptRunner.Result repoOffboard(String externalId) throws Exception {
    assertNotNull(cmisEnv, "cmisEnv is null — CMIS token was not fetched in @BeforeAll");
    return ShellScriptRunner.runAndCaptureAll(
        cmisEnv,
        REPO_MANAGE_SCRIPT,
        "offboard",
        "--externalId",
        externalId,
        "--subdomain",
        consumerSubdomain);
  }

  /** Check if a repo exists in provider scope (no --subdomain). Returns the Result. */
  private static ShellScriptRunner.Result repoCheckProviderScope(String externalId)
      throws Exception {
    return ShellScriptRunner.runAndCaptureAll(
        REPO_MANAGE_SCRIPT, "check", "--externalId", externalId);
  }

  /** Onboard a repo in provider scope (no --subdomain). Returns exit code. */
  private static int repoOnboardProviderScope(String externalId) throws Exception {
    return ShellScriptRunner.run(REPO_MANAGE_SCRIPT, "onboard", "--externalId", externalId);
  }

  /**
   * Polls the CMIS API until the repo returns NOT_FOUND (exit 1) in the consumer scope, or the
   * timeout is reached. Offboarding is async, so this retries every {@code intervalMs} up to {@code
   * maxRetries} times before failing the test.
   */
  private static void assertRepoOffboarded(String externalId) throws Exception {
    int maxRetries = 6;
    int intervalMs = 15_000;
    for (int attempt = 1; attempt <= maxRetries; attempt++) {
      ShellScriptRunner.Result result = repoCheck(externalId);
      if (result.getExitCode() == 1) {
        System.out.println(
            "  ✅ Repo '"
                + externalId
                + "' confirmed offboarded via CMIS (NOT_FOUND, attempt "
                + attempt
                + "/"
                + maxRetries
                + ")");
        return;
      }
      if (result.getExitCode() != 0) {
        fail(
            "CMIS check returned unexpected exit code "
                + result.getExitCode()
                + " for repo '"
                + externalId
                + "' (expected 0=found or 1=not_found). Output:\n"
                + result.getOutput());
        return;
      }
      if (attempt < maxRetries) {
        System.out.println(
            "  Repo still visible after unsubscribe (attempt "
                + attempt
                + "/"
                + maxRetries
                + ") — retrying in "
                + (intervalMs / 1000)
                + "s...");
        Thread.sleep(intervalMs);
      }
    }
    fail(
        "Repo '"
            + externalId
            + "' still exists in consumer scope "
            + (maxRetries * intervalMs / 1000)
            + "s after unsubscription");
  }

  // ───────────────────────────────────────────────────────────────────────────
  // Test 1 — Subscribe when already subscribed → handled gracefully, repo intact
  // ───────────────────────────────────────────────────────────────────────────
  @Test
  @Order(1)
  void testCreateSubscription_ExistingRepo_OnboardingSkipped() throws Exception {
    System.out.println("Test (1) : Subscribe when already subscribed — expect graceful handling");

    // Pre-condition: @BeforeAll left us subscribed with the repo onboarded.
    // Verify repo exists in consumer scope.
    System.out.println("  Verifying repo exists from setup subscription...");
    ShellScriptRunner.Result checkResult = repoCheck(SUBSCRIPTION_REPO_EXTERNAL_ID);
    assertEquals(
        0, checkResult.getExitCode(), "Repo should exist in consumer scope from @BeforeAll");

    // Act: Subscribe again (should detect 'Already subscribed' and exit 0)
    System.out.println("  Re-subscribing...");
    ShellScriptRunner.Result subscribeResult =
        ShellScriptRunner.runAndCaptureAll(TENANT_ENV, SUBSCRIBE_SCRIPT);
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
    String otherRepo = credentials.getProperty("defaultRepositoryID");
    assertNotNull(otherRepo, "defaultRepositoryID should be defined in credentials.properties");
    ShellScriptRunner.Result checkOther = repoCheckProviderScope(otherRepo);
    if (checkOther.getExitCode() != 0) {
      System.out.println("  Onboarding other repo '" + otherRepo + "' in provider scope...");
      int onboardExit = repoOnboardProviderScope(otherRepo);
      assertEquals(0, onboardExit, "Provider-scope onboard of other repo should succeed");
    }

    // Act: Unsubscribe
    System.out.println("  Unsubscribing...");
    int unsubscribeExit = ShellScriptRunner.run(TENANT_ENV, UNSUBSCRIBE_SCRIPT);
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

    // Extra check: verify via CMIS API that the subscription repo is no longer accessible
    System.out.println("  Verifying subscription repo offboarded via CMIS API...");
    assertRepoOffboarded(SUBSCRIPTION_REPO_EXTERNAL_ID);
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
    // Wait for test 2's unsubscribe to fully complete
    Thread.sleep(30_000);

    System.out.println("  Subscribing to set up precondition...");
    int subscribeExit = ShellScriptRunner.run(TENANT_ENV, SUBSCRIBE_SCRIPT);
    if (subscribeExit != 0) {
      System.out.println(
          "  First subscribe attempt failed (exit " + subscribeExit + ") — retrying after 30s...");
      Thread.sleep(30_000);
      subscribeExit = ShellScriptRunner.run(TENANT_ENV, SUBSCRIBE_SCRIPT);
    }
    assertEquals(0, subscribeExit, "Subscription should succeed");

    // Wait for repo to be onboarded
    Thread.sleep(15_000);

    // Verify precondition — repo exists in consumer scope
    ShellScriptRunner.Result checkResult = repoCheck(SUBSCRIPTION_REPO_EXTERNAL_ID);
    assertEquals(0, checkResult.getExitCode(), "Subscription repo should exist before unsubscribe");

    // Act: Unsubscribe
    System.out.println("  Unsubscribing...");
    int unsubscribeExit = ShellScriptRunner.run(TENANT_ENV, UNSUBSCRIBE_SCRIPT);
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

    // Extra check: verify via CMIS API that the subscription repo is no longer accessible
    System.out.println("  Verifying subscription repo offboarded via CMIS API...");
    assertRepoOffboarded(SUBSCRIPTION_REPO_EXTERNAL_ID);
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
    // Wait extra time for test 3's unsubscribe to fully complete
    Thread.sleep(30_000);

    System.out.println("  Subscribing...");
    int subscribeExit = ShellScriptRunner.run(TENANT_ENV, SUBSCRIBE_SCRIPT);
    if (subscribeExit != 0) {
      // Retry once after waiting — previous unsubscribe may still be processing
      System.out.println(
          "  First subscribe attempt failed (exit " + subscribeExit + ") — retrying after 30s...");
      Thread.sleep(30_000);
      subscribeExit = ShellScriptRunner.run(TENANT_ENV, SUBSCRIBE_SCRIPT);
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
    int unsubscribeExit = ShellScriptRunner.run(TENANT_ENV, UNSUBSCRIBE_SCRIPT);
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

    // Extra check: verify via CMIS API that the repo is still absent in consumer scope
    System.out.println("  Verifying repo remains absent via CMIS API...");
    assertRepoOffboarded(SUBSCRIPTION_REPO_EXTERNAL_ID);
  }

  // ───────────────────────────────────────────────────────────────────────────
  // Test 5 — Create subscription without existing repo → repo gets onboarded
  // ───────────────────────────────────────────────────────────────────────────
  @Test
  @Order(5)
  void testCreateSubscription_NoExistingRepo_RepoOnboarded() throws Exception {
    System.out.println("Test (5) : Subscribe without existing repo — expect repo to be onboarded");

    // Wait for test 4's unsubscribe to fully complete
    Thread.sleep(30_000);

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
    ShellScriptRunner.run(TENANT_ENV, UNSUBSCRIBE_SCRIPT);

    // Act: Subscribe
    System.out.println("  Subscribing...");
    int subscribeExit = ShellScriptRunner.run(TENANT_ENV, SUBSCRIBE_SCRIPT);
    if (subscribeExit != 0) {
      System.out.println(
          "  First subscribe attempt failed (exit " + subscribeExit + ") — retrying after 30s...");
      Thread.sleep(30_000);
      subscribeExit = ShellScriptRunner.run(TENANT_ENV, SUBSCRIBE_SCRIPT);
    }
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
