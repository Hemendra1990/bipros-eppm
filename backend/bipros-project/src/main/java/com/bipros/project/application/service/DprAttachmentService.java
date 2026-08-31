package com.bipros.project.application.service;

import com.bipros.common.exception.ResourceNotFoundException;
import com.bipros.project.application.dto.DprAttachmentResponse;
import com.bipros.project.domain.model.DailyProgressReport;
import com.bipros.project.domain.model.DprAttachment;
import com.bipros.project.domain.repository.DailyProgressReportRepository;
import com.bipros.project.domain.repository.DprAttachmentRepository;
import com.bipros.project.infrastructure.storage.DprAttachmentStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Manages photo attachments hanging off a DPR row. Lifecycle is independent of the resource
 * grids — photos are uploaded after the DPR is saved (the client posts the row first, then sends
 * a multipart photo request). Binaries live on disk via {@link DprAttachmentStorageService};
 * metadata lives in {@code project.dpr_attachments}.
 *
 * <p>If a photo's DB write fails we attempt best-effort cleanup of the just-written binary so the
 * filesystem doesn't accumulate orphans. The reverse (DB row exists but binary missing) is rare
 * and surfaces as a 500 on the binary GET — operators can trim DB rows pointing at missing files.
 */
@Service
@Transactional
@Slf4j
@RequiredArgsConstructor
public class DprAttachmentService {

  private final DailyProgressReportRepository dprRepository;
  private final DprAttachmentRepository attachmentRepository;
  private final DprAttachmentStorageService storage;

  public List<DprAttachmentResponse> addAll(UUID projectId, UUID dprId, List<MultipartFile> files, List<String> captions) {
    DailyProgressReport dpr = requireDpr(projectId, dprId);
    if (files == null || files.isEmpty()) {
      return List.of();
    }

    List<DprAttachment> created = new ArrayList<>(files.size());
    List<String> writtenPaths = new ArrayList<>(files.size());
    try {
      for (int i = 0; i < files.size(); i++) {
        MultipartFile file = files.get(i);
        String caption = captions == null || i >= captions.size() ? null : captions.get(i);
        DprAttachmentStorageService.StoredPhoto stored = storage.store(dprId, file);
        writtenPaths.add(stored.relativePath());
        DprAttachment entity = DprAttachment.builder()
            .dprId(dprId)
            .projectId(dpr.getProjectId())
            .fileName(stored.fileName())
            .mimeType(stored.mimeType())
            .fileSize(stored.fileSize())
            .storagePath(stored.relativePath())
            .caption(caption)
            .build();
        created.add(entity);
      }
      List<DprAttachment> saved = attachmentRepository.saveAll(created);
      return saved.stream().map(DprAttachmentResponse::from).toList();
    } catch (RuntimeException ex) {
      // Best-effort cleanup: any binaries we wrote in this call get removed so we don't leak.
      for (String path : writtenPaths) {
        storage.deleteQuietly(path);
      }
      throw ex;
    }
  }

  @Transactional(readOnly = true)
  public List<DprAttachmentResponse> list(UUID projectId, UUID dprId) {
    requireDpr(projectId, dprId);
    return attachmentRepository.findByDprIdOrderByCreatedAtAsc(dprId).stream()
        .map(DprAttachmentResponse::from).toList();
  }

  @Transactional(readOnly = true)
  public LoadedPhoto load(UUID projectId, UUID dprId, UUID photoId) {
    DprAttachment attachment = requireAttachment(projectId, dprId, photoId);
    Resource resource = storage.load(attachment.getStoragePath());
    return new LoadedPhoto(resource, attachment.getMimeType(), attachment.getFileName(), attachment.getFileSize());
  }

  public void delete(UUID projectId, UUID dprId, UUID photoId) {
    DprAttachment attachment = requireAttachment(projectId, dprId, photoId);
    String path = attachment.getStoragePath();
    attachmentRepository.delete(attachment);
    storage.deleteQuietly(path);
  }

  private DailyProgressReport requireDpr(UUID projectId, UUID dprId) {
    DailyProgressReport dpr = dprRepository.findById(dprId)
        .orElseThrow(() -> new ResourceNotFoundException("DailyProgressReport", dprId));
    if (!dpr.getProjectId().equals(projectId)) {
      throw new ResourceNotFoundException("DailyProgressReport", dprId);
    }
    return dpr;
  }

  private DprAttachment requireAttachment(UUID projectId, UUID dprId, UUID photoId) {
    DprAttachment attachment = attachmentRepository.findById(photoId)
        .orElseThrow(() -> new ResourceNotFoundException("DprAttachment", photoId));
    if (!attachment.getDprId().equals(dprId) || !attachment.getProjectId().equals(projectId)) {
      throw new ResourceNotFoundException("DprAttachment", photoId);
    }
    return attachment;
  }

  public record LoadedPhoto(Resource resource, String mimeType, String fileName, long fileSize) {}
}
