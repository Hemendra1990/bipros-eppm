package com.bipros.importexport.infrastructure.template;

import com.bipros.importexport.infrastructure.parser.ExcelScheduleParser;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class ExcelTemplateGeneratorTest {

  @Test
  void generate_roundTripsThroughExcelScheduleParser() {
    byte[] bytes = new ExcelTemplateGenerator().generate();

    Map<String, List<Map<String, String>>> t = new ExcelScheduleParser().parse(bytes);

    List<Map<String, String>> task = t.get("TASK");
    assertFalse(task.isEmpty());
    assertNotNull(task.get(0).get("task_code"));

    List<Map<String, String>> manpower = t.get("MANPOWER");
    assertNotNull(manpower);
    assertFalse(manpower.isEmpty());
    assertNotNull(manpower.get(0).get("role_code"));
  }
}
