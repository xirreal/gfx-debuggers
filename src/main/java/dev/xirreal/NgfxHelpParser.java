package dev.xirreal;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class NgfxHelpParser {

   private static final Pattern OPTION_LINE = Pattern.compile("^\\s{2}(--[a-zA-Z0-9][a-zA-Z0-9_-]*)(?:\\s+arg)?(?:\\s+\\(=(.+?)\\))?\\s*(.*)$");
   private static final Pattern SECTION_HEADER = Pattern.compile("^(\\S.+?):\\s*$");

   private NgfxHelpParser() {}

   public static NgfxHelpInfo parse(Path ngfxExe) {
      List<String> lines = runHelpAll(ngfxExe);
      if (lines == null) {
         return new NgfxHelpInfo(List.of(), List.of(), List.of());
      }

      List<String> platforms = parsePlatforms(lines);
      List<String> activities = parseActivities(lines);
      List<NgfxOption> gpuTraceOptions = parseActivityOptions(lines, "GPU Trace Profiler activity options");

      return new NgfxHelpInfo(platforms, activities, gpuTraceOptions);
   }

   private static List<String> runHelpAll(Path ngfxExe) {
      try {
         ProcessBuilder pb = new ProcessBuilder(ngfxExe.toAbsolutePath().toString(), "--help-all");
         pb.redirectErrorStream(true);
         Process process = pb.start();

         List<String> lines = new ArrayList<>();
         try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
               lines.add(line);
            }
         }

         if (!process.waitFor(5, TimeUnit.SECONDS)) {
            process.destroyForcibly();
            GfxDebuggers.LOGGER.warn("ngfx --help-all timed out");
            return null;
         }

         return lines;
      } catch (Exception e) {
         GfxDebuggers.LOGGER.warn("Failed to run ngfx --help-all: {}", e.getMessage());
         return null;
      }
   }

   private static List<String> parsePlatforms(List<String> lines) {
      List<String> platforms = new ArrayList<>();
      boolean inPlatformDesc = false;

      for (int i = 0; i < lines.size(); i++) {
         String line = lines.get(i);

         if (line.stripLeading().startsWith("--platform ")) {
            inPlatformDesc = true;
            continue;
         }

         if (inPlatformDesc) {
            String stripped = line.strip();
            if (stripped.isEmpty()) {
               continue;
            }
            if (line.startsWith("  --") || SECTION_HEADER.matcher(line).matches()) {
               break;
            }
            if (!stripped.startsWith("Target platform") && !stripped.startsWith("of:")) {
               platforms.add(stripped);
            }
         }
      }

      return platforms;
   }

   private static List<String> parseActivities(List<String> lines) {
      List<String> activities = new ArrayList<>();
      boolean inActivityDesc = false;

      for (int i = 0; i < lines.size(); i++) {
         String line = lines.get(i);

         if (line.stripLeading().startsWith("--activity ")) {
            inActivityDesc = true;
            continue;
         }

         if (inActivityDesc) {
            String stripped = line.strip();
            if (stripped.isEmpty()) {
               continue;
            }
            if (line.startsWith("  --") || SECTION_HEADER.matcher(line).matches()) {
               break;
            }
            if (!stripped.startsWith("Target activity") && !stripped.startsWith("of:")) {
               activities.add(stripped);
            }
         }
      }

      return activities;
   }

   private static List<NgfxOption> parseActivityOptions(List<String> lines, String sectionName) {
      List<NgfxOption> options = new ArrayList<>();

      int sectionStart = -1;
      for (int i = 0; i < lines.size(); i++) {
         String line = lines.get(i);
         if (line.strip().equalsIgnoreCase(sectionName + ":")) {
            sectionStart = i + 1;
            break;
         }
      }

      if (sectionStart < 0) {
         return options;
      }

      int i = sectionStart;
      while (i < lines.size()) {
         String line = lines.get(i);

         if (!line.isEmpty() && !line.startsWith(" ") && SECTION_HEADER.matcher(line).matches()) {
            break;
         }

         Matcher m = OPTION_LINE.matcher(line);
         if (m.matches()) {
            String flag = m.group(1);
            String defaultVal = m.group(2);
            boolean takesValue = line.contains(flag + " arg");
            String descPart = m.group(3) != null ? m.group(3).strip() : "";

            List<String> descLines = new ArrayList<>();
            if (!descPart.isEmpty()) {
               descLines.add(descPart);
            }
            i++;
            while (i < lines.size()) {
               String next = lines.get(i);
               if (next.isEmpty()) {
                  i++;
                  continue;
               }
               if (next.startsWith("  --") || (!next.startsWith(" ") && SECTION_HEADER.matcher(next).matches())) {
                  break;
               }
               String stripped = next.strip();
               if (!stripped.isEmpty()) {
                  descLines.add(stripped);
               }
               i++;
            }

            String description = String.join(" ", descLines);
            boolean deprecated = description.toLowerCase().contains("deprecated");
            boolean booleanArg = takesValue && defaultVal != null && (defaultVal.equals("0") || defaultVal.equals("1"));
            List<String> choices = parseChoices(descLines);

            options.add(new NgfxOption(flag, takesValue, defaultVal, description, deprecated, booleanArg, choices));
         } else {
            i++;
         }
      }

      return options;
      }

      private static final Pattern DASH_CHOICE = Pattern.compile("^-\\s+(.+)$");

      private static List<String> parseChoices(List<String> descLines) {
      List<String> choices = new ArrayList<>();
      for (String line : descLines) {
         Matcher m = DASH_CHOICE.matcher(line);
         if (m.matches()) {
            choices.add(m.group(1).strip());
         }
      }
      return choices;
      }
      }
