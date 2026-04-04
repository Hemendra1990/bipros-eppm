package com.bipros.importexport.infrastructure.parser;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
@Slf4j
public class XerParser {

  /**
   * Parses XER format (tab-delimited with %T, %F, %R headers)
   *
   * @param content the XER file content as a string
   * @return a map of table names to rows, each row is a map of field names to values
   */
  public Map<String, List<Map<String, String>>> parse(String content) {
    Map<String, List<Map<String, String>>> tables = new LinkedHashMap<>();
    String currentTable = null;
    List<String> fieldNames = null;

    for (String line : content.split("\n")) {
      if (line.startsWith("%T")) {
        // Table header: %T<table_name>
        currentTable = line.substring(2).trim();
        tables.put(currentTable, new ArrayList<>());
        fieldNames = null;
        log.debug("Found table: {}", currentTable);
      } else if (line.startsWith("%F")) {
        // Field header: %F<field1>\t<field2>\t...
        fieldNames = Arrays.asList(line.substring(2).trim().split("\t", -1));
        log.debug("Found fields for {}: {}", currentTable, fieldNames);
      } else if (line.startsWith("%R")) {
        // Row data: %R<value1>\t<value2>\t...
        if (currentTable != null && fieldNames != null) {
          String[] values = line.substring(2).trim().split("\t", -1);
          Map<String, String> row = new LinkedHashMap<>();

          for (int i = 0; i < Math.min(fieldNames.size(), values.length); i++) {
            row.put(fieldNames.get(i), values[i]);
          }

          tables.get(currentTable).add(row);
        }
      }
    }

    log.debug("Parsed XER: {} tables", tables.size());
    return tables;
  }
}
