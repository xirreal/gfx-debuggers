package dev.xirreal;

import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.StringArray;
import java.io.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.List;
import java.util.Map;

public class PlatformUtils {

   public static final boolean IS_LINUX = System.getProperty("os.name").toLowerCase().contains("linux");
   public static final boolean IS_WINDOWS = System.getProperty("os.name").toLowerCase().contains("win");

   public interface LibC extends Library {
      LibC INSTANCE = Native.load("c", LibC.class);
      int setenv(String name, String value, int overwrite);
      int execv(String pathname, StringArray argv);
   }

   public static boolean isLibraryLoaded(String needle) {
      try {
         String maps = Files.readString(Paths.get("/proc/self/maps"));
         return maps.contains(needle);
      } catch (IOException e) {
         return false;
      }
   }

   public static boolean relaunchWithExtraLD_PRELOAD(String exe, List<String> args, String preloadLib, String markerEnvVar) {
      String currentPreload = System.getenv("LD_PRELOAD");
      String newPreload;
      if (currentPreload != null && !currentPreload.isEmpty()) {
         newPreload = preloadLib + ":" + currentPreload;
      } else {
         newPreload = preloadLib;
      }

      LibC.INSTANCE.setenv("LD_PRELOAD", newPreload, 1);
      LibC.INSTANCE.setenv(markerEnvVar, "1", 1);

      GfxDebuggers.LOGGER.info("Replacing process...");

      StringArray argv = new StringArray(args.toArray(new String[0]));
      LibC.INSTANCE.execv(exe, argv);

      int errno = Native.getLastError();
      GfxDebuggers.LOGGER.error("execv failed with errno {}", errno);
      return false;
   }

   static Path writeArgFile(List<String> args) throws IOException {
      Path argFile = Files.createTempFile("gfx-debuggers-", ".args");

      argFile.toFile().deleteOnExit();

      try (BufferedWriter writer = Files.newBufferedWriter(argFile, StandardCharsets.UTF_8)) {
         for (String arg : args) {
            writer.write(quoteForArgFile(arg));
            writer.newLine();
         }
      }
      return argFile;
   }

   // https://docs.oracle.com/en/java/javase/17/docs/specs/man/java.html#java-command-line-argument-files
   static String quoteForArgFile(String arg) {
      if (arg.isEmpty()) {
         return "\"\"";
      }

      boolean needsQuoting = false;
      for (int i = 0; i < arg.length(); i++) {
         char c = arg.charAt(i);
         if (Character.isWhitespace(c) || c == '"' || c == '\'' || c == '\\' || c == '#') {
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
}
