package com.bipros.importexport.application.service;

import com.bipros.common.exception.ResourceNotFoundException;
import com.bipros.importexport.application.dto.ImportExportJobResponse;
import com.bipros.importexport.application.dto.ImportExportLogResponse;
import com.bipros.importexport.domain.model.*;
import com.bipros.importexport.domain.repository.ImportExportJobRepository;
import com.bipros.importexport.domain.repository.ImportExportLogRepository;
import com.bipros.importexport.infrastructure.parser.XerParser;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ImportExportService {

  private final ImportExportJobRepository jobRepository;
  private final ImportExportLogRepository logRepository;
  private final XerParser xerParser;
  private final XerImportMapper xerImportMapper;
  private final ObjectMapper objectMapper;

  @Transactional
  public ImportExportJobResponse exportProject(UUID projectId, ImportExportFormat format) throws Exception {
    var job = new ImportExportJob();
    job.setProjectId(projectId);
    job.setFormat(format);
    job.setDirection(ImportExportDirection.EXPORT);
    job.setStatus(ImportExportStatus.PENDING);
    job.setStartedAt(Instant.now());

    try {
      job.setStatus(ImportExportStatus.PROCESSING);
      var saved = jobRepository.save(job);

      String exportData = generateExportData(projectId, format);

      String fileName = "project_" + projectId + "_export." + getFileExtension(format);
      saved.setFileName(fileName);
      saved.setFilePath("/exports/" + fileName);
      saved.setStatus(ImportExportStatus.COMPLETED);
      saved.setCompletedAt(Instant.now());
      saved.setProcessedRecords(1);
      saved.setTotalRecords(1);

      var result = jobRepository.save(saved);
      log.info("Export completed: projectId={}, format={}, jobId={}", projectId, format, result.getId());

      return ImportExportJobResponse.from(result);
    } catch (Exception e) {
      log.error("Export failed: projectId={}, format={}", projectId, format, e);
      job.setStatus(ImportExportStatus.FAILED);
      job.setCompletedAt(Instant.now());
      try {
        job.setErrorLog(objectMapper.writeValueAsString(List.of(e.getMessage())));
      } catch (Exception logError) {
        job.setErrorLog("[\"" + e.getMessage() + "\"]");
      }
      jobRepository.save(job);
      throw e;
    }
  }

  @Transactional
  public ImportExportJobResponse importProject(
      MultipartFile file, ImportExportFormat format) throws Exception {
    var job = new ImportExportJob();
    job.setFileName(file.getOriginalFilename());
    job.setFormat(format);
    job.setDirection(ImportExportDirection.IMPORT);
    job.setStatus(ImportExportStatus.PENDING);
    job.setStartedAt(Instant.now());

    try {
      job.setStatus(ImportExportStatus.PROCESSING);
      var saved = jobRepository.save(job);

      String fileContent = new String(file.getBytes(), StandardCharsets.UTF_8);
      var errors = new ArrayList<String>();

      if (ImportExportFormat.XER.equals(format)) {
        parseXER(fileContent, saved.getId(), errors);
      } else if (ImportExportFormat.P6XML.equals(format)) {
        logMessage(saved.getId(), "WARN", "P6XML parsing not yet implemented");
      } else if (ImportExportFormat.MSP_XML.equals(format)) {
        logMessage(saved.getId(), "WARN", "MSP XML parsing not yet implemented");
      } else if (ImportExportFormat.EXCEL.equals(format)) {
        logMessage(saved.getId(), "WARN", "Excel parsing not yet implemented");
      }

      saved.setStatus(ImportExportStatus.COMPLETED);
      saved.setCompletedAt(Instant.now());
      saved.setProcessedRecords(1);
      saved.setTotalRecords(1);
      saved.setErrorCount(errors.size());

      if (!errors.isEmpty()) {
        try {
          saved.setErrorLog(objectMapper.writeValueAsString(errors));
        } catch (Exception e) {
          saved.setErrorLog(errors.toString());
        }
      }

      var result = jobRepository.save(saved);
      log.info("Import completed: fileName={}, format={}, jobId={}", file.getOriginalFilename(), format, result.getId());

      return ImportExportJobResponse.from(result);
    } catch (Exception e) {
      log.error("Import failed: fileName={}, format={}", file.getOriginalFilename(), format, e);
      job.setStatus(ImportExportStatus.FAILED);
      job.setCompletedAt(Instant.now());
      try {
        job.setErrorLog(objectMapper.writeValueAsString(List.of(e.getMessage())));
      } catch (Exception logError) {
        job.setErrorLog("[\"" + e.getMessage() + "\"]");
      }
      jobRepository.save(job);
      throw e;
    }
  }

  @Transactional(readOnly = true)
  public ImportExportJobResponse getJobStatus(UUID jobId) {
    var entity =
        jobRepository
            .findById(jobId)
            .orElseThrow(() -> new ResourceNotFoundException("ImportExportJob", jobId));
    return ImportExportJobResponse.from(entity);
  }

  @Transactional(readOnly = true)
  public List<ImportExportLogResponse> getJobLogs(UUID jobId) {
    return logRepository.findByJobId(jobId).stream()
        .map(ImportExportLogResponse::from)
        .collect(Collectors.toList());
  }

  @Transactional(readOnly = true)
  public List<ImportExportJobResponse> listJobs() {
    return jobRepository.findAll().stream()
        .map(ImportExportJobResponse::from)
        .collect(Collectors.toList());
  }

  private void parseXER(String content, UUID jobId, List<String> errors) {
    try {
      Map<String, List<Map<String, String>>> tables = xerParser.parse(content);
      logMessage(jobId, "INFO", "Parsed XER with " + tables.size() + " tables");

      for (String tableName : tables.keySet()) {
        int recordCount = tables.get(tableName).size();
        logMessage(jobId, "INFO", "Table " + tableName + ": " + recordCount + " records");
      }

      UUID projectId = xerImportMapper.importProject(tables);
      logMessage(jobId, "INFO", "Successfully imported project: " + projectId);
    } catch (Exception e) {
      String errorMsg = "Error parsing XER: " + e.getMessage();
      errors.add(errorMsg);
      logMessage(jobId, "ERROR", errorMsg);
      log.error("XER import failed", e);
    }
  }

  private String generateExportData(UUID projectId, ImportExportFormat format) throws Exception {
    return switch (format) {
      case XER -> generateXERData(projectId);
      case P6XML -> generateP6XMLData(projectId);
      case MSP_XML -> generateMSPXMLData(projectId);
      case EXCEL -> generateExcelData(projectId);
    };
  }

  private String generateXERData(UUID projectId) {
    // Placeholder: generate XER format structure
    return "%TPROJECT\n%FPROJECT_ID\tPROJECT_NAME\n%R" + projectId + "\tTest Project\n";
  }

  private String generateP6XMLData(UUID projectId) {
    // Placeholder: generate P6XML format
    return "<?xml version=\"1.0\"?>\n<Project>\n</Project>";
  }

  private String generateMSPXMLData(UUID projectId) {
    // Placeholder: generate MSP XML format
    return "<?xml version=\"1.0\"?>\n<Project>\n</Project>";
  }

  private String generateExcelData(UUID projectId) {
    // Placeholder: Excel data would be binary, return placeholder
    return "Excel export placeholder";
  }

  private void logMessage(UUID jobId, String level, String message) {
    var log = new ImportExportLog();
    log.setJobId(jobId);
    log.setLevel(level);
    log.setMessage(message);
    logRepository.save(log);
  }

  private String getFileExtension(ImportExportFormat format) {
    return switch (format) {
      case XER -> "xer";
      case P6XML -> "xml";
      case MSP_XML -> "xml";
      case EXCEL -> "xlsx";
    };
  }
}
