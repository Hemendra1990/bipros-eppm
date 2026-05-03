package com.bipros.ai.document;

import com.bipros.common.exception.BusinessRuleException;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.openxml4j.opc.OPCPackage;
import org.apache.poi.openxml4j.opc.PackageAccess;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.util.CellAddress;
import org.apache.poi.util.XMLHelper;
import org.apache.poi.xssf.eventusermodel.XSSFReader;
import org.apache.poi.xssf.eventusermodel.XSSFSheetXMLHandler;
import org.apache.poi.xssf.eventusermodel.XSSFSheetXMLHandler.SheetContentsHandler;
import org.apache.poi.xssf.model.SharedStrings;
import org.apache.poi.xssf.model.StylesTable;
import org.apache.poi.xssf.usermodel.XSSFComment;
import org.springframework.stereotype.Component;
import org.xml.sax.InputSource;
import org.xml.sax.XMLReader;

import java.io.InputStream;
import java.nio.file.Path;
import java.util.Iterator;

/**
 * Streams XLSX rows via the SAX {@link XSSFReader} pipeline so we never
 * build a {@code XSSFWorkbook} DOM in memory. With our 10 MB upload cap,
 * peak heap is bounded by the largest single row rather than the full
 * spreadsheet — the difference between ~1 MB and ~100 MB per concurrent
 * upload.
 *
 * <p>Combined with the global limits set by {@link PoiSafetyConfig},
 * this is the recommended POI pattern for untrusted input.
 */
@Component
@Slf4j
public class XlsxTextExtractor implements DocumentTextExtractor {

    private static final String MIME = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
    private static final int MAX_ROWS_PER_SHEET = 10_000;

    @Override
    public boolean supports(String mimeType, String originalFileName) {
        if (MIME.equalsIgnoreCase(mimeType)) return true;
        return originalFileName != null && originalFileName.toLowerCase().endsWith(".xlsx");
    }

    @Override
    public ExtractedText extract(Path file, String mimeType, String originalFileName) {
        try (OPCPackage opc = OPCPackage.open(file.toFile(), PackageAccess.READ)) {
            XSSFReader xssfReader = new XSSFReader(opc);
            SharedStrings strings = xssfReader.getSharedStringsTable();
            StylesTable styles = xssfReader.getStylesTable();
            DataFormatter formatter = new DataFormatter();

            StringBuilder out = new StringBuilder();
            Iterator<InputStream> sheets = xssfReader.getSheetsData();
            while (sheets.hasNext()) {
                if (out.length() > MAX_CHARS) break;

                // Order matters: next() advances to the sheet whose name we then read.
                try (InputStream sheetIn = sheets.next()) {
                    String sheetName = sheets instanceof XSSFReader.SheetIterator si
                            ? si.getSheetName() : "Sheet";
                    out.append("=== Sheet: ").append(sheetName).append(" ===\n");
                    SheetTextHandler handler = new SheetTextHandler(out);
                    XMLReader xmlReader = XMLHelper.newXMLReader();
                    xmlReader.setContentHandler(new XSSFSheetXMLHandler(
                            styles, null, strings, handler, formatter, false));
                    try {
                        xmlReader.parse(new InputSource(sheetIn));
                    } catch (SheetTextHandler.RowLimitReached ignored) {
                        // expected — we stop the parser early once the cap is hit
                    }
                }
            }
            return ExtractedText.cap(out.toString());
        } catch (BusinessRuleException e) {
            throw e;
        } catch (Exception e) {
            log.warn("XLSX extraction failed for {}: {}", originalFileName, e.getMessage());
            throw new BusinessRuleException("DOCUMENT_PARSE_FAILED",
                    "Failed to read Excel (.xlsx): " + e.getMessage());
        }
    }

    /** SAX-style handler that emits one tab-separated line per non-empty row. */
    private static final class SheetTextHandler implements SheetContentsHandler {

        private final StringBuilder out;
        private final StringBuilder rowBuf = new StringBuilder();
        private int currentCol = -1;
        private int rowsEmitted = 0;
        private boolean rowHasContent = false;

        SheetTextHandler(StringBuilder out) {
            this.out = out;
        }

        @Override
        public void startRow(int rowNum) {
            rowBuf.setLength(0);
            currentCol = -1;
            rowHasContent = false;
        }

        @Override
        public void endRow(int rowNum) {
            if (!rowHasContent) return;
            out.append(rowBuf).append('\n');
            rowsEmitted++;
            if (rowsEmitted >= MAX_ROWS_PER_SHEET) {
                out.append("[NOTE: sheet truncated at ")
                        .append(MAX_ROWS_PER_SHEET).append(" rows]\n");
                throw new RowLimitReached();
            }
            if (out.length() > MAX_CHARS) {
                throw new RowLimitReached();
            }
        }

        @Override
        public void cell(String cellReference, String formattedValue, XSSFComment comment) {
            if (cellReference == null) cellReference = new CellAddress(0, currentCol + 1).formatAsString();
            int col = new CellAddress(cellReference).getColumn();
            // Pad missing leading / intermediate cells with tabs so the row keeps a stable
            // shape (column N is always the Nth tab-separated field). LLMs reason better
            // over consistent table shapes than over jagged rows.
            for (int i = currentCol + 1; i < col; i++) {
                rowBuf.append('\t');
            }
            if (col > 0) rowBuf.append('\t');
            currentCol = col;
            if (formattedValue != null && !formattedValue.isEmpty()) {
                rowHasContent = true;
                // Strip newlines inside cells so each row stays one line in our output.
                rowBuf.append(formattedValue.replace('\n', ' ').replace('\r', ' '));
            }
        }

        /** Thrown to bail out of SAX parsing once we've hit a cap. */
        static final class RowLimitReached extends RuntimeException {
            RowLimitReached() {
                super(null, null, false, false);
            }
        }
    }
}
