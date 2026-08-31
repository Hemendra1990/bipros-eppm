package com.bipros.reporting.infrastructure.export;

import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

@Component
@Slf4j
public class PdfReportGenerator {

  private static final DateTimeFormatter DATE_FORMATTER =
      DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

  public byte[] generateReport(String title, String htmlContent, String projectName) {
    try {
      String html = buildHtml(title, projectName, htmlContent);
      return renderToPdf(html);
    } catch (IOException e) {
      log.error("Error generating PDF report: {}", title, e);
      throw new RuntimeException("Failed to generate PDF report", e);
    }
  }

  public byte[] generateActivityReport(UUID projectId, String projectName, String htmlTable) {
    String html = buildHtml("Activity Report", projectName, htmlTable);
    return generateReport("Activity Report", html, projectName);
  }

  public byte[] generateResourceReport(UUID projectId, String projectName, String htmlTable) {
    String html = buildHtml("Resource Report", projectName, htmlTable);
    return generateReport("Resource Report", html, projectName);
  }

  public byte[] generateCostReport(UUID projectId, String projectName, String htmlTable) {
    String html = buildHtml("Cost Report", projectName, htmlTable);
    return generateReport("Cost Report", html, projectName);
  }

  private String buildHtml(String title, String projectName, String tableContent) {
    String timestamp = LocalDateTime.now().format(DATE_FORMATTER);
    // openhtmltopdf parses this as strict XHTML, so plain-text values (title, project name)
    // injected into the template MUST be XML-escaped — a raw '&' or '<' (e.g. a project named
    // "…Road & Link…") otherwise aborts PDF rendering at the <title>. tableContent is markup,
    // escaped at its own source (e.g. DprReportHtmlRenderer.esc).
    String safeTitle = escapeXml(title);
    String safeProject = escapeXml(projectName);

    return String.format(
        """
        <!DOCTYPE html>
        <html>
        <head>
            <meta charset="UTF-8" />
            <title>%s</title>
            <style>
                body {
                    font-family: Arial, sans-serif;
                    margin: 40px;
                    color: #333;
                }
                .header {
                    border-bottom: 2px solid #0066cc;
                    padding-bottom: 15px;
                    margin-bottom: 20px;
                }
                .header h1 {
                    margin: 0;
                    color: #0066cc;
                }
                .meta-info {
                    font-size: 12px;
                    color: #666;
                    margin-top: 5px;
                }
                table {
                    width: 100%%;
                    border-collapse: collapse;
                    margin: 20px 0;
                }
                thead {
                    background-color: #0066cc;
                    color: white;
                }
                th {
                    padding: 10px;
                    text-align: left;
                    font-weight: bold;
                }
                td {
                    padding: 8px 10px;
                    border-bottom: 1px solid #ddd;
                }
                tbody tr:nth-child(even) {
                    background-color: #f9f9f9;
                }
                tbody tr:hover {
                    background-color: #f0f0f0;
                }
            </style>
        </head>
        <body>
            <div class="header">
                <h1>%s</h1>
                <div class="meta-info">
                    <p><strong>Project:</strong> %s</p>
                    <p><strong>Generated:</strong> %s</p>
                </div>
            </div>
            %s
        </body>
        </html>
        """,
        safeTitle, safeTitle, safeProject, timestamp, tableContent);
  }

  /**
   * Branded document shell (2026-08-05): the content brings its OWN header band and styling
   * (e.g. the Daily Project Report), so this wrapper adds only the page frame — no blue h1
   * header, no default table skin that would fight the content's inline styles. The generated
   * timestamp goes into a discreet footer line instead.
   */
  public byte[] generateBranded(String title, String bodyContent) {
    String timestamp = LocalDateTime.now().format(DATE_FORMATTER);
    String html = String.format(
        """
        <!DOCTYPE html>
        <html>
        <head>
            <meta charset="UTF-8" />
            <title>%s</title>
            <style>
                @page { size: A4; margin: 14mm 12mm; }
                body { font-family: Arial, sans-serif; margin: 0; color: #232A31; }
            </style>
        </head>
        <body>
            %s
            <div style="margin-top:10px;font-size:9px;color:#8a9199;text-align:center">Generated %s</div>
        </body>
        </html>
        """,
        escapeXml(title), bodyContent, timestamp);
    try {
      return renderToPdf(html);
    } catch (IOException e) {
      log.error("Error generating branded PDF report: {}", title, e);
      throw new RuntimeException("Failed to generate PDF report", e);
    }
  }

  /** XML-escape a plain-text value for safe injection into the XHTML the PDF engine parses. */
  private static String escapeXml(String s) {
    if (s == null) return "";
    return s.replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;");
  }

  private byte[] renderToPdf(String html) throws IOException {
    ByteArrayOutputStream baos = new ByteArrayOutputStream();

    PdfRendererBuilder builder = new PdfRendererBuilder();
    builder.withHtmlContent(html, null);
    builder.toStream(baos);
    builder.run();

    return baos.toByteArray();
  }
}
