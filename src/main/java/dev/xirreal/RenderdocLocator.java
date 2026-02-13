package dev.xirreal;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

public final class RenderdocLocator {

   private RenderdocLocator() {}

   public static String findRenderdocSo() {
      String propPath = System.getProperty("renderdoc.path");
      if (propPath != null) {
         File propFile = new File(propPath);
         if (propFile.isFile()) {
            return propFile.getAbsolutePath();
         }

         File inDir = new File(propPath, "librenderdoc.so");
         if (inDir.isFile()) {
            return inDir.getAbsolutePath();
         }
         GfxDebuggers.LOGGER.warn("renderdoc.path system property set to '{}' but librenderdoc.so was not found there.", propPath);
      }

      String renderdocPath = System.getenv("RENDERDOC_PATH");
      if (renderdocPath != null) {
         File rpFile = new File(renderdocPath);
         if (rpFile.isFile()) {
            return rpFile.getAbsolutePath();
         }
         File rpInDir = new File(renderdocPath, "librenderdoc.so");
         if (rpInDir.isFile()) {
            return rpInDir.getAbsolutePath();
         }
         GfxDebuggers.LOGGER.warn("RENDERDOC_PATH env var set to '{}' but librenderdoc.so was not found there.", renderdocPath);
      }

      String envPath = System.getenv("RENDERDOC_LIB_PATH");
      if (envPath != null && new File(envPath).isFile()) {
         return envPath;
      }

      String[] searchPaths = {
         "/usr/lib64/renderdoc/librenderdoc.so",
         "/usr/lib64/librenderdoc.so",
         "/usr/lib/librenderdoc.so",
         "/usr/lib/x86_64-linux-gnu/librenderdoc.so",
         "/usr/lib/renderdoc/librenderdoc.so",
         "/usr/local/lib/librenderdoc.so",
         "/usr/local/lib64/librenderdoc.so",
      };

      for (String path : searchPaths) {
         if (new File(path).exists()) {
            return path;
         }
      }

      String home = System.getProperty("user.home");
      Path homePath = Paths.get(home, ".local", "lib", "librenderdoc.so");
      if (Files.exists(homePath)) {
         return homePath.toAbsolutePath().toString();
      }

      return null;
   }

   public static String findRenderdocDll() {
      String propPath = System.getProperty("renderdoc.path");
      if (propPath != null) {
         String resolved = resolveRenderdocDll(propPath);
         if (resolved != null) return resolved;
         GfxDebuggers.LOGGER.warn("renderdoc.path system property set to '{}' but renderdoc.dll was not found there.", propPath);
      }

      String envPath = System.getenv("RENDERDOC_PATH");
      if (envPath != null) {
         String resolved = resolveRenderdocDll(envPath);
         if (resolved != null) return resolved;
         GfxDebuggers.LOGGER.warn("RENDERDOC_PATH env var set to '{}' but renderdoc.dll was not found there.", envPath);
      }

      List<String> roots = new ArrayList<>();
      String programFiles = System.getenv("ProgramFiles");
      if (programFiles != null) roots.add(programFiles);
      String programFilesX86 = System.getenv("ProgramFiles(x86)");
      if (programFilesX86 != null) roots.add(programFilesX86);

      for (String root : roots) {
         String direct = resolveRenderdocDll(Paths.get(root, "RenderDoc").toString());
         if (direct != null) return direct;

         Path rootPath = Paths.get(root);
         if (Files.isDirectory(rootPath)) {
            try (Stream<Path> dirs = Files.list(rootPath)) {
               Path found = dirs
                  .filter(Files::isDirectory)
                  .filter(p -> {
                     String name = p.getFileName().toString().toLowerCase();
                     return name.startsWith("renderdoc");
                  })
                  .sorted(Comparator.comparing(p -> p.getFileName().toString(), Comparator.reverseOrder()))
                  .filter(p -> Files.exists(p.resolve("renderdoc.dll")))
                  .findFirst()
                  .orElse(null);
               if (found != null) {
                  return found.resolve("renderdoc.dll").toAbsolutePath().toString();
               }
            } catch (IOException ignored) {}
         }
      }

      String home = System.getProperty("user.home");
      if (home != null) {
         String homeResult = resolveRenderdocDll(Paths.get(home, "RenderDoc").toString());
         if (homeResult != null) return homeResult;
      }

      return null;
   }

   private static String resolveRenderdocDll(String path) {
      File f = new File(path);
      if (f.isFile() && f.getName().equalsIgnoreCase("renderdoc.dll")) {
         return f.getAbsolutePath();
      }
      if (f.isDirectory()) {
         File dll = new File(f, "renderdoc.dll");
         if (dll.isFile()) {
            return dll.getAbsolutePath();
         }
      }
      return null;
   }
}
