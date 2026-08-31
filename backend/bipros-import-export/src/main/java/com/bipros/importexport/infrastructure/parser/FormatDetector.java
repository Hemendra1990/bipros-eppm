package com.bipros.importexport.infrastructure.parser;

import com.bipros.importexport.domain.model.ImportExportFormat;
import org.springframework.stereotype.Component;
import java.nio.charset.StandardCharsets;

/** Sniffs the true format of an uploaded schedule file from its leading bytes, so the correct
 *  parser is used regardless of the format the user picked. Returns null when it cannot tell —
 *  the caller then falls back to the user-selected format. */
@Component
public class FormatDetector {
  public ImportExportFormat detect(byte[] content) {
    if (content == null || content.length < 4) return null;
    // .xlsx (any OOXML) is a ZIP: magic PK\x03\x04
    if (content[0] == 'P' && content[1] == 'K' && content[2] == 0x03 && content[3] == 0x04) {
      return ImportExportFormat.EXCEL;
    }
    // Primavera XER: tab-delimited text beginning with ERMHDR, carrying %T/%F/%R markers.
    String head = new String(content, 0, Math.min(content.length, 512), StandardCharsets.ISO_8859_1);
    if (head.startsWith("ERMHDR") || head.contains("\n%T\t") || head.startsWith("%T\t")) {
      return ImportExportFormat.XER;
    }
    return null;  // XML (P6/MSP) or unknown -> caller uses the selected format
  }
}
