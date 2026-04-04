package com.bipros.importexport.infrastructure.export;

import com.bipros.activity.domain.model.Activity;
import com.bipros.activity.domain.model.ActivityRelationship;
import com.bipros.activity.domain.model.RelationshipType;
import com.bipros.activity.domain.repository.ActivityRelationshipRepository;
import com.bipros.activity.domain.repository.ActivityRepository;
import com.bipros.project.domain.model.Project;
import com.bipros.project.domain.repository.ProjectRepository;
import com.bipros.resource.domain.model.Resource;
import com.bipros.resource.domain.model.ResourceAssignment;
import com.bipros.resource.domain.repository.ResourceAssignmentRepository;
import com.bipros.resource.domain.repository.ResourceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamWriter;
import java.io.StringWriter;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class MspXmlExporter {

  private final ProjectRepository projectRepository;
  private final ActivityRepository activityRepository;
  private final ActivityRelationshipRepository activityRelationshipRepository;
  private final ResourceRepository resourceRepository;
  private final ResourceAssignmentRepository resourceAssignmentRepository;

  private static final String MSP_XMLNS = "http://schemas.microsoft.com/project";
  private static final DateTimeFormatter ISO_DATE = DateTimeFormatter.ISO_DATE;

  public String export(UUID projectId) throws Exception {
    var project = projectRepository.findById(projectId)
        .orElseThrow(() -> new IllegalArgumentException("Project not found: " + projectId));

    StringWriter sw = new StringWriter();
    XMLOutputFactory factory = XMLOutputFactory.newInstance();
    XMLStreamWriter writer = factory.createXMLStreamWriter(sw);

    try {
      writer.writeStartDocument("UTF-8", "1.0");
      writer.writeStartElement("Project");
      writer.writeDefaultNamespace(MSP_XMLNS);

      // Project metadata
      writeElement(writer, "Name", project.getName());
      writeElement(writer, "Code", project.getCode());
      if (project.getPlannedStartDate() != null) {
        writeElement(writer, "StartDate", project.getPlannedStartDate().toString());
      }
      if (project.getPlannedFinishDate() != null) {
        writeElement(writer, "FinishDate", project.getPlannedFinishDate().toString());
      }

      // Tasks section
      List<Activity> activities = activityRepository.findByProjectId(projectId);
      List<ActivityRelationship> relationships = activityRelationshipRepository.findByProjectId(projectId);

      writer.writeStartElement("Tasks");
      Map<UUID, Integer> activityUidMap = new HashMap<>();
      int uid = 1;
      for (Activity activity : activities) {
        activityUidMap.put(activity.getId(), uid);
        uid++;
      }

      // Now write tasks with proper predecessor UIDs
      uid = 1;
      for (Activity activity : activities) {
        writeTaskElementWithPredecessors(writer, activity, uid, activityUidMap, relationships);
        uid++;
      }
      writer.writeEndElement(); // Tasks

      // Resources section
      List<Resource> resources = resourceRepository.findAll();
      writer.writeStartElement("Resources");
      Map<UUID, Integer> resourceUidMap = new HashMap<>();
      uid = 1;
      for (Resource resource : resources) {
        resourceUidMap.put(resource.getId(), uid);
        writeResourceElement(writer, resource, uid);
        uid++;
      }
      writer.writeEndElement(); // Resources

      // Assignments section
      List<ResourceAssignment> assignments = resourceAssignmentRepository.findByProjectId(projectId);
      writer.writeStartElement("Assignments");
      for (ResourceAssignment assignment : assignments) {
        Integer taskUid = activityUidMap.get(assignment.getActivityId());
        Integer resUid = resourceUidMap.get(assignment.getResourceId());
        if (taskUid != null && resUid != null) {
          writeAssignmentElement(writer, assignment, taskUid, resUid);
        }
      }
      writer.writeEndElement(); // Assignments

      writer.writeEndElement(); // Project
      writer.writeEndDocument();
      writer.flush();

      log.info("MSP XML export completed for project: {}", projectId);
      return sw.toString();
    } finally {
      writer.close();
    }
  }

  private void writeTaskElementWithPredecessors(XMLStreamWriter writer, Activity activity, int uid, Map<UUID, Integer> activityUidMap, List<ActivityRelationship> allRelationships) throws Exception {
    writer.writeStartElement("Task");
    writeElement(writer, "UID", String.valueOf(uid));
    writeElement(writer, "ID", String.valueOf(uid));
    writeElement(writer, "Name", activity.getName());
    writeElement(writer, "Code", activity.getCode());

    if (activity.getPlannedStartDate() != null) {
      writeElement(writer, "Start", activity.getPlannedStartDate().toString());
    }
    if (activity.getPlannedFinishDate() != null) {
      writeElement(writer, "Finish", activity.getPlannedFinishDate().toString());
    }

    // Duration in ISO 8601 format (PT8H = 8 hours)
    if (activity.getRemainingDuration() != null && activity.getRemainingDuration() > 0) {
      long hours = Math.round(activity.getRemainingDuration());
      writeElement(writer, "Duration", "PT" + hours + "H");
    } else {
      writeElement(writer, "Duration", "PT8H");
    }

    double percentComplete = activity.getPercentComplete() != null ? activity.getPercentComplete() : 0.0;
    writeElement(writer, "PercentComplete", String.valueOf((int)(percentComplete * 100)));

    if (activity.getTotalFloat() != null) {
      writeElement(writer, "TotalSlack", String.valueOf(Math.round(activity.getTotalFloat())));
    }

    // Predecessors (relationships where this activity is the successor)
    for (ActivityRelationship rel : allRelationships) {
      if (rel.getSuccessorActivityId().equals(activity.getId())) {
        Integer predUid = activityUidMap.get(rel.getPredecessorActivityId());
        if (predUid != null) {
          writer.writeStartElement("PredecessorLink");
          writeElement(writer, "PredecessorUID", String.valueOf(predUid));
          writeElement(writer, "Type", mapRelationshipTypeToMsp(rel.getRelationshipType()));
          if (rel.getLag() != null && rel.getLag() != 0) {
            writeElement(writer, "LinkLag", String.valueOf((int)Math.round(rel.getLag())));
          }
          writer.writeEndElement(); // PredecessorLink
        }
      }
    }

    writer.writeEndElement(); // Task
  }

  private void writeResourceElement(XMLStreamWriter writer, Resource resource, int uid) throws Exception {
    writer.writeStartElement("Resource");
    writeElement(writer, "UID", String.valueOf(uid));
    writeElement(writer, "ID", String.valueOf(uid));
    writeElement(writer, "Name", resource.getName());
    writeElement(writer, "Type", resource.getResourceType() != null ? resource.getResourceType().toString() : "Material");
    writeElement(writer, "MaxUnits", "100");
    writer.writeEndElement(); // Resource
  }

  private void writeAssignmentElement(XMLStreamWriter writer, ResourceAssignment assignment, int taskUid, int resourceUid) throws Exception {
    writer.writeStartElement("Assignment");
    writeElement(writer, "TaskUID", String.valueOf(taskUid));
    writeElement(writer, "ResourceUID", String.valueOf(resourceUid));
    writeElement(writer, "Units", String.valueOf(assignment.getPlannedUnits() != null ? assignment.getPlannedUnits() : 1.0));
    writer.writeEndElement(); // Assignment
  }

  private String mapRelationshipTypeToMsp(com.bipros.activity.domain.model.RelationshipType type) {
    if (type == null) {
      return "1"; // Default to FS
    }
    return switch (type) {
      case FINISH_TO_START -> "1";
      case FINISH_TO_FINISH -> "2";
      case START_TO_START -> "3";
      case START_TO_FINISH -> "4";
      default -> "1";
    };
  }

  private void writeElement(XMLStreamWriter writer, String elementName, String value) throws Exception {
    writer.writeStartElement(elementName);
    if (value != null && !value.isEmpty()) {
      writer.writeCharacters(value);
    }
    writer.writeEndElement();
  }
}
