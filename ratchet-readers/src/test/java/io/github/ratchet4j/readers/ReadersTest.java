package io.github.ratchet4j.readers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.ratchet4j.core.model.Severity;
import io.github.ratchet4j.core.model.Violation;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Reader tests. NOTE: the XML/JSON samples below follow the real report layouts but were written
 * by hand. Task P1-T3 replaces/extends them with output captured from the real tools.
 */
class ReadersTest {

  private static Path root() throws IOException {
    Path root = Files.createTempDirectory("ratchet-readers").toAbsolutePath().normalize();
    Files.createDirectories(root.resolve("src/main/java/demo"));
    Files.writeString(root.resolve("src/main/java/demo/A.java"), "package demo;\nclass A {}\n");
    return root;
  }

  private static String abs(Path root, String rel) {
    return root.toString().replace('\\', '/') + "/" + rel;
  }

  private static List<Violation> read(ViolationReader r, String text, ReaderContext ctx) throws IOException {
    return r.read(new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8)), ctx);
  }

  @Test
  void checkstyleReaderMapsEverything() throws IOException {
    Path root = root();
    String xml =
        "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<checkstyle version=\"10.12.0\">\n"
            + "<file name=\"" + abs(root, "src/main/java/demo/A.java") + "\">\n"
            + "<error line=\"3\" column=\"1\" severity=\"warning\" message=\"Unused import - java.util.List.\""
            + " source=\"com.puppycrawl.tools.checkstyle.checks.imports.UnusedImportsCheck\"/>\n"
            + "<error line=\"10\" severity=\"error\" message=\"Missing a Javadoc comment.\""
            + " source=\"com.puppycrawl.tools.checkstyle.checks.javadoc.MissingJavadocMethodCheck\"/>\n"
            + "</file>\n<file name=\"" + abs(root, "src/main/java/demo/Clean.java") + "\"></file>\n</checkstyle>";
    List<Violation> vs = read(new CheckstyleXmlReader(), xml, ReaderContext.defaults(root));
    assertEquals(2, vs.size());
    Violation v = vs.get(0);
    assertEquals("checkstyle", v.tool());
    assertEquals("UnusedImports", v.ruleId());
    assertEquals("checkstyle:UnusedImports", v.rule());
    assertEquals("src/main/java/demo/A.java", v.file());
    assertEquals(3, v.line());
    assertEquals(1, v.column());
    assertEquals(Severity.WARNING, v.severity());
    assertEquals("Unused import - java.util.List.", v.message());
    assertTrue(v.raw().get("source").endsWith("UnusedImportsCheck"));
    assertEquals(Severity.ERROR, vs.get(1).severity());
    assertEquals(0, vs.get(1).column());
    assertEquals("MissingJavadocMethod", vs.get(1).ruleId());
  }

  @Test
  void pmdReaderMapsEverything() throws IOException {
    Path root = root();
    String xml =
        "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<pmd version=\"6.55.0\" timestamp=\"2026-10-31T10:00:00.000\">\n"
            + "<file name=\"" + abs(root, "src/main/java/demo/A.java") + "\">\n"
            + "<violation beginline=\"12\" endline=\"12\" begincolumn=\"17\" endcolumn=\"21\""
            + " rule=\"UnusedPrivateField\" ruleset=\"Best Practices\" package=\"demo\" class=\"A\" priority=\"3\">\n"
            + "Avoid unused private fields such as 'x'.\n</violation>\n"
            + "<violation beginline=\"20\" begincolumn=\"5\" rule=\"EmptyCatchBlock\" ruleset=\"Error Prone\" priority=\"1\">Avoid empty catch blocks</violation>\n"
            + "</file>\n</pmd>";
    List<Violation> vs = read(new PmdXmlReader(), xml, ReaderContext.defaults(root));
    assertEquals(2, vs.size());
    assertEquals("pmd:UnusedPrivateField", vs.get(0).rule());
    assertEquals(12, vs.get(0).line());
    assertEquals(17, vs.get(0).column());
    assertEquals("Avoid unused private fields such as 'x'.", vs.get(0).message());
    assertEquals(Severity.WARNING, vs.get(0).severity());
    assertEquals(Severity.ERROR, vs.get(1).severity());
    assertEquals("Best Practices", vs.get(0).raw().get("ruleset"));
    assertEquals("src/main/java/demo/A.java", vs.get(0).file());
  }

  @Test
  void spotbugsReaderPrefersOwnLineThenMethodThenClassThenNoLine() throws IOException {
    Path root = root();
    String xml =
        "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<BugCollection version=\"4.8.3\">\n"
            + "<BugInstance type=\"NP_NULL_ON_SOME_PATH\" priority=\"1\" rank=\"3\" abbrev=\"NP\" category=\"CORRECTNESS\">\n"
            + " <ShortMessage>Possible null pointer dereference</ShortMessage>\n"
            + " <LongMessage>Possible null pointer dereference in demo.A.run()</LongMessage>\n"
            + " <Class classname=\"demo.A\" primary=\"true\"><SourceLine classname=\"demo.A\" start=\"1\" end=\"30\" sourcepath=\"demo/A.java\"/></Class>\n"
            + " <Method classname=\"demo.A\" name=\"run\" signature=\"()V\" primary=\"true\"><SourceLine classname=\"demo.A\" start=\"10\" end=\"20\" sourcepath=\"demo/A.java\"/></Method>\n"
            + " <SourceLine classname=\"demo.A\" primary=\"true\" start=\"14\" end=\"14\" sourcepath=\"demo/A.java\" sourcefile=\"A.java\"/>\n"
            + "</BugInstance>\n"
            + "<BugInstance type=\"DLS_DEAD_LOCAL_STORE\" priority=\"2\">\n"
            + " <LongMessage>Dead store in demo.A.go()</LongMessage>\n"
            + " <Class classname=\"demo.A\"><SourceLine classname=\"demo.A\" start=\"1\" end=\"30\" sourcepath=\"demo/A.java\"/></Class>\n"
            + " <Method classname=\"demo.A\" name=\"go\"><SourceLine classname=\"demo.A\" start=\"22\" end=\"25\" sourcepath=\"demo/A.java\"/></Method>\n"
            + "</BugInstance>\n"
            + "<BugInstance type=\"URF_UNREAD_FIELD\" priority=\"3\">\n"
            + " <LongMessage>Unread field: demo.B$Inner.x</LongMessage>\n"
            + " <Class classname=\"demo.B$Inner\"/>\n"
            + "</BugInstance>\n</BugCollection>";
    List<Violation> vs = read(new SpotBugsXmlReader(), xml, ReaderContext.defaults(root));
    assertEquals(3, vs.size());

    assertEquals("spotbugs:NP_NULL_ON_SOME_PATH", vs.get(0).rule());
    assertEquals("src/main/java/demo/A.java", vs.get(0).file());
    assertEquals(14, vs.get(0).line());
    assertEquals(Severity.ERROR, vs.get(0).severity());
    assertEquals("Possible null pointer dereference in demo.A.run()", vs.get(0).message());
    assertEquals("A#run", vs.get(0).raw().get("scope"));

    assertEquals(22, vs.get(1).line());
    assertEquals(Severity.WARNING, vs.get(1).severity());

    assertEquals(0, vs.get(2).line());
    assertEquals("demo/B.java", vs.get(2).file());
    assertEquals("Inner", vs.get(2).raw().get("scope"));
    assertEquals(Severity.INFO, vs.get(2).severity());
  }

  @Test
  void sarifReaderToleratesMissingParts() throws IOException {
    Path root = root();
    String sarif =
        "{\"version\":\"2.1.0\",\"runs\":[{\"tool\":{\"driver\":{\"name\":\"Qodana\"}},\"results\":["
            + "{\"ruleId\":\"JavaDoc\",\"level\":\"error\",\"message\":{\"text\":\"Missing Javadoc\"},"
            + "\"locations\":[{\"physicalLocation\":{\"artifactLocation\":{\"uri\":\"src/main/java/demo/A.java\"},"
            + "\"region\":{\"startLine\":7,\"startColumn\":3}}},{\"physicalLocation\":{}}]},"
            + "{\"ruleId\":\"NoLocation\",\"message\":{\"text\":\"Global finding\"}},"
            + "{\"message\":{\"text\":\"no rule id\"},\"level\":\"note\",\"someFutureField\":[1,2,3]}"
            + "]}]}";
    List<Violation> vs = read(new SarifReader(), sarif, ReaderContext.defaults(root));
    assertEquals(3, vs.size());
    assertEquals("qodana", vs.get(0).tool());
    assertEquals("qodana:JavaDoc", vs.get(0).rule());
    assertEquals("src/main/java/demo/A.java", vs.get(0).file());
    assertEquals(7, vs.get(0).line());
    assertEquals(3, vs.get(0).column());
    assertEquals(Severity.ERROR, vs.get(0).severity());
    assertEquals("1", vs.get(0).raw().get("extraLocations"));
    assertEquals(0, vs.get(1).line());
    assertEquals("unknown", vs.get(1).file());
    assertEquals("unknown", vs.get(2).ruleId());
    assertEquals(Severity.INFO, vs.get(2).severity());
  }

  @Test
  void detectionPicksTheRightReaderAndRejectsUnknownFormats() throws IOException {
    Path dir = Files.createTempDirectory("ratchet-detect");
    Path cs = dir.resolve("cs.xml");
    Files.writeString(cs, "<?xml version=\"1.0\"?>\n<checkstyle version=\"10\"></checkstyle>");
    Path pmd = dir.resolve("pmd.xml");
    Files.writeString(pmd, "\uFEFF<?xml version=\"1.0\"?>\n<pmd version=\"7\"></pmd>");
    Path sb = dir.resolve("sb.xml");
    Files.writeString(sb, "<BugCollection version=\"4\"></BugCollection>");
    Path sarif = dir.resolve("r.sarif");
    Files.writeString(sarif, "  {\"version\":\"2.1.0\",\"runs\":[]}");
    Path junk = dir.resolve("junk.txt");
    Files.writeString(junk, "hello");

    assertEquals("checkstyle-xml", Readers.detect(cs).name());
    assertEquals("pmd-xml", Readers.detect(pmd).name());
    assertEquals("spotbugs-xml", Readers.detect(sb).name());
    assertEquals("sarif", Readers.detect(sarif).name());
    assertThrows(IOException.class, () -> Readers.detect(junk));
  }

  @Test
  void pathsAreNormalizedFromAllCommonForms() throws IOException {
    Path root = root();
    ReaderContext ctx = ReaderContext.defaults(root);
    assertEquals("src/A.java", ctx.normalizePath("./src/../src/A.java"));
    assertEquals("src/A.java", ctx.normalizePath("src\\A.java"));
    assertEquals("src/A.java", ctx.normalizePath(abs(root, "src/A.java")));
    assertEquals("src/A.java", ctx.normalizePath(root.resolve("src/A.java").toUri().toString()));
    assertEquals("demo/A.java", ctx.resolveSourcePath("demo/A.java").replace("src/main/java/", ""));
    assertEquals("src/main/java/demo/A.java", ctx.resolveSourcePath("demo/A.java"));
    assertEquals("no/such/File.java", ctx.resolveSourcePath("no/such/File.java"));
  }

  @Test
  void brokenXmlIsReportedAsIoException() {
    assertThrows(IOException.class, () -> read(new CheckstyleXmlReader(), "<checkstyle><file name=\"a\"><error", ReaderContext.defaults(Path.of("."))));
    assertThrows(IOException.class, () -> read(new SarifReader(), "{ not json", ReaderContext.defaults(Path.of("."))));
  }

  @Test
  void externalEntitiesAreNeverExpanded() throws IOException {
    Path secret = Files.createTempFile("ratchet-secret", ".txt");
    Files.writeString(secret, "TOP-SECRET-CONTENT");
    String xml =
        "<?xml version=\"1.0\"?>\n<!DOCTYPE checkstyle [<!ENTITY xxe SYSTEM \"" + secret.toUri() + "\">]>\n"
            + "<checkstyle><file name=\"a/A.java\"><error line=\"1\" message=\"&xxe;\" source=\"x.YCheck\"/></file></checkstyle>";
    try {
      List<Violation> vs = read(new CheckstyleXmlReader(), xml, ReaderContext.defaults(Path.of(".")));
      for (Violation v : vs) {
        assertTrue(!v.message().contains("TOP-SECRET-CONTENT"), "entity must not be expanded");
      }
    } catch (IOException expected) {
      // refusing the document is also a safe outcome
    }
  }
}
