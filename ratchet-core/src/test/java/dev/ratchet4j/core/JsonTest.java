package dev.ratchet4j.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.ratchet4j.core.json.Json;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class JsonTest {

  @Test
  void parsesNestedStructures() {
    Map<String, Object> m = Json.asMap(Json.parse("{\"a\": [1, 2.5, true, null, \"x\"], \"b\": {\"c\": -3}}"));
    List<Object> a = Json.asList(m.get("a"));
    assertEquals(1L, a.get(0));
    assertEquals(2.5, a.get(1));
    assertEquals(true, a.get(2));
    assertNull(a.get(3));
    assertEquals("x", a.get(4));
    assertEquals(-3L, Json.asLong(Json.asMap(m.get("b")).get("c"), 0));
  }

  @Test
  void parsesEscapes() {
    assertEquals("a\"b\\c\n\u00e9/", Json.parse("\"a\\\"b\\\\c\\n\\u00e9\\/\""));
  }

  @Test
  void quoteRoundTrips() {
    String tricky = "q\" b\\ n\n t\t c\u0001 u\u00fc";
    assertEquals(tricky, Json.parse(Json.quote(tricky)));
  }

  @Test
  void rejectsMalformedInput() {
    assertThrows(IllegalArgumentException.class, () -> Json.parse("{\"a\": }"));
    assertThrows(IllegalArgumentException.class, () -> Json.parse("[1, 2"));
    assertThrows(IllegalArgumentException.class, () -> Json.parse("{} extra"));
    assertThrows(IllegalArgumentException.class, () -> Json.parse("\"unterminated"));
  }

  @Test
  void rejectsAbsurdNesting() {
    String deep = "[".repeat(10_000) + "]".repeat(10_000);
    assertThrows(IllegalArgumentException.class, () -> Json.parse(deep));
  }

  @Test
  void accessorsAreForgiving() {
    assertEquals(Map.of(), Json.asMap("nope"));
    assertEquals(List.of(), Json.asList(null));
    assertNull(Json.asString(5L));
    assertEquals(7L, Json.asLong("x", 7));
  }
}
