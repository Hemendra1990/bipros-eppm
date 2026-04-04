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

    return String.format(
        """
        <!DOCTYPE html>
        <html>
        <head>
            <meta charset="UTF-8">
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
        title, title, projectName, timestamp, tableContent);
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
