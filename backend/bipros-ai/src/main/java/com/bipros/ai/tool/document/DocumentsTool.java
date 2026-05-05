package com.bipros.ai.tool.document;

import com.bipros.ai.context.AiContext;
import com.bipros.ai.tool.Tool;
import com.bipros.ai.tool.ToolResult;
import com.bipros.document.domain.model.Document;
import com.bipros.document.domain.model.DocumentFolder;
import com.bipros.document.domain.model.DocumentType;
import com.bipros.document.domain.repository.DocumentFolderRepository;
import com.bipros.document.domain.repository.DocumentRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Document METADATA queries — no full-text content access. Operations: list, by_type, by_activity,
 * by_wbs, search_metadata.
 * <p>
 * Notes on scope:
 * <ul>
 *   <li>{@code Document} has no activity_id column. {@code by_activity} therefore returns an
 *       explanatory error with the recommended next step (search by metadata or WBS).</li>
 *   <li>{@code by_wbs} joins through {@link DocumentFolder#wbsNodeId} — the schema models the
 *       WBS link at the folder level, not the document level.</li>
 *   <li>For full-text content extraction, use the existing async ingestion pipeline; this tool
 *       deliberately stays metadata-only to keep latency predictable.</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DocumentsTool implements Tool {

  private static final int DEFAULT_LIMIT = 100;
  private static final int MAX_LIMIT = 500;

  private final DocumentRepository documentRepository;
  private final DocumentFolderRepository folderRepository;
  private final ObjectMapper objectMapper;

  @Override
  public String name() {
    return "documents";
  }

  @Override
  public String description() {
    return "Use this for document metadata queries — list documents, filter by type (DRAWING / "
        + "SPECIFICATION / RFI / MINUTES / etc) or status, search by name/title substring, or scope "
        + "to a WBS package via folder. Operations via op param: 'list' (paginated documents for "
        + "the project), 'by_type' (filter by document_type or mime_type substring), 'by_activity' "
        + "(NOT MODELLED — Document has no activity_id; returns guidance), 'by_wbs' (documents "
        + "whose folder is linked to a wbs_node_id), 'search_metadata' (case-insensitive substring "
        + "on title / file_name / document_number). Tool returns metadata only; no full-text "
        + "content extraction (use the async ingestion pipeline for that). Project-scoped.";
  }

  @Override
  public JsonNode inputSchema() {
    ObjectNode schema = objectMapper.createObjectNode();
    schema.put("type", "object");
    ObjectNode props = objectMapper.createObjectNode();
    ArrayNode opEnum = objectMapper.createArrayNode();
    opEnum.add("list");
    opEnum.add("by_type");
    opEnum.add("by_activity");
    opEnum.add("by_wbs");
    opEnum.add("search_metadata");
    ObjectNode opNode = objectMapper.createObjectNode();
    opNode.put("type", "string");
    opNode.set("enum", opEnum);
    props.set("op", opNode);
    props.set("document_type", objectMapper.createObjectNode().put("type", "string")
        .put("description", "DocumentType enum value, e.g. DRAWING, SPECIFICATION, RFI, MINUTES."));
    props.set("mime_type", objectMapper.createObjectNode().put("type", "string")
        .put("description", "Case-insensitive substring match on mime_type, e.g. pdf, dwg, image."));
    props.set("wbs_node_id", objectMapper.createObjectNode().put("type", "string").put("format", "uuid")
        .put("description", "For op=by_wbs."));
    props.set("query", objectMapper.createObjectNode().put("type", "string")
        .put("description", "Substring for op=search_metadata; matches title, file_name, document_number."));
    props.set("limit", objectMapper.createObjectNode().put("type", "integer").put("minimum", 1).put("maximum", MAX_LIMIT).put("default", DEFAULT_LIMIT));
    schema.set("properties", props);
    ArrayNode required = objectMapper.createArrayNode();
    required.add("op");
    schema.set("required", required);
    return schema;
  }

  @Override
  @Transactional(readOnly = true)
  public ToolResult execute(JsonNode input, AiContext ctx) {
    UUID projectId = ctx.projectId();
    if (projectId == null) return ToolResult.error("documents needs a project in scope.");
    if (!"ADMIN".equals(ctx.role())
        && (ctx.scopedProjectIds() == null || !ctx.scopedProjectIds().contains(projectId))) {
      throw new AccessDeniedException("project not in user scope");
    }
    String op = orNull(input.path("op").asText(null));
    if (op == null) return ToolResult.error("op is required");
    int limit = Math.max(1, Math.min(MAX_LIMIT, input.path("limit").asInt(DEFAULT_LIMIT)));

    return switch (op) {
      case "list" -> doList(projectId, null, null, null, limit);
      case "by_type" -> doByType(projectId, input, limit);
      case "by_wbs" -> doByWbs(projectId, input, limit);
      case "search_metadata" -> doSearch(projectId, input, limit);
      case "by_activity" -> ToolResult.error(
          "by_activity is not supported: Document has no activity_id column in this schema. "
              + "Try search_metadata with the activity code as the query, or use by_wbs to scope "
              + "by the activity's WBS node.");
      default -> ToolResult.error("Unknown op: " + op);
    };
  }

  private ToolResult doList(UUID projectId, DocumentType type, String mime, String wbsLabel, int limit) {
    List<Document> all = documentRepository.findByProjectId(projectId);
    return rowsResponse(all, type, mime, null, projectId, limit, wbsLabel == null ? "all documents" : wbsLabel);
  }

  private ToolResult doByType(UUID projectId, JsonNode input, int limit) {
    String typeStr = orNull(input.path("document_type").asText(null));
    String mime = orNull(input.path("mime_type").asText(null));
    DocumentType type = null;
    if (typeStr != null) {
      try { type = DocumentType.valueOf(typeStr.toUpperCase()); }
      catch (IllegalArgumentException e) {
        return ToolResult.error("Unknown document_type: " + typeStr + " (expected DRAWING / SPECIFICATION / RFI / MINUTES / CONTRACT_DOCUMENT / REPORT / BANK_GUARANTEE / LOA)");
      }
    }
    if (type == null && mime == null) return ToolResult.error("Provide document_type or mime_type for op=by_type.");
    List<Document> all = documentRepository.findByProjectId(projectId);
    String label = "type=" + (type == null ? "*" : type.name()) + (mime == null ? "" : "/mime~" + mime);
    return rowsResponse(all, type, mime, null, projectId, limit, label);
  }

  private ToolResult doByWbs(UUID projectId, JsonNode input, int limit) {
    String wbsStr = orNull(input.path("wbs_node_id").asText(null));
    if (wbsStr == null) return ToolResult.error("wbs_node_id is required for op=by_wbs.");
    UUID wbsId;
    try { wbsId = UUID.fromString(wbsStr); }
    catch (IllegalArgumentException e) { return ToolResult.error("wbs_node_id must be a UUID."); }

    List<DocumentFolder> folders = folderRepository.findByProjectIdOrderBySortOrder(projectId);
    Set<UUID> matchingFolderIds = new HashSet<>();
    for (DocumentFolder f : folders) if (wbsId.equals(f.getWbsNodeId())) matchingFolderIds.add(f.getId());
    if (matchingFolderIds.isEmpty()) {
      ObjectNode w = objectMapper.createObjectNode();
      w.set("rows", objectMapper.createArrayNode());
      w.put("count", 0);
      w.put("note", "No folders are linked to that WBS node.");
      return ToolResult.ok("0 documents (no folders linked to that WBS)", w);
    }
    List<Document> all = documentRepository.findByProjectId(projectId);
    return rowsResponse(all, null, null, matchingFolderIds, projectId, limit, "wbs=" + wbsStr);
  }

  private ToolResult doSearch(UUID projectId, JsonNode input, int limit) {
    String q = orNull(input.path("query").asText(null));
    if (q == null) return ToolResult.error("query is required for op=search_metadata.");
    String lq = q.toLowerCase();
    List<Document> all = documentRepository.findByProjectId(projectId);
    ArrayNode rows = objectMapper.createArrayNode();
    int matched = 0;
    int returned = 0;
    Map<String, Integer> typeCounts = new LinkedHashMap<>();
    for (Document d : all) {
      boolean hit = (d.getTitle() != null && d.getTitle().toLowerCase().contains(lq))
          || (d.getFileName() != null && d.getFileName().toLowerCase().contains(lq))
          || (d.getDocumentNumber() != null && d.getDocumentNumber().toLowerCase().contains(lq));
      if (!hit) continue;
      matched++;
      String t = d.getDocumentType() == null ? "UNSET" : d.getDocumentType().name();
      typeCounts.merge(t, 1, Integer::sum);
      if (returned >= limit) continue;
      rows.add(toRow(d));
      returned++;
    }
    ObjectNode w = objectMapper.createObjectNode();
    w.set("rows", rows);
    w.put("matched", matched);
    w.put("returned", returned);
    w.put("query", q);
    ArrayNode tc = objectMapper.createArrayNode();
    for (var e : typeCounts.entrySet()) {
      ObjectNode n = objectMapper.createObjectNode();
      n.put("type", e.getKey());
      n.put("count", e.getValue());
      tc.add(n);
    }
    w.set("type_counts", tc);
    return ToolResult.ok(matched + " document" + (matched == 1 ? "" : "s") + " matching \"" + q + "\"", w);
  }

  private ToolResult rowsResponse(List<Document> all, DocumentType type, String mime, Set<UUID> folderIds, UUID projectId, int limit, String label) {
    String lm = mime == null ? null : mime.toLowerCase();
    ArrayNode rows = objectMapper.createArrayNode();
    int matched = 0;
    int returned = 0;
    Map<String, Integer> typeCounts = new LinkedHashMap<>();
    for (Document d : all) {
      if (type != null && d.getDocumentType() != type) continue;
      if (lm != null && (d.getMimeType() == null || !d.getMimeType().toLowerCase().contains(lm))) continue;
      if (folderIds != null && (d.getFolderId() == null || !folderIds.contains(d.getFolderId()))) continue;
      matched++;
      String t = d.getDocumentType() == null ? "UNSET" : d.getDocumentType().name();
      typeCounts.merge(t, 1, Integer::sum);
      if (returned >= limit) continue;
      rows.add(toRow(d));
      returned++;
    }
    ObjectNode w = objectMapper.createObjectNode();
    w.set("rows", rows);
    w.put("matched", matched);
    w.put("returned", returned);
    w.put("filter", label);
    ArrayNode tc = objectMapper.createArrayNode();
    for (var e : typeCounts.entrySet()) {
      ObjectNode n = objectMapper.createObjectNode();
      n.put("type", e.getKey());
      n.put("count", e.getValue());
      tc.add(n);
    }
    w.set("type_counts", tc);
    return ToolResult.ok(matched + " documents (" + label + ")", w);
  }

  private ObjectNode toRow(Document d) {
    ObjectNode n = objectMapper.createObjectNode();
    n.put("document_id", d.getId().toString());
    n.put("document_number", d.getDocumentNumber());
    n.put("title", d.getTitle());
    n.put("file_name", d.getFileName());
    n.put("file_size", d.getFileSize());
    n.put("mime_type", d.getMimeType());
    n.put("document_type", d.getDocumentType() == null ? null : d.getDocumentType().name());
    n.put("status", d.getStatus() == null ? null : d.getStatus().name());
    n.put("discipline", d.getDiscipline() == null ? null : d.getDiscipline().name());
    n.put("current_version", d.getCurrentVersion());
    n.put("folder_id", d.getFolderId() == null ? null : d.getFolderId().toString());
    n.put("transmittal_number", d.getTransmittalNumber());
    n.put("wbs_package_code", d.getWbsPackageCode());
    n.put("issued_by", d.getIssuedBy());
    n.put("issued_date", d.getIssuedDate() == null ? null : d.getIssuedDate().toString());
    n.put("approved_by", d.getApprovedBy());
    n.put("approved_date", d.getApprovedDate() == null ? null : d.getApprovedDate().toString());
    return n;
  }

  private static String orNull(String s) {
    return s == null || s.isBlank() ? null : s.trim();
  }
}
