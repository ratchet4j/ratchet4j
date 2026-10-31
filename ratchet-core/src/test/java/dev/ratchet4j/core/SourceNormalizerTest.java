package dev.ratchet4j.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.ratchet4j.core.source.SourceNormalizer;
import org.junit.jupiter.api.Test;

class SourceNormalizerTest {

  @Test
  void collapsesWhitespaceAndTrims() {
    assertEquals("int a = 1;", SourceNormalizer.normalizeLine("   int\t a   =  1;  "));
  }

  @Test
  void stripsTrailingLineComments() {
    assertEquals("bad();", SourceNormalizer.normalizeLine("bad(); // remove me"));
  }

  @Test
  void keepsCommentMarkersInsideStringLiterals() {
    assertEquals("String u = \"http://x.y\";", SourceNormalizer.normalizeLine("String u = \"http://x.y\"; // url"));
    assertEquals("char c = '\"'; x();", SourceNormalizer.normalizeLine("char c = '\"'; x(); // q"));
  }

  @Test
  void handlesEscapedQuotesInStrings() {
    assertEquals("s = \"a\\\"//b\";", SourceNormalizer.normalizeLine("s = \"a\\\"//b\"; // c"));
  }

  @Test
  void removesInlineBlockComments() {
    assertEquals("int x = 1;", SourceNormalizer.normalizeLine("int /* the x */ x = 1;"));
    assertEquals("int y;", SourceNormalizer.normalizeLine("int y; /* unterminated"));
  }

  @Test
  void commentOnlyAndBlankLinesAreNoise() {
    assertTrue(SourceNormalizer.isNoise(""));
    assertTrue(SourceNormalizer.isNoise("   "));
    assertTrue(SourceNormalizer.isNoise("  // hello"));
    assertTrue(SourceNormalizer.isNoise("  /** Javadoc */"));
    assertTrue(SourceNormalizer.isNoise("   * @param x the x"));
    assertTrue(SourceNormalizer.isNoise("   */"));
    assertFalse(SourceNormalizer.isNoise("int a = 1; // trailing"));
  }

  @Test
  void messagesIgnoreNumbers() {
    assertEquals("Line is # characters long (max #)", SourceNormalizer.normalizeMessage("Line is 130  characters long (max 120)"));
  }
}
