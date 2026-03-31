package integration.com.sap.cds.sdm.utils;

import static org.junit.jupiter.api.Assertions.fail;

/**
 * Helper for Cloud Foundry environment variable operations. Delegates to cf-update-env.sh via
 * ShellScriptRunner, following the same pattern as CmisDocumentHelper.
 */
public class CfEnvHelper {

  private static final String UPDATE_ENV_SCRIPT =
      "src/test/java/integration/com/sap/cds/sdm/utils/cf-update-env.sh";

  /**
   * Updates an environment variable on the CF app defined in credentials.properties, then restages
   * the app.
   *
   * @param key the environment variable name to set
   * @param value the value to assign
   */
  public static void updateEnv(String key, String value) {
    try {
      int exitCode = ShellScriptRunner.run(UPDATE_ENV_SCRIPT, "--key", key, "--value", value);
      if (exitCode != 0) {
        fail("cf-update-env.sh exited with non-zero code: " + exitCode);
      }
    } catch (Exception e) {
      fail("Failed to update CF environment variable '" + key + "': " + e.getMessage());
    }
  }
}
