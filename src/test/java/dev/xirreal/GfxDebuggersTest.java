package dev.xirreal;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class GfxDebuggersTest {

   // ── quoteForArgFile ──────────────────────────────────────────────────

   @Nested
   class QuoteForArgFile {

      @Test
      void emptyString() {
         assertEquals("\"\"", GfxDebuggers.quoteForArgFile(""));
      }

      @Test
      void simpleArgNoSpecialChars() {
         assertEquals("-Xmx4G", GfxDebuggers.quoteForArgFile("-Xmx4G"));
      }

      @Test
      void simpleClasspath() {
         assertEquals("net.minecraft.Main", GfxDebuggers.quoteForArgFile("net.minecraft.Main"));
      }

      @Test
      void flagWithEquals() {
         assertEquals("--version=1.20.4", GfxDebuggers.quoteForArgFile("--version=1.20.4"));
      }

      @Test
      void argWithSpaces() {
         assertEquals("\"hello world\"", GfxDebuggers.quoteForArgFile("hello world"));
      }

      @Test
      void argWithTab() {
         assertEquals("\"a\tb\"", GfxDebuggers.quoteForArgFile("a\tb"));
      }

      @Test
      void argWithNewline() {
         assertEquals("\"line1\nline2\"", GfxDebuggers.quoteForArgFile("line1\nline2"));
      }

      @Test
      void argWithCarriageReturn() {
         assertEquals("\"a\rb\"", GfxDebuggers.quoteForArgFile("a\rb"));
      }

      @Test
      void argWithDoubleQuotes() {
         // Quotes should be escaped with backslash
         assertEquals("\"say \\\"hi\\\"\"", GfxDebuggers.quoteForArgFile("say \"hi\""));
      }

      @Test
      void argWithSingleQuotes() {
         assertEquals("\"it's\"", GfxDebuggers.quoteForArgFile("it's"));
      }

      @Test
      void argWithBackslashes() {
         // Backslashes should be escaped with backslash
         assertEquals("\"C:\\\\Program Files\\\\java\"", GfxDebuggers.quoteForArgFile("C:\\Program Files\\java"));
      }

      @Test
      void argWithHash() {
         // Hash is a comment character in argfiles
         assertEquals("\"#not-a-comment\"", GfxDebuggers.quoteForArgFile("#not-a-comment"));
      }

      @Test
      void argWithBackslashAndQuote() {
         // Both need escaping
         assertEquals("\"a\\\\\\\"b\"", GfxDebuggers.quoteForArgFile("a\\\"b"));
      }

      @Test
      void argWithOnlyBackslashes() {
         assertEquals("\"\\\\\\\\\"", GfxDebuggers.quoteForArgFile("\\\\"));
      }

      @Test
      void argWithOnlyQuote() {
         assertEquals("\"\\\"\"", GfxDebuggers.quoteForArgFile("\""));
      }

      @Test
      void hyphenArgNotQuoted() {
         assertEquals("-verbose", GfxDebuggers.quoteForArgFile("-verbose"));
      }

      @Test
      void pathWithSlashesNotQuoted() {
         assertEquals("/usr/lib/jvm/java-17/bin/java", GfxDebuggers.quoteForArgFile("/usr/lib/jvm/java-17/bin/java"));
      }

      @Test
      void windowsPathWithoutSpacesIsQuoted() {
         // Backslash triggers quoting
         assertEquals("\"C:\\\\java\\\\bin\\\\java.exe\"", GfxDebuggers.quoteForArgFile("C:\\java\\bin\\java.exe"));
      }

      @Test
      void windowsPathWithSpaces() {
         assertEquals("\"C:\\\\Program Files\\\\Java\\\\bin\\\\java.exe\"", GfxDebuggers.quoteForArgFile("C:\\Program Files\\Java\\bin\\java.exe"));
      }
   }

   // ── filterJavaArgs ───────────────────────────────────────────────────

   @Nested
   class FilterJavaArgs {

      @Test
      void emptyList() {
         assertEquals(List.of(), GfxDebuggers.filterJavaArgs(List.of()));
      }

      @Test
      void normalArgsPassThrough() {
         List<String> args = List.of("-Xmx4G", "-Dfoo=bar", "net.minecraft.Main", "--username", "Player");
         assertEquals(args, GfxDebuggers.filterJavaArgs(args));
      }

      @Test
      void theseusJavaagentStripped() {
         List<String> args = new ArrayList<>(List.of("-javaagent:/path/to/theseus.jar", "-Xmx4G", "net.minecraft.Main"));
         List<String> result = GfxDebuggers.filterJavaArgs(args);
         assertEquals(List.of("-Xmx4G", "net.minecraft.Main"), result);
      }

      @Test
      void theseusJavaagentWithOptionsStripped() {
         List<String> args = new ArrayList<>(List.of("-javaagent:/path/to/theseus.jar=someopt", "-Xmx4G"));
         List<String> result = GfxDebuggers.filterJavaArgs(args);
         assertEquals(List.of("-Xmx4G"), result);
      }

      @Test
      void theseusMainClassStripped() {
         List<String> args = new ArrayList<>(List.of("-Xmx4G", "com.modrinth.theseus.MinecraftLaunch", "--username", "Player"));
         List<String> result = GfxDebuggers.filterJavaArgs(args);
         assertEquals(List.of("-Xmx4G", "--username", "Player"), result);
      }

      @Test
      void modrinthInternalPropertiesStripped() {
         List<String> args = new ArrayList<>(List.of("-Dmodrinth.internal.foo=bar", "-Dmodrinth.internal.baz=qux", "-Xmx4G", "-Dnormal.property=value"));
         List<String> result = GfxDebuggers.filterJavaArgs(args);
         assertEquals(List.of("-Xmx4G", "-Dnormal.property=value"), result);
      }

      @Test
      void classpathTheseusJarStrippedLinuxSeparator() {
         // On macOS (test host), IS_WINDOWS is false, so separator is ":"
         if (PlatformUtils.IS_WINDOWS) return; // Skip on Windows CI
         List<String> args = new ArrayList<>(List.of("-cp", "lib/a.jar:/path/to/theseus.jar:lib/b.jar", "net.minecraft.Main"));
         List<String> result = GfxDebuggers.filterJavaArgs(args);
         assertEquals(3, result.size());
         assertEquals("-cp", result.get(0));
         assertEquals("lib/a.jar:lib/b.jar", result.get(1));
         assertEquals("net.minecraft.Main", result.get(2));
      }

      @Test
      void classpathWithoutTheseusUntouched() {
         if (PlatformUtils.IS_WINDOWS) return;
         List<String> args = new ArrayList<>(List.of("-classpath", "lib/a.jar:lib/b.jar", "net.minecraft.Main"));
         List<String> result = GfxDebuggers.filterJavaArgs(args);
         assertEquals(List.of("-classpath", "lib/a.jar:lib/b.jar", "net.minecraft.Main"), result);
      }

      @Test
      void classpathFlagAtEndOfList() {
         // -cp without a following arg should pass through as-is
         List<String> args = new ArrayList<>(List.of("-Xmx4G", "-cp"));
         List<String> result = GfxDebuggers.filterJavaArgs(args);
         assertEquals(List.of("-Xmx4G", "-cp"), result);
      }

      @Test
      void allTheseusArgsStrippedTogether() {
         if (PlatformUtils.IS_WINDOWS) return;
         List<String> args = new ArrayList<>(
            List.of(
               "-javaagent:/tmp/theseus.jar",
               "-Dmodrinth.internal.launch=true",
               "-Dmodrinth.internal.id=123",
               "-cp",
               "lib/minecraft.jar:/tmp/theseus.jar:lib/fabric.jar",
               "com.modrinth.theseus.MinecraftLaunch",
               "--username",
               "Player"
            )
         );
         List<String> result = GfxDebuggers.filterJavaArgs(args);
         assertEquals(List.of("-cp", "lib/minecraft.jar:lib/fabric.jar", "--username", "Player"), result);
      }

      @Test
      void nonTheseusJavaagentKept() {
         List<String> args = new ArrayList<>(List.of("-javaagent:/path/to/other-agent.jar", "-Xmx4G"));
         List<String> result = GfxDebuggers.filterJavaArgs(args);
         assertEquals(List.of("-javaagent:/path/to/other-agent.jar", "-Xmx4G"), result);
      }

      @Test
      void nonModrinthDPropertyKept() {
         List<String> args = new ArrayList<>(List.of("-Dmodrinth.public.foo=bar", "-Dfoo=bar"));
         List<String> result = GfxDebuggers.filterJavaArgs(args);
         assertEquals(List.of("-Dmodrinth.public.foo=bar", "-Dfoo=bar"), result);
      }

      @Test
      void singleArg() {
         List<String> args = new ArrayList<>(List.of("-Xmx4G"));
         assertEquals(List.of("-Xmx4G"), GfxDebuggers.filterJavaArgs(args));
      }
   }

   // ── stripTheseusFromArgsString ───────────────────────────────────────

   @Nested
   class StripTheseusFromArgsString {

      @Test
      void cleanStringUnchanged() {
         assertEquals("-Xmx4G -jar test.jar", GfxDebuggers.stripTheseusFromArgsString("-Xmx4G -jar test.jar"));
      }

      @Test
      void emptyString() {
         assertEquals("", GfxDebuggers.stripTheseusFromArgsString(""));
      }

      @Test
      void theseusJavaagentStripped() {
         String input = "-javaagent:/path/to/theseus.jar -Xmx4G";
         String result = GfxDebuggers.stripTheseusFromArgsString(input);
         assertFalse(result.contains("theseus"));
         assertTrue(result.contains("-Xmx4G"));
      }

      @Test
      void theseusMainClassStripped() {
         String input = "-Xmx4G com.modrinth.theseus.MinecraftLaunch --username Player";
         String result = GfxDebuggers.stripTheseusFromArgsString(input);
         assertFalse(result.contains("theseus"));
         assertTrue(result.contains("-Xmx4G"));
         assertTrue(result.contains("--username Player"));
      }

      @Test
      void modrinthInternalPropertiesStripped() {
         String input = "-Dmodrinth.internal.foo=bar -Dmodrinth.internal.baz=qux -Xmx4G";
         String result = GfxDebuggers.stripTheseusFromArgsString(input);
         assertFalse(result.contains("modrinth.internal"));
         assertTrue(result.contains("-Xmx4G"));
      }

      @Test
      void theseusJarInClasspathStripped() {
         String input = "-cp lib/a.jar:/path/theseus.jar:lib/b.jar net.minecraft.Main";
         String result = GfxDebuggers.stripTheseusFromArgsString(input);
         assertFalse(result.contains("theseus"));
         assertTrue(result.contains("lib/a.jar"));
         assertTrue(result.contains("lib/b.jar"));
      }

      @Test
      void doubleColonsCollapsed() {
         String input = "-cp lib/a.jar::lib/b.jar";
         String result = GfxDebuggers.stripTheseusFromArgsString(input);
         assertFalse(result.contains("::"));
      }

      @Test
      void doubleSemicolonsCollapsed() {
         String input = "-cp lib\\a.jar;;lib\\b.jar";
         String result = GfxDebuggers.stripTheseusFromArgsString(input);
         assertFalse(result.contains(";;"));
      }

      @Test
      void multipleSpacesCollapsed() {
         String input = "-Xmx4G     -Xms1G";
         String result = GfxDebuggers.stripTheseusFromArgsString(input);
         assertEquals("-Xmx4G -Xms1G", result);
      }

      @Test
      void leadingTrailingSpacesTrimmed() {
         String input = "  -Xmx4G  ";
         String result = GfxDebuggers.stripTheseusFromArgsString(input);
         assertEquals("-Xmx4G", result);
      }

      @Test
      void allTheseusArtifactsStripped() {
         String input =
            "-javaagent:/tmp/theseus.jar -Dmodrinth.internal.launch=true -cp lib/mc.jar:/tmp/theseus.jar:lib/fabric.jar com.modrinth.theseus.MinecraftLaunch --username Player";
         String result = GfxDebuggers.stripTheseusFromArgsString(input);
         assertFalse(result.contains("theseus"));
         assertFalse(result.contains("modrinth.internal"));
         assertTrue(result.contains("--username Player"));
         assertTrue(result.contains("lib/mc.jar"));
         assertTrue(result.contains("lib/fabric.jar"));
      }

      @Test
      void nonTheseusJavaagentKept() {
         String input = "-javaagent:/path/to/other-agent.jar -Xmx4G";
         String result = GfxDebuggers.stripTheseusFromArgsString(input);
         assertTrue(result.contains("-javaagent:/path/to/other-agent.jar"));
      }

      @Test
      void leadingSeparatorAfterClasspathFlagCleaned() {
         String input = "-cp :lib/b.jar net.minecraft.Main";
         String result = GfxDebuggers.stripTheseusFromArgsString(input);
         // The leading colon after -cp should be removed
         assertFalse(result.matches(".*-cp\\s+:.*"));
      }

      @Test
      void leadingSemicolonAfterClasspathFlagCleaned() {
         String input = "--classpath ;lib\\b.jar net.minecraft.Main";
         String result = GfxDebuggers.stripTheseusFromArgsString(input);
         assertFalse(result.matches(".*--classpath\\s+;.*"));
      }
   }

   // ── writeArgFile ─────────────────────────────────────────────────────

   @Nested
   class WriteArgFile {

      @Test
      void writesAndReadsBack() throws IOException {
         List<String> args = List.of("-Xmx4G", "-Dfoo=bar", "net.minecraft.Main");
         Path argFile = GfxDebuggers.writeArgFile(args);
         try {
            assertTrue(Files.exists(argFile));
            List<String> lines = Files.readAllLines(argFile);
            assertEquals(3, lines.size());
            assertEquals("-Xmx4G", lines.get(0));
            assertEquals("-Dfoo=bar", lines.get(1));
            assertEquals("net.minecraft.Main", lines.get(2));
         } finally {
            Files.deleteIfExists(argFile);
         }
      }

      @Test
      void quotesArgsWithSpaces() throws IOException {
         List<String> args = List.of("hello world", "simple");
         Path argFile = GfxDebuggers.writeArgFile(args);
         try {
            List<String> lines = Files.readAllLines(argFile);
            assertEquals(2, lines.size());
            assertEquals("\"hello world\"", lines.get(0));
            assertEquals("simple", lines.get(1));
         } finally {
            Files.deleteIfExists(argFile);
         }
      }

      @Test
      void emptyArgsWritesEmptyFile() throws IOException {
         Path argFile = GfxDebuggers.writeArgFile(List.of());
         try {
            assertTrue(Files.exists(argFile));
            assertEquals(0, Files.readAllLines(argFile).size());
         } finally {
            Files.deleteIfExists(argFile);
         }
      }

      @Test
      void fileNameMatchesPattern() throws IOException {
         Path argFile = GfxDebuggers.writeArgFile(List.of("test"));
         try {
            String fileName = argFile.getFileName().toString();
            assertTrue(fileName.startsWith("gfx-debuggers-"), "Expected prefix 'gfx-debuggers-', got: " + fileName);
            assertTrue(fileName.endsWith(".args"), "Expected suffix '.args', got: " + fileName);
         } finally {
            Files.deleteIfExists(argFile);
         }
      }

      @Test
      void emptyArgQuoted() throws IOException {
         List<String> args = List.of("");
         Path argFile = GfxDebuggers.writeArgFile(args);
         try {
            List<String> lines = Files.readAllLines(argFile);
            assertEquals(1, lines.size());
            assertEquals("\"\"", lines.get(0));
         } finally {
            Files.deleteIfExists(argFile);
         }
      }

      @Test
      void specialCharsEscaped() throws IOException {
         List<String> args = List.of("say \"hi\"", "it's", "C:\\path");
         Path argFile = GfxDebuggers.writeArgFile(args);
         try {
            List<String> lines = Files.readAllLines(argFile);
            assertEquals(3, lines.size());
            assertEquals("\"say \\\"hi\\\"\"", lines.get(0));
            assertEquals("\"it's\"", lines.get(1));
            assertEquals("\"C:\\\\path\"", lines.get(2));
         } finally {
            Files.deleteIfExists(argFile);
         }
      }

      @Test
      void hashArgQuoted() throws IOException {
         List<String> args = List.of("#comment-looking-arg");
         Path argFile = GfxDebuggers.writeArgFile(args);
         try {
            List<String> lines = Files.readAllLines(argFile);
            assertEquals("\"#comment-looking-arg\"", lines.get(0));
         } finally {
            Files.deleteIfExists(argFile);
         }
      }

      @Test
      void realisticArgList() throws IOException {
         List<String> args = List.of(
            "-Xmx4G",
            "-Xms1G",
            "-Dfoo=bar",
            "-cp",
            "/home/user/.minecraft/libraries/a.jar:/home/user/.minecraft/libraries/b.jar",
            "net.minecraft.client.Main",
            "--username",
            "Player",
            "--version",
            "1.20.4",
            "--gameDir",
            "/home/user/.minecraft",
            "--assetsDir",
            "/home/user/.minecraft/assets"
         );
         Path argFile = GfxDebuggers.writeArgFile(args);
         try {
            List<String> lines = Files.readAllLines(argFile);
            assertEquals(args.size(), lines.size());
            // None of these args have special chars, so they should all be unquoted
            for (int i = 0; i < args.size(); i++) {
               assertEquals(args.get(i), lines.get(i));
            }
         } finally {
            Files.deleteIfExists(argFile);
         }
      }

      @Test
      void realisticArgListWithSpaces() throws IOException {
         List<String> args = List.of(
            "-Xmx4G",
            "-Xms1G",
            "-Dfoo=bar",
            "-cp",
            "C:\\Program Files\\Minecraft\\.minecraft\\libraries\\a.jar;C:\\Program Files\\java\\lib\\b.jar",
            "net.minecraft.client.Main",
            "--username",
            "Player",
            "--version",
            "1.20.4",
            "--gameDir",
            "C:\\Users\\Test User\\AppData\\Roaming\\.minecraft",
            "--assetsDir",
            "C:\\Users\\Test User\\AppData\\Roaming\\.minecraft\\assets"
         );
         Path argFile = GfxDebuggers.writeArgFile(args);
         try {
            List<String> lines = Files.readAllLines(argFile);
            assertEquals(args.size(), lines.size());
            // Simple flags stay unquoted
            assertEquals("-Xmx4G", lines.get(0));
            assertEquals("-Xms1G", lines.get(1));
            assertEquals("-Dfoo=bar", lines.get(2));
            assertEquals("-cp", lines.get(3));
            // Classpath with backslashes gets quoted and escaped
            assertEquals("\"C:\\\\Program Files\\\\Minecraft\\\\.minecraft\\\\libraries\\\\a.jar;C:\\\\Program Files\\\\java\\\\lib\\\\b.jar\"", lines.get(4));
            assertEquals("net.minecraft.client.Main", lines.get(5));
            assertEquals("--username", lines.get(6));
            assertEquals("Player", lines.get(7));
            assertEquals("--version", lines.get(8));
            assertEquals("1.20.4", lines.get(9));
            assertEquals("--gameDir", lines.get(10));
            assertEquals("\"C:\\\\Users\\\\Test User\\\\AppData\\\\Roaming\\\\.minecraft\"", lines.get(11));
            assertEquals("--assetsDir", lines.get(12));
            assertEquals("\"C:\\\\Users\\\\Test User\\\\AppData\\\\Roaming\\\\.minecraft\\\\assets\"", lines.get(13));
         } finally {
            Files.deleteIfExists(argFile);
         }
      }
   }
}
