package dev.xirreal;

import static dev.xirreal.PlatformUtils.*;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import javax.swing.*;
import net.fabricmc.loader.api.entrypoint.PreLaunchEntrypoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GfxDebuggers implements PreLaunchEntrypoint {

   public static final String MOD_ID = "gfx-debuggers";
   public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

   private static final String RENDERDOC_MARKER_ENV = "GFX_DEBUGGERS_RENDERDOC";
   private static final String NSIGHT_MARKER_ENV = "GFX_DEBUGGERS_NSIGHT";

   @Override
   public void onPreLaunch() {
      if (!IS_WINDOWS && !IS_LINUX) {
         LOGGER.error("Unsupported OS: {}", System.getProperty("os.name"));
         return;
      }
      String arch = System.getProperty("os.arch").toLowerCase();
      if (!arch.contains("64")) {
         LOGGER.error("Unsupported architecture: {}", arch);
         return;
      }

      String renderdocMarker = System.getenv(RENDERDOC_MARKER_ENV);
      if ("1".equals(renderdocMarker)) {
         LOGGER.info("Game was re-launched with RenderDoc LD_PRELOAD marker. Checking for library...");
         if (IS_LINUX && isLibraryLoaded("librenderdoc")) {
            LOGGER.info("Renderdoc injection successful.");
         } else if (IS_LINUX) {
            LOGGER.warn("Renderdoc marker found but library not loaded. Injection failed.");
         }
         return;
      }

      String nsightMarker = System.getenv(NSIGHT_MARKER_ENV);
      if ("1".equals(nsightMarker)) {
         LOGGER.info("Game was launched via ngfx. NSight Graphics injection is active.");
         return;
      }

      int option = -1;
      String optionString = System.getProperty("debugger");
      if (optionString != null) {
         if (optionString.equalsIgnoreCase("renderdoc")) {
            option = JOptionPane.CANCEL_OPTION;
         } else if (optionString.equalsIgnoreCase("nsight-gpu")) {
            option = JOptionPane.YES_OPTION;
         } else if (optionString.equalsIgnoreCase("nsight-frame")) {
            option = JOptionPane.NO_OPTION;
         }
      }

      if (option == -1) {
         System.setProperty("java.awt.headless", "false");

         try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
         } catch (ReflectiveOperationException | UnsupportedLookAndFeelException ignored) {}

         String[] options = { "NSight GPU Trace", "NSight Frame Profiler", "Renderdoc" };

         JFrame frame = new JFrame("Choose a debugger to be loaded");
         frame.setUndecorated(true);
         frame.setVisible(true);
         frame.setLocationRelativeTo(null);
         frame.requestFocus();

         option = JOptionPane.showOptionDialog(
            frame,
            "Closing the dialog will skip injection.\n\nNSight or Renderdoc must be installed for this to work properly.",
            "Shader debugging",
            JOptionPane.YES_NO_CANCEL_OPTION,
            JOptionPane.QUESTION_MESSAGE,
            null,
            options,
            null
         );

         frame.dispose();
         System.setProperty("java.awt.headless", "true");
      }

      if (option == JOptionPane.CLOSED_OPTION) {
         LOGGER.info("Modal closed, skipping injection...");
         return;
      }

      if (option == JOptionPane.CANCEL_OPTION) {
         injectRenderdoc();
         return;
      }

      launchViaNgfx(option);
   }

   private void injectRenderdoc() {
      LOGGER.info("Injecting Renderdoc...");
      try {
         if (IS_LINUX) {
            String renderdocPath = RenderdocLocator.findRenderdocSo();
            if (renderdocPath == null) {
               LOGGER.error("Renderdoc library not found. Checked standard system paths.");
               LOGGER.error("Set -Drenderdoc.path=<path> or RENDERDOC_PATH env var to your RenderDoc install directory or librenderdoc.so path.");
               return;
            }
            LOGGER.info("Found Renderdoc at: {}", renderdocPath);

            if (isLibraryLoaded("librenderdoc")) {
               LOGGER.info("Renderdoc is already loaded, no re-exec needed.");
               return;
            }

            Map<String, String> env = new HashMap<>();
            env.put(RENDERDOC_MARKER_ENV, "1");

            if (reExecWithPreload(renderdocPath, env)) {
               LOGGER.error("Re-exec with LD_PRELOAD failed. RenderDoc cannot be injected.");
               LOGGER.error("Try launching the game manually with: LD_PRELOAD={} <command>", renderdocPath);
            }
         } else {
            String renderdocDll = RenderdocLocator.findRenderdocDll();
            if (renderdocDll == null) {
               LOGGER.error("Renderdoc installation not found in common paths.");
               LOGGER.error("Set -Drenderdoc.path=<path> or RENDERDOC_PATH env var to your RenderDoc install directory.");
               return;
            }
            LOGGER.info("Found Renderdoc installation at: {}", renderdocDll);
            System.load(renderdocDll);
            LOGGER.info("Renderdoc loaded successfully.");
         }
      } catch (UnsatisfiedLinkError e) {
         LOGGER.error("Failed to load Renderdoc: ", e);
      }
   }

   private void launchViaNgfx(int option) {
      String activity = (option == JOptionPane.YES_OPTION) ? "GPU Trace Profiler" : "Frame Debugger";
      LOGGER.info("Launching game via ngfx CLI for {}...", activity);

      Path ngfx = NgfxLocator.findNgfxExecutable();
      if (ngfx == null) {
         LOGGER.error("NSight Graphics ngfx executable not found.");
         if (IS_LINUX) {
            LOGGER.error("Expected at: ~/nvidia/NVIDIA-Nsight-Graphics-*/host/linux-desktop-nomad-x64/ngfx");
         } else {
            LOGGER.error("Expected in: Program Files/NVIDIA Corporation/Nsight Graphics */host/windows-desktop-nomad-x64/ngfx.exe");
         }
         return;
      }
      LOGGER.info("Found ngfx at: {}", ngfx);

      String exe;
      try {
         exe = getCurrentExe();
      } catch (IOException e) {
         LOGGER.error("Failed to read current process exe: ", e);
         return;
      }

      String workDir = System.getProperty("user.dir");
      String platform = IS_LINUX ? "Linux (x86_64)" : "Windows";

      List<String> cmd = new ArrayList<>();
      cmd.add(ngfx.toAbsolutePath().toString());
      cmd.add("--activity=" + activity);
      cmd.add("--platform=" + platform);
      cmd.add("--exe=" + exe);

      Path argFile = null;
      try {
         List<String> rawArgs = readCurrentArgsList();
         if (!rawArgs.isEmpty()) {
            List<String> javaArgs = filterJavaArgs(rawArgs);
            argFile = writeArgFile(javaArgs);
            LOGGER.info("Wrote {} args to argfile: {}", javaArgs.size(), argFile);
            cmd.add("--args=@" + argFile.toAbsolutePath());
         }
      } catch (IOException e) {
         LOGGER.error("Failed to create argfile, falling back to inline args", e);
         try {
            String argsString = getCurrentArgsString();
            argsString = stripTheseusFromArgsString(argsString);
            if (!argsString.isEmpty()) {
               if (IS_WINDOWS) {
                  argsString = argsString.replace("\"", "\\\"");
               }
               cmd.add("--args=" + argsString);
            }
         } catch (IOException e2) {
            LOGGER.error("Failed to read current args: ", e2);
            return;
         }
      }

      cmd.add("--dir=" + workDir);
      cmd.add("--env=" + NSIGHT_MARKER_ENV + "=1;");
      cmd.add("--launch-detached");
      if (activity.equals("GPU Trace Profiler")) {
         cmd.add("--start-after-hotkey");
      }

      LOGGER.info("Running ngfx: {}", censorArgList(cmd));

      try {
         ProcessBuilder pb = new ProcessBuilder(cmd);
         pb.redirectErrorStream(true);
         Process process = pb.start();

         try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
               if (!line.trim().isEmpty()) {
                  LOGGER.info("{}", line);
               }
            }
         }

         int exitCode = process.waitFor();

         if (exitCode != 0) {
            LOGGER.error("ngfx exited with code {}. Check that NSight Graphics is installed correctly.", exitCode);
            return;
         }

         LOGGER.info("Game re-launched via ngfx (exit code 0). Terminating current process.");
         System.exit(0);
      } catch (IOException e) {
         LOGGER.error("Failed to run ngfx: ", e);
      } catch (InterruptedException e) {
         Thread.currentThread().interrupt();
         LOGGER.error("Interrupted while waiting for ngfx: ", e);
      } finally {
         if (argFile != null) {
            try {
               Files.deleteIfExists(argFile);
               LOGGER.info("Cleaned up argfile: {}", argFile);
            } catch (IOException e) {
               LOGGER.warn("Failed to clean up argfile: {}", argFile, e);
            }
         }
      }
   }

   static List<String> filterJavaArgs(List<String> args) {
      List<String> filtered = new ArrayList<>();
      for (int i = 0; i < args.size(); i++) {
         String arg = args.get(i);

         if (arg.matches("-javaagent:.*theseus\\.jar.*")) {
            LOGGER.info("Stripping theseus javaagent: {}", arg);
            continue;
         }
         if (arg.equals("com.modrinth.theseus.MinecraftLaunch")) {
            LOGGER.info("Stripping theseus main class");
            continue;
         }
         if (arg.startsWith("-Dmodrinth.internal.")) {
            LOGGER.info("Stripping Modrinth property: {}", arg);
            continue;
         }

         if ((arg.equals("-cp") || arg.equals("-classpath")) && i + 1 < args.size()) {
            filtered.add(arg);
            i++;
            String cp = args.get(i);
            String separator = IS_WINDOWS ? ";" : ":";
            String cleaned = Arrays.stream(cp.split(IS_WINDOWS ? ";" : ":"))
               .filter(entry -> !entry.contains("theseus.jar"))
               .collect(Collectors.joining(separator));
            if (!cleaned.equals(cp)) {
               LOGGER.info("Stripped theseus.jar from classpath");
            }
            filtered.add(cleaned);
            continue;
         }

         filtered.add(arg);
      }
      return filtered;
   }

   static Path writeArgFile(List<String> args) throws IOException {
      Path argFile = Files.createTempFile("gfx-debuggers-", ".args");
      try (BufferedWriter writer = Files.newBufferedWriter(argFile)) {
         for (String arg : args) {
            writer.write(quoteForArgFile(arg));
            writer.newLine();
         }
      }
      return argFile;
   }

   static String quoteForArgFile(String arg) {
      if (arg.isEmpty()) {
         return "\"\"";
      }
      boolean needsQuoting = false;
      for (int i = 0; i < arg.length(); i++) {
         char c = arg.charAt(i);
         if (c == ' ' || c == '\t' || c == '\n' || c == '\r' || c == '"' || c == '\'' || c == '\\' || c == '#') {
            needsQuoting = true;
            break;
         }
      }
      if (!needsQuoting) {
         return arg;
      }
      StringBuilder sb = new StringBuilder(arg.length() + 8);
      sb.append('"');
      for (int i = 0; i < arg.length(); i++) {
         char c = arg.charAt(i);
         if (c == '\\' || c == '"') {
            sb.append('\\');
         }
         sb.append(c);
      }
      sb.append('"');
      return sb.toString();
   }

   static String stripTheseusFromArgsString(String argsString) {
      argsString = argsString.replaceAll("-javaagent:[^\\s]+theseus\\.jar", "");
      argsString = argsString.replaceAll("com\\.modrinth\\.theseus\\.MinecraftLaunch", "");
      argsString = argsString.replaceAll("-Dmodrinth\\.internal\\.[^\\s]+", "");
      argsString = argsString.replaceAll("[^\\s:;]*theseus\\.jar[;:]?", "");
      argsString = argsString.replaceAll("::+", ":").replaceAll(";;+", ";");
      argsString = argsString.replaceAll("(-cp\\s+|--classpath\\s+)[;:]", "$1");
      argsString = argsString.replaceAll("  +", " ").trim();
      return argsString;
   }
}
