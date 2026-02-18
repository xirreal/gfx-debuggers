package dev.xirreal;

import static dev.xirreal.DebuggerPicker.DebuggerSelection;
import static dev.xirreal.PlatformUtils.*;

import java.awt.*;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import javax.swing.*;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.entrypoint.PreLaunchEntrypoint;
import org.lwjgl.util.tinyfd.TinyFileDialogs;
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
         LOGGER.error("Unsupported OS: " + System.getProperty("os.name"));
         return;
      }
      String arch = System.getProperty("os.arch").toLowerCase();
      if (!arch.contains("64")) {
         LOGGER.error("Unsupported architecture: " + arch);
         return;
      }

      if (System.getenv(RENDERDOC_MARKER_ENV) != null) {
         LOGGER.info("Process relaunched with Renderdoc marker. Checking if library is loaded...");
         if (isLibraryLoaded("librenderdoc")) {
            LOGGER.info("Renderdoc library is loaded. Continuing with normal launch.");
         } else {
            LOGGER.error("Renderdoc marker environment variable is set but library is not loaded. Something went wrong with the injection.");
            throw new IllegalStateException("Renderdoc injection failed");
         }
         return;
      } else if (System.getenv(NSIGHT_MARKER_ENV) != null) {
         LOGGER.info("Process relaunched with NSight Graphics marker. Continuing with normal launch.");
         return;
      }

      ProcessHandle processHandle = ProcessHandle.current();
      String javaExecutable = processHandle.info().command().orElse("java");
      List<String> jvmArgs = ManagementFactory.getRuntimeMXBean().getInputArguments();

      List<String> fullArgs = new ArrayList<>();
      fullArgs.addAll(jvmArgs);
      if (System.getProperty("java.library.path") != null) {
         fullArgs.add("-Djava.library.path=" + System.getProperty("java.library.path"));
      }
      fullArgs.add("-cp");
      fullArgs.add(System.getProperty("java.class.path"));

      // Bypass launcher shims and launch fabric directly as god intended
      fullArgs.add("net.fabricmc.loader.impl.launch.knot.KnotClient");

      String[] args = FabricLoader.getInstance().getLaunchArguments(false);
      fullArgs.addAll(Arrays.asList(args));

      DebuggerSelection choice = DebuggerSelection.NONE;

      String optionString = System.getProperty("debugger");
      if (optionString != null) {
         if (optionString.equalsIgnoreCase("renderdoc")) {
            choice = DebuggerSelection.RENDERDOC;
         } else if (optionString.equalsIgnoreCase("nsight-gpu")) {
            choice = DebuggerSelection.GPU_TRACE;
         } else if (optionString.equalsIgnoreCase("nsight-frame")) {
            choice = DebuggerSelection.FRAME_DEBUGGER;
         }
      }

      if (choice == DebuggerSelection.NONE) {
         String originalHeadless = System.getProperty("java.awt.headless");
         String originalAA = System.getProperty("awt.useSystemAAFontSettings");
         String originalAAText = System.getProperty("swing.aatext");

         try {
            System.setProperty("java.awt.headless", "false");
            System.setProperty("awt.useSystemAAFontSettings", "on");
            System.setProperty("swing.aatext", "true");

            DebuggerPicker picker = new DebuggerPicker();
            choice = picker.getSelection();
         } catch (Exception e) {
            LOGGER.error("Could not open Swing window. Falling back to command line selection.", e);
         } finally {
            if (originalHeadless != null) {
               System.setProperty("java.awt.headless", originalHeadless);
            } else {
               System.clearProperty("java.awt.headless");
            }
            if (originalAA != null) {
               System.setProperty("awt.useSystemAAFontSettings", originalAA);
            } else {
               System.clearProperty("awt.useSystemAAFontSettings");
            }
            if (originalAAText != null) {
               System.setProperty("swing.aatext", originalAAText);
            } else {
               System.clearProperty("swing.aatext");
            }
         }
      }

      if (choice == DebuggerSelection.NONE) {
         LOGGER.warn("Injection skipped! No debugger will be injected and the game will launch normally.");
         return;
      } else if (choice == DebuggerSelection.RENDERDOC) {
         launchRenderdoc(javaExecutable, fullArgs);
      } else {
         launchViaNgfx(javaExecutable, fullArgs, choice);
      }
   }

   private void launchRenderdoc(String javaExecutable, List<String> args) {
      LOGGER.info("Injecting Renderdoc...");
      try {
         if (IS_LINUX) {
            String renderdocPath = RenderdocLocator.findRenderdocSo();
            if (renderdocPath == null) {
               LOGGER.error("Renderdoc library not found. Checked standard system paths.");
               LOGGER.error("Set -Drenderdoc.path=<path> or RENDERDOC_PATH env var to your RenderDoc install directory or librenderdoc.so path.");
               throw new IllegalStateException("Renderdoc library not found");
            }
            LOGGER.info("Found Renderdoc at: {}", renderdocPath);

            if (isLibraryLoaded("librenderdoc")) {
               LOGGER.info("Renderdoc is already loaded, no re-exec needed.");
               return;
            }

            if (!relaunchWithExtraLD_PRELOAD(javaExecutable, args, renderdocPath, RENDERDOC_MARKER_ENV)) {
               LOGGER.error("Re-exec with LD_PRELOAD failed.");
               LOGGER.error("Try launching the game manually with this environment variable set: LD_PRELOAD={}", renderdocPath);
               throw new IllegalStateException("Failed to relaunch with Renderdoc");
            }
         } else {
            String renderdocDll = RenderdocLocator.findRenderdocDll();
            if (renderdocDll == null) {
               LOGGER.error("Renderdoc installation not found in common paths.");
               LOGGER.error("Set -Drenderdoc.path=<path> or RENDERDOC_PATH env var to your RenderDoc install directory.");
               throw new IllegalStateException("Renderdoc DLL not found");
            }
            LOGGER.info("Found Renderdoc shared library at: {}", renderdocDll);
            System.load(renderdocDll);
            LOGGER.info("Renderdoc loaded successfully.");
         }
      } catch (Exception e) {
         LOGGER.error("Failed to launch with Renderdoc: ", e);
         throw new IllegalStateException("Failed to launch with Renderdoc", e);
      }
   }

   private void launchViaNgfx(String exe, List<String> args, DebuggerSelection activity) {
      LOGGER.info("Launching game via ngfx CLI for {}...", activity.name());

      Path ngfx = NgfxLocator.findNgfxExecutable();
      if (ngfx == null) {
         LOGGER.error("NSight Graphics ngfx executable not found.");
         if (IS_LINUX) {
            LOGGER.error("Expected at: ~/nvidia/NVIDIA-Nsight-Graphics-*/host/linux-desktop-nomad-x64/ngfx");
         } else {
            LOGGER.error("Expected in: Program Files/NVIDIA Corporation/Nsight Graphics */host/windows-desktop-nomad-x64/ngfx.exe");
         }
         throw new IllegalStateException("ngfx executable not found");
      }

      LOGGER.info("Found ngfx at: {}", ngfx);

      String workDir = System.getProperty("user.dir");

      List<String> cmd = new ArrayList<>();
      cmd.add(ngfx.toAbsolutePath().toString());
      cmd.add("--activity=" + (activity == DebuggerSelection.GPU_TRACE ? "GPU Trace Profiler" : "Frame Debugger"));
      cmd.add("--exe=" + exe);

      Path argFile = null;
      try {
         argFile = writeArgFile(args);
         cmd.add("--args=@" + argFile.toAbsolutePath());
      } catch (Exception e) {
         LOGGER.error("Failed to create argfile for ngfx: ", e);
         throw new IllegalStateException("Failed to create argfile for ngfx", e);
      }

      cmd.add("--dir=" + workDir);
      cmd.add("--env=" + NSIGHT_MARKER_ENV + "=1");
      cmd.add("--launch-detached");
      if (activity == DebuggerSelection.GPU_TRACE) {
         cmd.add("--limit-to-frames");
         cmd.add("5");
         //cmd.add("--multi-pass-metrics");
         cmd.add("--start-after-hotkey");
      }

      LOGGER.info("Running ngfx with command: {}", String.join(" ", cmd));

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
            throw new IllegalStateException("ngfx exited with code " + exitCode);
         }

         LOGGER.info("Game re-launched via ngfx (exit code 0). Terminating current process.");
         System.exit(0);
      } catch (IOException e) {
         LOGGER.error("Failed to run ngfx: ", e);
         throw new IllegalStateException("Failed to run ngfx", e);
      } catch (InterruptedException e) {
         Thread.currentThread().interrupt();
         LOGGER.error("Interrupted while waiting for ngfx: ", e);
         throw new IllegalStateException("Interrupted while waiting for ngfx", e);
      } catch (Exception e) {
         LOGGER.error("Failed to launch with NSight Graphics: ", e);
         throw new IllegalStateException("Failed to launch with NSight Graphics", e);
      } finally {
         if (argFile != null) {
            try {
               Files.deleteIfExists(argFile);
            } catch (IOException e) {
               LOGGER.error("Failed to delete temporary arg file: {}", argFile, e);
            }
         }
      }
   }
}
