package io.github.ratchet4j.readers;

import java.io.IOException;
import java.io.InputStream;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

/** Hardened StAX helper: no DTDs and no external entities (XXE safe). */
final class Xml {

  private Xml() {}

  static XMLStreamReader open(InputStream in) throws IOException {
    XMLInputFactory f = XMLInputFactory.newFactory();
    f.setProperty(XMLInputFactory.SUPPORT_DTD, false);
    f.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
    f.setProperty(XMLInputFactory.IS_REPLACING_ENTITY_REFERENCES, true);
    try {
      return f.createXMLStreamReader(in);
    } catch (XMLStreamException e) {
      throw new IOException("Cannot parse XML: " + e.getMessage(), e);
    }
  }

  static int intAttr(XMLStreamReader r, String name) {
    String v = r.getAttributeValue(null, name);
    if (v == null) {
      return 0;
    }
    try {
      return Integer.parseInt(v.trim());
    } catch (NumberFormatException e) {
      return 0;
    }
  }
}
