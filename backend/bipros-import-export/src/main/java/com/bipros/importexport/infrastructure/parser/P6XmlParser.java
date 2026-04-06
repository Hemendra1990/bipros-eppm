package com.bipros.importexport.infrastructure.parser;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamReader;
import java.io.StringReader;
import java.util.*;

/**
 * Parses P6 XML format (APIBusinessObjects root element) as exported by P6XmlExporter.
 * Extracts Project, WBS, Activity, ActivityRelationship, Resource, and ResourceAssignment elements.
 */
@Component
@Slf4j
public class P6XmlParser {

  /**
   * Parses P6 XML content into a map of element types to lists of field maps.
   *
   * @param xmlContent the P6 XML string
   * @return map of element type names (e.g. "Project", "Activity") to rows of field name → value
   */
  public Map<String, List<Map<String, String>>> parse(String xmlContent) {
    Map<String, List<Map<String, String>>> result = new LinkedHashMap<>();
    result.put("Project", new ArrayList<>());
    result.put("WBS", new ArrayList<>());
    result.put("Activity", new ArrayList<>());
    result.put("ActivityRelationship", new ArrayList<>());
    result.put("Resource", new ArrayList<>());
    result.put("ResourceAssignment", new ArrayList<>());

    Set<String> knownElements = result.keySet();

    XMLInputFactory factory = XMLInputFactory.newInstance();
    // Prevent XXE attacks
    factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
    factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);

    try {
      XMLStreamReader reader = factory.createXMLStreamReader(new StringReader(xmlContent));

      String currentElement = null;
      Map<String, String> currentRow = null;
      String currentField = null;
      StringBuilder fieldValue = null;

      while (reader.hasNext()) {
        int event = reader.next();

        switch (event) {
          case XMLStreamConstants.START_ELEMENT -> {
            String name = reader.getLocalName();

            if (knownElements.contains(name) && currentElement == null) {
              currentElement = name;
              currentRow = new LinkedHashMap<>();
            } else if (currentElement != null && currentRow != null) {
              currentField = name;
              fieldValue = new StringBuilder();
            }
          }

          case XMLStreamConstants.CHARACTERS -> {
            if (fieldValue != null) {
              fieldValue.append(reader.getText());
            }
          }

          case XMLStreamConstants.END_ELEMENT -> {
            String name = reader.getLocalName();

            if (name.equals(currentElement) && currentRow != null) {
              result.get(currentElement).add(currentRow);
              currentElement = null;
              currentRow = null;
              currentField = null;
              fieldValue = null;
            } else if (currentField != null && name.equals(currentField)) {
              if (currentRow != null && fieldValue != null) {
                currentRow.put(currentField, fieldValue.toString().trim());
              }
              currentField = null;
              fieldValue = null;
            }
          }
        }
      }

      reader.close();

      int total = result.values().stream().mapToInt(List::size).sum();
      log.info("Parsed P6 XML: {} total records across {} element types", total, result.size());
      result.forEach((type, rows) -> {
        if (!rows.isEmpty()) {
          log.debug("  {}: {} records", type, rows.size());
        }
      });

    } catch (Exception e) {
      log.error("Failed to parse P6 XML content", e);
      throw new RuntimeException("Failed to parse P6 XML: " + e.getMessage(), e);
    }

    return result;
  }
}
