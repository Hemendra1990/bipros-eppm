package com.bipros.ai.document;

import com.bipros.common.exception.BusinessRuleException;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.poifs.filesystem.POIFSFileSystem;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.springframework.stereotype.Component;

import java.nio.file.Path;

/**
 * Legacy {@code .xls} (BIFF) handler. POI's streaming HSSFEventFactory exists
 * but adds significant complexity for a format vendors rarely deliver in 2026.
 * We use {@link HSSFWorkbook} in-memory but inherit the JVM-global POI limits
 * configured by {@link PoiSafetyConfig}, which bound peak heap.
 */
@Component
@Slf4j
public class XlsTextExtractor implements DocumentTextExtractor {

    private static final String MIME = "application/vnd.ms-excel";
    private static final int MAX_ROWS_PER_SHEET = 10_000;

    @Override
    public boolean supports(String mimeType, String originalFileName) {
        if (MIME.equalsIgnoreCase(mimeType)) return true;
        return originalFileName != null && originalFileName.toLowerCase().endsWith(".xls");
    }

    @Override
    public ExtractedText extract(Path file, String mimeType, String originalFileName) {
        try (POIFSFileSystem poifs = new POIFSFileSystem(file.toFile(), true);
             HSSFWorkbook workbook = new HSSFWorkbook(poifs.getRoot(), true)) {

            DataFormatter formatter = new DataFormatter();
            StringBuilder out = new StringBuilder();
            for (int s = 0; s < workbook.getNumberOfSheets(); s++) {
                Sheet sheet = workbook.getSheetAt(s);
                out.append("=== Sheet: ").append(sheet.getSheetName()).append(" ===\n");
                int rowsEmitted = 0;
                for (Row row : sheet) {
                    if (row == null) continue;
                    StringBuilder rowText = new StringBuilder();
                    boolean hasContent = false;
                    int lastCell = row.getLastCellNum();
                    for (int c = 0; c < lastCell; c++) {
                        if (c > 0) rowText.append('\t');
                        Cell cell = row.getCell(c);
                        if (cell != null) {
                            String v = formatter.formatCellValue(cell);
                            if (v != null && !v.isEmpty()) hasContent = true;
                            rowText.append(v == null ? "" : v.replace('\n', ' ').replace('\r', ' '));
                        }
                    }
                    if (hasContent) {
                        out.append(rowText).append('\n');
                        rowsEmitted++;
                        if (rowsEmitted >= MAX_ROWS_PER_SHEET) {
                            out.append("[NOTE: sheet truncated at ")
                                    .append(MAX_ROWS_PER_SHEET).append(" rows]\n");
                            break;
                        }
                    }
                    if (out.length() > MAX_CHARS) break;
                }
                if (out.length() > MAX_CHARS) break;
            }
            return ExtractedText.cap(out.toString());
        } catch (Exception e) {
            log.warn("XLS extraction failed for {}: {}", originalFileName, e.getMessage());
            throw new BusinessRuleException("DOCUMENT_PARSE_FAILED",
                    "Failed to read Excel (.xls): " + e.getMessage());
        }
    }
}
