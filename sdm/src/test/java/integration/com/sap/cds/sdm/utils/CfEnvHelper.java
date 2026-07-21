package integration.com.sap.cds.sdm.utils;

import static org.junit.jupiter.api.Assertions.fail;

/**
 * Helper for Cloud Foundry environment variable operations. Delegates to cf-update-env.sh via
 * ShellScriptRunner, following the same pattern as CmisDocumentHelper.
 */
public class CfEnvHelper {

  private static final String UPDATE_ENV_SCRIPT =
      "src/test/java/integration/com/sap/cds/sdm/utils/cf-update-env.sh";

  public static void updateEnv(String key, String value) {
    updateEnv(null, key, value);
  }

  public static void updateEnv(String app, String key, String value) {
    try {
      int exitCode;
      if (app != null) {
        exitCode =
            ShellScriptRunner.run(UPDATE_ENV_SCRIPT, "--app", app, "--key", key, "--value", value);
      } else {
        exitCode = ShellScriptRunner.run(UPDATE_ENV_SCRIPT, "--key", key, "--value", value);
      }
      if (exitCode != 0) {
        fail("cf-update-env.sh exited with non-zero code: " + exitCode);
      }
    } catch (Exception e) {
      fail("Failed to update CF environment variable '" + key + "': " + e.getMessage());
    }
  }
}
