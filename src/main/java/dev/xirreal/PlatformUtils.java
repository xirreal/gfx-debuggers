package dev.xirreal;

import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.StringArray;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class PlatformUtils {

   public static final boolean IS_LINUX = System.getProperty("os.name").toLowerCase().contains("linux");
   public static final boolean IS_WINDOWS = System.getProperty("os.name").toLowerCase().contains("win");

   public interface LibC extends Library {
      LibC INSTANCE = Native.load("c", LibC.class);
      int setenv(String name, String value, int overwrite);
      int execv(String pathname, StringArray argv);
   }

   public interface Kernel32 extends Library {
      Pointer GetCommandLineW();
   }

   public static final Kernel32 KERNEL32 = IS_WINDOWS ? Native.load("kernel32", Kernel32.class) : null;

   public static boolean isLibraryLoaded(String needle) {
      try {
         String maps = Files.readString(Paths.get("/proc/self/maps"));
         return maps.contains(needle);
      } catch (IOException e) {
         return false;
      }
   }

   public static List<String> readCurrentCmdline() throws IOException {
      byte[] cmdlineBytes = Files.readAllBytes(Paths.get("/proc/self/cmdline"));
      List<String> args = new ArrayList<>();
      int start = 0;
      for (int i = 0; i < cmdlineBytes.length; i++) {
         if (cmdlineBytes[i] == 0) {
            if (i > start) {
               args.add(new String(cmdlineBytes, start, i - start));
            }
            start = i + 1;
         }
      }
      if (start < cmdlineBytes.length) {
         args.add(new String(cmdlineBytes, start, cmdlineBytes.length - start));
      }
      return args;
   }

   public static boolean reExecWithPreload(String preloadLib, Map<String, String> extraEnv) {
      try {
         List<String> args = readCurrentCmdline();
         if (args.isEmpty()) {
            GfxDebuggers.LOGGER.error("Failed to read /proc/self/cmdline: no arguments found");
            return true;
         }

         String exe = Paths.get("/proc/self/exe").toRealPath().toString();

         String currentPreload = System.getenv("LD_PRELOAD");
         String newPreload;
         if (currentPreload != null && !currentPreload.isEmpty()) {
            newPreload = preloadLib + ":" + currentPreload;
         } else {
            newPreload = preloadLib;
         }

         LibC.INSTANCE.setenv("LD_PRELOAD", newPreload, 1);

         if (extraEnv != null) {
            for (Map.Entry<String, String> entry : extraEnv.entrySet()) {
               LibC.INSTANCE.setenv(entry.getKey(), entry.getValue(), 1);
            }
         }

         GfxDebuggers.LOGGER.info("Re-executing process with LD_PRELOAD={}", newPreload);
         GfxDebuggers.LOGGER.info("Executable: {}", exe);
         GfxDebuggers.LOGGER.info("Arguments: {}", censorArgList(args));

         StringArray argv = new StringArray(args.toArray(new String[0]));
         LibC.INSTANCE.execv(exe, argv);

         int errno = Native.getLastError();
         GfxDebuggers.LOGGER.error("execv failed with errno {}", errno);
         return true;
      } catch (IOException e) {
         GfxDebuggers.LOGGER.error("Failed to re-exec with LD_PRELOAD: ", e);
         return true;
      }
   }

   public static List<String> readCurrentArgsList() throws IOException {
      if (IS_LINUX) {
         List<String> cmdline = readCurrentCmdline();
         if (cmdline.size() <= 1) return List.of();
         return new ArrayList<>(cmdline.subList(1, cmdline.size()));
      } else {
         String fullCmd = KERNEL32.GetCommandLineW().getWideString(0);
         List<String> all = parseWindowsCommandLine(fullCmd);
         if (all.size() <= 1) return List.of();
         return new ArrayList<>(all.subList(1, all.size()));
      }
   }

   public static List<String> parseWindowsCommandLine(String commandLine) {
      List<String> args = new ArrayList<>();
      if (commandLine == null || commandLine.isEmpty()) return args;

      StringBuilder current = new StringBuilder();
      boolean inQuotes = false;
      int i = 0;

      while (i < commandLine.length()) {
         char c = commandLine.charAt(i);

         if (c == '\\') {
            int numBackslashes = 0;
            while (i < commandLine.length() && commandLine.charAt(i) == '\\') {
               numBackslashes++;
               i++;
            }
            if (i < commandLine.length() && commandLine.charAt(i) == '"') {
               for (int j = 0; j < numBackslashes / 2; j++) {
                  current.append('\\');
               }
               if (numBackslashes % 2 == 1) {
                  current.append('"');
                  i++;
               }
            } else {
               for (int j = 0; j < numBackslashes; j++) {
                  current.append('\\');
               }
            }
         } else if (c == '"') {
            inQuotes = !inQuotes;
            i++;
         } else if ((c == ' ' || c == '\t') && !inQuotes) {
            if (current.length() > 0) {
               args.add(current.toString());
               current.setLength(0);
            }
            i++;
         } else {
            current.append(c);
            i++;
         }
      }
      if (current.length() > 0) {
         args.add(current.toString());
      }
      return args;
   }

   public static String getCurrentExe() throws IOException {
      if (IS_LINUX) {
         return Paths.get("/proc/self/exe").toRealPath().toString();
      } else {
         return ProcessHandle.current().info().command().orElse(Paths.get(System.getProperty("java.home"), "bin", "java.exe").toString());
      }
   }

   public static String getCurrentArgsString() throws IOException {
      if (IS_LINUX) {
         List<String> cmdline = readCurrentCmdline();
         if (cmdline.size() <= 1) return "";
         StringBuilder sb = new StringBuilder();
         for (int i = 1; i < cmdline.size(); i++) {
            if (i > 1) sb.append(' ');
            sb.append(shellQuote(cmdline.get(i)));
         }
         return sb.toString();
      } else {
         String fullCmd = KERNEL32.GetCommandLineW().getWideString(0);
         return stripExeFromCommandLine(fullCmd);
      }
   }

   public static String shellQuote(String arg) {
      if (arg.isEmpty()) return "''";
      if (arg.matches("[a-zA-Z0-9._/=:@%,+-]+")) return arg;
      return "'" + arg.replace("'", "'\\''") + "'";
   }

   private static final Set<String> SENSITIVE_ARGS = new HashSet<>(Arrays.asList("--accessToken", "--uuid", "--username", "--xuid", "--clientId"));

   public static String censorArgList(List<String> args) {
      return args
         .stream()
         .map(arg -> {
            for (String sensitive : SENSITIVE_ARGS) {
               if (arg.startsWith(sensitive + "=")) {
                  return sensitive + "=<censored>";
               }
               if (arg.startsWith(sensitive + " ")) {
                  return sensitive + " <censored>";
               }
            }
            return arg;
         })
         .collect(Collectors.toList())
         .toString();
   }

   public static String stripExeFromCommandLine(String cmdLine) {
      if (cmdLine == null || cmdLine.isEmpty()) return "";
      String trimmed = cmdLine.trim();
      if (trimmed.startsWith("\"")) {
         int endQuote = trimmed.indexOf('"', 1);
         if (endQuote >= 0) {
            return trimmed.substring(endQuote + 1).trim();
         }
      }
      int space = trimmed.indexOf(' ');
      return space >= 0 ? trimmed.substring(space + 1).trim() : "";
   }
}
