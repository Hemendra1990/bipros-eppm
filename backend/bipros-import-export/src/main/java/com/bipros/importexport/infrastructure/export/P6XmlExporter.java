package com.bipros.importexport.infrastructure.export;

import com.bipros.activity.domain.model.Activity;
import com.bipros.activity.domain.model.ActivityRelationship;
import com.bipros.activity.domain.repository.ActivityRelationshipRepository;
import com.bipros.activity.domain.repository.ActivityRepository;
import com.bipros.project.domain.model.Project;
import com.bipros.project.domain.model.WbsNode;
import com.bipros.project.domain.repository.ProjectRepository;
import com.bipros.project.domain.repository.WbsNodeRepository;
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
import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class P6XmlExporter {

  private final ProjectRepository projectRepository;
  private final WbsNodeRepository wbsNodeRepository;
  private final ActivityRepository activityRepository;
  private final ActivityRelationshipRepository activityRelationshipRepository;
  private final ResourceRepository resourceRepository;
  private final ResourceAssignmentRepository resourceAssignmentRepository;

  private static final DateTimeFormatter ISO_DATE = DateTimeFormatter.ISO_DATE;

  public String export(UUID projectId) throws Exception {
    var project = projectRepository.findById(projectId)
        .orElseThrow(() -> new IllegalArgumentException("Project not found: " + projectId));

    StringWriter sw = new StringWriter();
    XMLOutputFactory factory = XMLOutputFactory.newInstance();
    XMLStreamWriter writer = factory.createXMLStreamWriter(sw);

    try {
      writer.writeStartDocument("UTF-8", "1.0");
      writer.writeStartElement("APIBusinessObjects");

      // Export Project
      writeProjectElement(writer, project);

      // Export WBS Hierarchy
      List<WbsNode> wbsNodes = wbsNodeRepository.findByProjectIdOrderBySortOrder(projectId);
      for (WbsNode wbs : wbsNodes) {
        writeWbsElement(writer, wbs);
      }

      // Export Activities
      List<Activity> activities = activityRepository.findByProjectId(projectId);
      for (Activity activity : activities) {
        writeActivityElement(writer, activity);
      }

      // Export Activity Relationships
      List<ActivityRelationship> relationships = activityRelationshipRepository.findByProjectId(projectId);
      for (ActivityRelationship rel : relationships) {
        writeRelationshipElement(writer, rel);
      }

      // Export Resources
      List<Resource> resources = resourceRepository.findAll();
      for (Resource resource : resources) {
        writeResourceElement(writer, resource);
      }

      // Export Resource Assignments
      List<ResourceAssignment> assignments = resourceAssignmentRepository.findByProjectId(projectId);
      for (ResourceAssignment assignment : assignments) {
        writeAssignmentElement(writer, assignment);
      }

      writer.writeEndElement(); // APIBusinessObjects
      writer.writeEndDocument();
      writer.flush();

      log.info("P6XML export completed for project: {}", projectId);
      return sw.toString();
    } finally {
      writer.close();
    }
  }

  private void writeProjectElement(XMLStreamWriter writer, Project project) throws Exception {
    writer.writeStartElement("Project");
    writeElement(writer, "Id", project.getId().toString());
    writeElement(writer, "Name", project.getName());
    writeElement(writer, "Code", project.getCode());
    writeElement(writer, "Status", project.getStatus() != null ? project.getStatus().toString() : "");
    if (project.getPlannedStartDate() != null) {
      writeElement(writer, "PlannedStartDate", project.getPlannedStartDate().toString());
    }
    if (project.getPlannedFinishDate() != null) {
      writeElement(writer, "PlannedFinishDate", project.getPlannedFinishDate().toString());
    }
    writer.writeEndElement(); // Project
  }

  private void writeWbsElement(XMLStreamWriter writer, WbsNode wbs) throws Exception {
    writer.writeStartElement("WBS");
    writeElement(writer, "Id", wbs.getId().toString());
    writeElement(writer, "Code", wbs.getCode());
    writeElement(writer, "Name", wbs.getName());
    if (wbs.getParentId() != null) {
      writeElement(writer, "ParentId", wbs.getParentId().toString());
    }
    writeElement(writer, "ProjectId", wbs.getProjectId().toString());
    writer.writeEndElement(); // WBS
  }

  private void writeActivityElement(XMLStreamWriter writer, Activity activity) throws Exception {
    writer.writeStartElement("Activity");
    writeElement(writer, "Id", activity.getId().toString());
    writeElement(writer, "Code", activity.getCode());
    writeElement(writer, "Name", activity.getName());
    writeElement(writer, "Type", activity.getActivityType() != null ? activity.getActivityType().toString() : "");
    if (activity.getPlannedStartDate() != null) {
      writeElement(writer, "PlannedStartDate", activity.getPlannedStartDate().toString());
    }
    if (activity.getPlannedFinishDate() != null) {
      writeElement(writer, "PlannedFinishDate", activity.getPlannedFinishDate().toString());
    }
    if (activity.getActualStartDate() != null) {
      writeElement(writer, "ActualStartDate", activity.getActualStartDate().toString());
    }
    if (activity.getActualFinishDate() != null) {
      writeElement(writer, "ActualFinishDate", activity.getActualFinishDate().toString());
    }
    writeElement(writer, "PercentComplete", String.valueOf(activity.getPercentComplete() != null ? activity.getPercentComplete() : 0.0));
    writeElement(writer, "RemainingDuration", String.valueOf(activity.getRemainingDuration() != null ? activity.getRemainingDuration() : 0.0));
    writeElement(writer, "WbsNodeId", activity.getWbsNodeId().toString());
    writeElement(writer, "ProjectId", activity.getProjectId().toString());
    writer.writeEndElement(); // Activity
  }

  private void writeRelationshipElement(XMLStreamWriter writer, ActivityRelationship rel) throws Exception {
    writer.writeStartElement("ActivityRelationship");
    writeElement(writer, "PredecessorActivityId", rel.getPredecessorActivityId().toString());
    writeElement(writer, "SuccessorActivityId", rel.getSuccessorActivityId().toString());
    writeElement(writer, "Type", rel.getRelationshipType() != null ? rel.getRelationshipType().toString() : "");
    writeElement(writer, "Lag", String.valueOf(rel.getLag() != null ? rel.getLag() : 0.0));
    writeElement(writer, "ProjectId", rel.getProjectId().toString());
    writer.writeEndElement(); // ActivityRelationship
  }

  private void writeResourceElement(XMLStreamWriter writer, Resource resource) throws Exception {
    writer.writeStartElement("Resource");
    writeElement(writer, "Id", resource.getId().toString());
    writeElement(writer, "Code", resource.getCode());
    writeElement(writer, "Name", resource.getName());
    writeElement(writer, "Type", resource.getResourceType() != null ? resource.getResourceType().toString() : "");
    writer.writeEndElement(); // Resource
  }

  private void writeAssignmentElement(XMLStreamWriter writer, ResourceAssignment assignment) throws Exception {
    writer.writeStartElement("ResourceAssignment");
    writeElement(writer, "ActivityId", assignment.getActivityId().toString());
    writeElement(writer, "ResourceId", assignment.getResourceId().toString());
    writeElement(writer, "PlannedUnits", String.valueOf(assignment.getPlannedUnits() != null ? assignment.getPlannedUnits() : 0.0));
    writeElement(writer, "ActualUnits", String.valueOf(assignment.getActualUnits() != null ? assignment.getActualUnits() : 0.0));
    writeElement(writer, "ProjectId", assignment.getProjectId().toString());
    writer.writeEndElement(); // ResourceAssignment
  }

  private void writeElement(XMLStreamWriter writer, String elementName, String value) throws Exception {
    writer.writeStartElement(elementName);
    if (value != null && !value.isEmpty()) {
      writer.writeCharacters(value);
    }
    writer.writeEndElement();
  }
}
