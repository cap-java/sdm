package integration.com.sap.cds.sdm.utils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

public class ShellScriptRunner {

  /**
   * Runs a shell script and returns its exit code. stdout and stderr are printed to System.out /
   * System.err.
   *
   * @param scriptPath absolute or relative path to the .sh file
   * @param args additional arguments forwarded to the script
   * @return exit code of the process (0 = success)
   */
  public static int run(String scriptPath, String... args)
      throws IOException, InterruptedException {
    return run(null, scriptPath, args);
  }

  public static int run(Map<String, String> env, String scriptPath, String... args)
      throws IOException, InterruptedException {
    List<String> command = new ArrayList<>();
    command.add("bash");
    command.add(scriptPath);
    Collections.addAll(command, args);

    ProcessBuilder pb = new ProcessBuilder(command);
    if (env != null) {
      pb.environment().putAll(env);
    }
    pb.redirectErrorStream(false);
    Process process = pb.start();

    // Drain stdout (suppress console output)
    Thread stdoutThread =
        new Thread(
            () -> {
              try (BufferedReader reader =
                  new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                while (reader.readLine() != null) {
                  // discard
                }
              } catch (IOException e) {
                // ignore
              }
            });

    // Drain stderr (suppress console output)
    Thread stderrThread =
        new Thread(
            () -> {
              try (BufferedReader reader =
                  new BufferedReader(new InputStreamReader(process.getErrorStream()))) {
                while (reader.readLine() != null) {
                  // discard
                }
              } catch (IOException e) {
                // ignore
              }
            });

    stdoutThread.start();
    stderrThread.start();
    int exitCode = process.waitFor();
    stdoutThread.join();
    stderrThread.join();
    return exitCode;
  }

  /**
   * Runs a shell script, streams stderr to System.err, and returns the last non-empty line of
   * stdout. Useful for scripts that print a single result value as their final output line.
   *
   * @param scriptPath absolute or relative path to the .sh file
   * @param args additional arguments forwarded to the script
   * @return the last non-empty stdout line, or null if stdout was empty
   */
  public static String runAndCaptureOutput(String scriptPath, String... args)
      throws IOException, InterruptedException {
    return runAndCaptureOutput(null, scriptPath, args);
  }

  public static String runAndCaptureOutput(
      Map<String, String> env, String scriptPath, String... args)
      throws IOException, InterruptedException {
    List<String> command = new ArrayList<>();
    command.add("bash");
    command.add(scriptPath);
    Collections.addAll(command, args);

    ProcessBuilder pb = new ProcessBuilder(command);
    if (env != null) {
      pb.environment().putAll(env);
    }
    pb.redirectErrorStream(false);
    Process process = pb.start();

    final List<String> stdoutLines = new CopyOnWriteArrayList<>();
    final List<String> stderrLines = new CopyOnWriteArrayList<>();

    Thread stdoutThread =
        new Thread(
            () -> {
              try (BufferedReader reader =
                  new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                  if (!line.trim().isEmpty()) stdoutLines.add(line.trim());
                }
              } catch (IOException e) {
                // ignore
              }
            });

    Thread stderrThread =
        new Thread(
            () -> {
              try (BufferedReader reader =
                  new BufferedReader(new InputStreamReader(process.getErrorStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                  if (!line.trim().isEmpty()) stderrLines.add(line.trim());
                }
              } catch (IOException e) {
                // ignore
              }
            });

    stdoutThread.start();
    stderrThread.start();
    int exitCode = process.waitFor();
    stdoutThread.join();
    stderrThread.join();

    if (exitCode != 0) {
      String output = String.join("\n", stdoutLines);
      String errors = String.join("\n", stderrLines);
      throw new RuntimeException(
          scriptPath
              + " exited with code "
              + exitCode
              + "\nOutput: "
              + output
              + "\nStderr: "
              + errors);
    }
    return stdoutLines.isEmpty() ? null : stdoutLines.get(stdoutLines.size() - 1);
  }

  /**
   * Runs a shell script and returns all stdout lines as a list. Does NOT throw on non-zero exit
   * code — the caller is responsible for checking the exit code via the returned result.
   *
   * @param scriptPath absolute or relative path to the .sh file
   * @param args additional arguments forwarded to the script
   * @return a Result containing the exit code and all stdout lines
   */
  public static Result runAndCaptureAll(String scriptPath, String... args)
      throws IOException, InterruptedException {
    List<String> command = new ArrayList<>();
    command.add("bash");
    command.add(scriptPath);
    Collections.addAll(command, args);

    ProcessBuilder pb = new ProcessBuilder(command);
    pb.redirectErrorStream(false);
    Process process = pb.start();

    final List<String> stdoutLines = new CopyOnWriteArrayList<>();

    Thread stdoutThread =
        new Thread(
            () -> {
              try (BufferedReader reader =
                  new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                  stdoutLines.add(line);
                }
              } catch (IOException e) {
                // ignore
              }
            });

    Thread stderrThread =
        new Thread(
            () -> {
              try (BufferedReader reader =
                  new BufferedReader(new InputStreamReader(process.getErrorStream()))) {
                while (reader.readLine() != null) {
                  // discard
                }
              } catch (IOException e) {
                // ignore
              }
            });

    stdoutThread.start();
    stderrThread.start();
    int exitCode = process.waitFor();
    stdoutThread.join();
    stderrThread.join();

    return new Result(exitCode, stdoutLines);
  }

  /** Holds the exit code and captured stdout lines from a script execution. */
  public static class Result {
    private final int exitCode;
    private final List<String> lines;

    public Result(int exitCode, List<String> lines) {
      this.exitCode = exitCode;
      this.lines = lines;
    }

    public int getExitCode() {
      return exitCode;
    }

    public List<String> getLines() {
      return lines;
    }

    /** Returns all stdout lines joined with newline. */
    public String getOutput() {
      return String.join("\n", lines);
    }

    /** Check if any line contains the given substring (case-insensitive). */
    public boolean containsIgnoreCase(String substring) {
      String lower = substring.toLowerCase();
      return lines.stream().anyMatch(l -> l.toLowerCase().contains(lower));
    }
  }
}
