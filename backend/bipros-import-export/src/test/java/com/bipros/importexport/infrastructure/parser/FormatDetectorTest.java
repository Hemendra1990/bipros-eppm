package com.bipros.importexport.infrastructure.parser;

import com.bipros.importexport.domain.model.ImportExportFormat;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import static org.junit.jupiter.api.Assertions.*;

class FormatDetectorTest {
  private final FormatDetector detector = new FormatDetector();

  @Test
  void xlsxBytes_detectExcel() throws Exception {
    try (Workbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
      wb.createSheet("Activities"); wb.write(out);
      assertEquals(ImportExportFormat.EXCEL, detector.detect(out.toByteArray()));
    }
  }

  @Test
  void xerText_detectXer() {
    String xer = "ERMHDR\t18.0\t2026-01-01\n%T\tPROJECT\n%F\tproj_id\n%R\t1\n";
    assertEquals(ImportExportFormat.XER, detector.detect(xer.getBytes(StandardCharsets.UTF_8)));
  }

  @Test
  void garbage_returnsNull() {
    assertNull(detector.detect("just some random prose".getBytes(StandardCharsets.UTF_8)));
    assertNull(detector.detect(new byte[0]));
    assertNull(detector.detect(null));
  }
}
