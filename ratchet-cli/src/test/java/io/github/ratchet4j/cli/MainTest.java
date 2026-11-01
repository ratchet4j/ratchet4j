package io.github.ratchet4j.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.ratchet4j.core.baseline.BaselineIO;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** End-to-end: real files on disk, real Checkstyle-style XML, real CLI entry point. */
class MainTest {

  private static final String SOURCE_V1 =
      "package demo;\n\nimport java.util.List;\nimport java.util.Map;\n\npublic class A {\n    void run() {\n    }\n}\n";

  private record Result(int code, String out, String err) {}

  private static Result cli(String... args) {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    ByteArrayOutputStream err = new ByteArrayOutputStream();
    int code =
        Main.run(
            args,
            new PrintStream(out, true, StandardCharsets.UTF_8),
            new PrintStream(err, true, StandardCharsets.UTF_8));
    return new Result(code, out.toString(StandardCharsets.UTF_8), err.toString(StandardCharsets.UTF_8));
  }

  private static String checkstyle(String file, int... unusedImportLines) {
    StringBuilder sb = new StringBuilder("<?xml version=\"1.0\"?>\n<checkstyle version=\"10\">\n");
    sb.append("<file name=\"").append(file).append("\">\n");
    for (int line : unusedImportLines) {
      sb.append("<error line=\"").append(line).append("\" column=\"1\" severity=\"warning\" ")
          .append("message=\"Unused import.\" source=\"com.puppycrawl.tools.checkstyle.checks.imports.UnusedImportsCheck\"/>\n");
    }
    return sb.append("</file>\n</checkstyle>\n").toString();
  }

  private static Path project() throws IOException {
    Path dir = Files.createTempDirectory("ratchet-cli").toAbsolutePath().normalize();
    Files.createDirectories(dir.resolve("src/main/java/demo"));
    Files.writeString(dir.resolve("src/main/java/demo/A.java"), SOURCE_V1);
    return dir;
  }

  @Test
  void fullLifecycle() throws IOException {
    Path dir = project();
    String file = "src/main/java/demo/A.java";
    Path report = dir.resolve("checkstyle.xml");
    Path baseline = dir.resolve("ratchet-baseline.json");
    String[] common = {"--root", dir.toString(), "--input", report.toString(), "--baseline", baseline.toString()};
    String[] c = common;

    // 1. two legacy violations -> baseline
    Files.writeString(report, checkstyle(dir + "/" + file, 3, 4));
    Result r = cli(concat("baseline", c));
    assertEquals(0, r.code, r.err);
    assertTrue(r.out.contains("2 violations"), r.out);
    assertEquals(2, BaselineIO.read(baseline).size());

    // 2. baseline refuses to silently overwrite (it could grow)
    assertEquals(2, cli(concat("baseline", c)).code);
    assertEquals(0, cli(concat("baseline", c, "--force")).code);

    // 3. same state -> check passes
    r = cli(concat("check", c));
    assertEquals(0, r.code, r.out + r.err);
    assertTrue(r.out.contains("0 new"), r.out);
    assertTrue(r.out.contains("2 baselined"), r.out);

    // 4. a line is inserted above (line numbers shift) -> still passes
    Files.writeString(dir.resolve(file), "// license header\n" + SOURCE_V1);
    Files.writeString(report, checkstyle(dir + "/" + file, 4, 5));
    assertEquals(0, cli(concat("check", c)).code);

    // 5. someone adds a NEW unused import -> check fails and names it
    Files.writeString(dir.resolve(file), "// license header\nimport java.io.File;\n" + SOURCE_V1);
    Files.writeString(report, checkstyle(dir + "/" + file, 2, 5, 6));
    r = cli(concat("check", c));
    assertEquals(1, r.code, r.out);
    assertTrue(r.out.contains(file + ":2:1:"), r.out);
    assertTrue(r.out.contains("[checkstyle:UnusedImports]"), r.out);
    assertTrue(r.out.contains("NEW"), r.out);
    assertTrue(r.out.contains("1 new"), r.out);

    // 6. update refuses to add it (ratchet only goes down)
    int sizeBefore = BaselineIO.read(baseline).size();
    r = cli(concat("update", c));
    assertEquals(1, r.code);
    assertTrue(r.err.contains("NOT added"), r.err);
    assertEquals(sizeBefore, BaselineIO.read(baseline).size());

    // 7. the developer fixes the new one and one legacy one -> update shrinks
    Files.writeString(dir.resolve(file), "// license header\n" + SOURCE_V1.replace("import java.util.List;\n", ""));
    Files.writeString(report, checkstyle(dir + "/" + file, 4));
    r = cli(concat("check", c));
    assertEquals(0, r.code, r.out);
    assertTrue(r.out.contains("1 fixed since baseline"), r.out);
    r = cli(concat("update", c));
    assertEquals(0, r.code, r.err);
    assertEquals(1, BaselineIO.read(baseline).size());

    // 8. progress report
    r = cli(concat("report", c));
    assertEquals(0, r.code);
    assertTrue(r.out.contains("checkstyle:UnusedImports"), r.out);
    assertTrue(r.out.contains("TOTAL"), r.out);
  }

  @Test
  void strictRulesFailEvenWhenBaselined() throws IOException {
    Path dir = project();
    Path report = dir.resolve("cs.xml");
    Path baseline = dir.resolve("b.json");
    Files.writeString(report, checkstyle("src/main/java/demo/A.java", 3));
    String[] c = {"--root", dir.toString(), "--input", report.toString(), "--baseline", baseline.toString()};
    assertEquals(0, cli(concat("baseline", c)).code);
    assertEquals(0, cli(concat("check", c)).code);
    Result r = cli(concat("check", c, "--strict", "checkstyle:Unused*"));
    assertEquals(1, r.code, r.out);
    assertTrue(r.out.contains("STRICT"), r.out);
  }

  @Test
  void usageAndErrorsUseDedicatedExitCodes() throws IOException {
    assertEquals(2, cli().code);
    assertEquals(0, cli("--help").code);
    assertEquals(2, cli("frobnicate").code);
    assertEquals(2, cli("check", "--bogus").code);
    assertEquals(2, cli("check", "--context", "abc").code);
    Path dir = project();
    Result missing = cli("check", "--root", dir.toString(), "--baseline", dir.resolve("none.json").toString(), "--input", dir.resolve("x.xml").toString());
    assertEquals(2, missing.code);
    Files.writeString(dir.resolve("junk.txt"), "hello");
    Files.writeString(dir.resolve("b.json"), "{\"schema\":\"ratchet4j-baseline/1\",\"entries\":[]}");
    Result junk = cli("check", "--root", dir.toString(), "--baseline", dir.resolve("b.json").toString(), "--input", dir.resolve("junk.txt").toString());
    assertEquals(3, junk.code);
    assertTrue(junk.err.contains("Unrecognized report format"), junk.err);
  }

  private static String[] concat(String command, String[] rest, String... more) {
    String[] all = new String[1 + rest.length + more.length];
    all[0] = command;
    System.arraycopy(rest, 0, all, 1, rest.length);
    System.arraycopy(more, 0, all, 1 + rest.length, more.length);
    return all;
  }
}
