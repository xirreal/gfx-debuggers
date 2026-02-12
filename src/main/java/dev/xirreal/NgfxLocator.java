package dev.xirreal;

import static dev.xirreal.PlatformUtils.IS_LINUX;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.stream.Stream;

public class NgfxLocator {

   private static final String NGFX_EXE = IS_LINUX ? "ngfx" : "ngfx.exe";
   private static final String HOST_DIR = IS_LINUX ? "linux-desktop-nomad-x64" : "windows-desktop-nomad-x64";

   public static Path findNgfxExecutable() {
      String propPath = System.getProperty("ngfx.path");
      if (propPath != null) {
         Path resolved = resolveNgfx(propPath);
         if (resolved != null) {
            return resolved;
         }
         GfxDebuggers.LOGGER.warn("ngfx.path system property set to '{}' but ngfx executable was not found there.", propPath);
      }

      String envPath = System.getenv("NGFX_PATH");
      if (envPath != null) {
         Path resolved = resolveNgfx(envPath);
         if (resolved != null) {
            return resolved;
         }
         GfxDebuggers.LOGGER.warn("NGFX_PATH env var set to '{}' but ngfx executable was not found there.", envPath);
      }

      return IS_LINUX ? findNgfxLinux() : findNgfxWindows();
   }

   private static Path resolveNgfx(String pathStr) {
      Path p = Paths.get(pathStr);

      if (Files.isExecutable(p) && !Files.isDirectory(p)) {
         return p;
      }

      Path inHost = p.resolve("host").resolve(HOST_DIR).resolve(NGFX_EXE);
      if (Files.isExecutable(inHost)) {
         return inHost;
      }

      Path direct = p.resolve(NGFX_EXE);
      if (Files.isExecutable(direct)) {
         return direct;
      }

      return null;
   }

   private static Path findNgfxLinux() {
      String home = System.getProperty("user.home");
      Path nsightBase = Paths.get(home, "nvidia");
      if (!Files.isDirectory(nsightBase)) {
         return null;
      }

      try (Stream<Path> dirs = Files.list(nsightBase)) {
         return dirs
            .filter(Files::isDirectory)
            .filter(p -> p.getFileName().toString().startsWith("NVIDIA-Nsight-Graphics-"))
            .max(Comparator.comparing(p -> p.getFileName().toString()))
            .map(p -> p.resolve("host/linux-desktop-nomad-x64/ngfx"))
            .filter(Files::isExecutable)
            .orElse(null);
      } catch (IOException e) {
         GfxDebuggers.LOGGER.error("Failed to search for NSight installation: ", e);
         return null;
      }
   }

   private static Path findNgfxWindows() {
      String programFiles = System.getenv("ProgramFiles");
      if (programFiles == null) {
         programFiles = "C:\\Program Files";
      }

      Path nvCorp = Paths.get(programFiles, "NVIDIA Corporation");
      if (Files.isDirectory(nvCorp)) {
         Path found = searchNgfxWindows(nvCorp);
         if (found != null) return found;
      }

      String programFilesX86 = System.getenv("ProgramFiles(x86)");
      if (programFilesX86 != null) {
         Path nvCorpX86 = Paths.get(programFilesX86, "NVIDIA Corporation");
         if (Files.isDirectory(nvCorpX86)) {
            Path found = searchNgfxWindows(nvCorpX86);
            if (found != null) return found;
         }
      }

      return null;
   }

   private static Path searchNgfxWindows(Path nvCorp) {
      try (Stream<Path> dirs = Files.list(nvCorp)) {
         return dirs
            .filter(Files::isDirectory)
            .filter(p -> p.getFileName().toString().startsWith("Nsight Graphics "))
            .max(Comparator.comparing(p -> p.getFileName().toString()))
            .map(p -> p.resolve("host/windows-desktop-nomad-x64/ngfx.exe"))
            .filter(Files::exists)
            .orElse(null);
      } catch (IOException e) {
         GfxDebuggers.LOGGER.error("Failed to search for NSight installation under {}: ", nvCorp, e);
         return null;
      }
   }
}
