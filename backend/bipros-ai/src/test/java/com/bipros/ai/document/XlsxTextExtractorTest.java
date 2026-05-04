package com.bipros.ai.document;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Smoke test for the streaming XSSFReader+SAX path. Writes a small workbook
 * to a temp file, reads it back via {@link XlsxTextExtractor}, asserts the
 * key cell values appear in the extracted text.
 */
class XlsxTextExtractorTest {

    @Test
    void extractsRowsFromMultipleSheets() throws Exception {
        Path tempFile = Files.createTempFile("xlsx-extract-test-", ".xlsx");
        try {
            try (XSSFWorkbook wb = new XSSFWorkbook();
                 OutputStream out = Files.newOutputStream(tempFile)) {

                Sheet s1 = wb.createSheet("WBS");
                Row h1 = s1.createRow(0);
                h1.createCell(0).setCellValue("Code");
                h1.createCell(1).setCellValue("Description");
                Row r1 = s1.createRow(1);
                r1.createCell(0).setCellValue("1");
                r1.createCell(1).setCellValue("Site Preparation");
                Row r2 = s1.createRow(2);
                r2.createCell(0).setCellValue("1.1");
                r2.createCell(1).setCellValue("Excavation");

                Sheet s2 = wb.createSheet("Notes");
                Row n1 = s2.createRow(0);
                n1.createCell(0).setCellValue("This sheet must also be included.");

                wb.write(out);
            }

            XlsxTextExtractor extractor = new XlsxTextExtractor();
            DocumentTextExtractor.ExtractedText extracted =
                    extractor.extract(tempFile, MimeTypeDetector.XLSX, "test.xlsx");

            String text = extracted.text();
            assertFalse(extracted.truncated(), "small workbook should not truncate");
            assertTrue(text.contains("=== Sheet: WBS ==="),  "sheet header for WBS missing");
            assertTrue(text.contains("=== Sheet: Notes ==="),"sheet header for Notes missing");
            assertTrue(text.contains("Site Preparation"),    "row 1 description missing");
            assertTrue(text.contains("Excavation"),          "row 2 description missing");
            assertTrue(text.contains("This sheet must also be included."),
                    "second-sheet content missing");
            // Tab-separated row shape: code <tab> description
            assertTrue(text.contains("1\tSite Preparation"), "tab-separated row missing");
        } finally {
            Files.deleteIfExists(tempFile);
        }
    }
}
