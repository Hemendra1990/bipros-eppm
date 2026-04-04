package com.bipros.project.application.service;

import com.bipros.common.exception.BusinessRuleException;
import com.bipros.common.exception.ResourceNotFoundException;
import com.bipros.project.application.dto.CreateWbsTemplateRequest;
import com.bipros.project.application.dto.WbsTemplateResponse;
import com.bipros.project.domain.model.AssetClass;
import com.bipros.project.domain.model.WbsNode;
import com.bipros.project.domain.model.WbsTemplate;
import com.bipros.project.domain.repository.WbsNodeRepository;
import com.bipros.project.domain.repository.WbsTemplateRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@Transactional
@RequiredArgsConstructor
@Slf4j
public class WbsTemplateService {

    private final WbsTemplateRepository wbsTemplateRepository;
    private final WbsNodeRepository wbsNodeRepository;
    private final ObjectMapper objectMapper;

    public List<WbsTemplateResponse> listTemplates() {
        log.info("Listing all active WBS templates");
        return wbsTemplateRepository.findByIsActiveTrueOrderByAssetClassAscNameAsc()
            .stream()
            .map(WbsTemplateResponse::from)
            .toList();
    }

    public List<WbsTemplateResponse> listTemplatesByAssetClass(AssetClass assetClass) {
        log.info("Listing WBS templates for asset class: {}", assetClass);
        return wbsTemplateRepository.findByAssetClassOrderByNameAsc(assetClass)
            .stream()
            .filter(WbsTemplate::getIsActive)
            .map(WbsTemplateResponse::from)
            .toList();
    }

    public WbsTemplateResponse getTemplate(UUID id) {
        log.info("Fetching WBS template: {}", id);
        WbsTemplate template = wbsTemplateRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("WbsTemplate", id));
        return WbsTemplateResponse.from(template);
    }

    public WbsTemplateResponse createTemplate(CreateWbsTemplateRequest request) {
        log.info("Creating WBS template with code: {}", request.code());

        // Check if code already exists
        if (wbsTemplateRepository.findByCode(request.code()).isPresent()) {
            throw new BusinessRuleException("TEMPLATE_CODE_EXISTS", "Template with code " + request.code() + " already exists");
        }

        // Validate JSON structure
        try {
            objectMapper.readTree(request.defaultStructure());
        } catch (Exception e) {
            throw new BusinessRuleException("INVALID_JSON", "Default structure must be valid JSON");
        }

        WbsTemplate template = new WbsTemplate();
        template.setCode(request.code());
        template.setName(request.name());
        template.setAssetClass(request.assetClass());
        template.setDescription(request.description());
        template.setDefaultStructure(request.defaultStructure());
        template.setIsActive(request.isActive() != null ? request.isActive() : true);

        WbsTemplate saved = wbsTemplateRepository.save(template);
        log.info("WBS template created with ID: {}", saved.getId());

        return WbsTemplateResponse.from(saved);
    }

    public void applyTemplate(UUID projectId, UUID templateId) {
        log.info("Applying WBS template: {} to project: {}", templateId, projectId);

        WbsTemplate template = wbsTemplateRepository.findById(templateId)
            .orElseThrow(() -> new ResourceNotFoundException("WbsTemplate", templateId));

        try {
            JsonNode structureNode = objectMapper.readTree(template.getDefaultStructure());
            createNodesFromTemplate(projectId, structureNode, null, template.getAssetClass());
            log.info("WBS template applied successfully to project: {}", projectId);
        } catch (Exception e) {
            log.error("Error applying template: {}", e.getMessage(), e);
            throw new BusinessRuleException("TEMPLATE_APPLY_ERROR", "Failed to apply template: " + e.getMessage());
        }
    }

    private void createNodesFromTemplate(UUID projectId, JsonNode node, UUID parentId, AssetClass assetClass) throws Exception {
        if (node.isArray()) {
            for (JsonNode item : node) {
                createNodeFromJson(projectId, item, parentId, assetClass);
            }
        } else {
            createNodeFromJson(projectId, node, parentId, assetClass);
        }
    }

    private void createNodeFromJson(UUID projectId, JsonNode nodeJson, UUID parentId, AssetClass assetClass) throws Exception {
        String code = nodeJson.get("code").asText();
        String name = nodeJson.get("name").asText();

        WbsNode wbsNode = new WbsNode();
        wbsNode.setCode(code);
        wbsNode.setName(name);
        wbsNode.setParentId(parentId);
        wbsNode.setProjectId(projectId);
        wbsNode.setAssetClass(assetClass);
        wbsNode.setSortOrder(0);

        WbsNode savedNode = wbsNodeRepository.save(wbsNode);

        // Process children recursively
        if (nodeJson.has("children") && nodeJson.get("children").isArray()) {
            createNodesFromTemplate(projectId, nodeJson.get("children"), savedNode.getId(), assetClass);
        }
    }

    public void seedDefaultTemplates() {
        log.info("Seeding default WBS templates");

        // ROAD template
        if (wbsTemplateRepository.findByCode("ROAD").isEmpty()) {
            String roadStructure = """
                [
                  {
                    "code": "ROAD",
                    "name": "Road Project",
                    "level": 0,
                    "children": [
                      {
                        "code": "ROAD.1",
                        "name": "Pre-Construction",
                        "level": 1,
                        "children": [
                          {"code": "ROAD.1.1", "name": "Survey & Investigation", "level": 2},
                          {"code": "ROAD.1.2", "name": "Land Acquisition", "level": 2},
                          {"code": "ROAD.1.3", "name": "Utility Shifting", "level": 2}
                        ]
                      },
                      {
                        "code": "ROAD.2",
                        "name": "Earthwork",
                        "level": 1,
                        "children": [
                          {"code": "ROAD.2.1", "name": "Cutting", "level": 2},
                          {"code": "ROAD.2.2", "name": "Embankment", "level": 2}
                        ]
                      },
                      {
                        "code": "ROAD.3",
                        "name": "Pavement",
                        "level": 1,
                        "children": [
                          {"code": "ROAD.3.1", "name": "Sub-Base", "level": 2},
                          {"code": "ROAD.3.2", "name": "Base Course", "level": 2},
                          {"code": "ROAD.3.3", "name": "Surface Course", "level": 2}
                        ]
                      },
                      {
                        "code": "ROAD.4",
                        "name": "Structures",
                        "level": 1,
                        "children": [
                          {"code": "ROAD.4.1", "name": "Minor Bridges", "level": 2},
                          {"code": "ROAD.4.2", "name": "Major Bridges", "level": 2},
                          {"code": "ROAD.4.3", "name": "Culverts", "level": 2}
                        ]
                      },
                      {
                        "code": "ROAD.5",
                        "name": "Protection Works",
                        "level": 1
                      },
                      {
                        "code": "ROAD.6",
                        "name": "Traffic Management",
                        "level": 1
                      }
                    ]
                  }
                ]
                """;

            WbsTemplate roadTemplate = new WbsTemplate();
            roadTemplate.setCode("ROAD");
            roadTemplate.setName("Road Infrastructure Project");
            roadTemplate.setAssetClass(AssetClass.ROAD);
            roadTemplate.setDescription("Standard WBS template for road infrastructure projects");
            roadTemplate.setDefaultStructure(roadStructure);
            roadTemplate.setIsActive(true);
            wbsTemplateRepository.save(roadTemplate);
            log.info("ROAD template seeded");
        }

        // BUILDING template
        if (wbsTemplateRepository.findByCode("BUILDING").isEmpty()) {
            String buildingStructure = """
                [
                  {
                    "code": "BLDG",
                    "name": "Building Project",
                    "level": 0,
                    "children": [
                      {
                        "code": "BLDG.1",
                        "name": "Design & Planning",
                        "level": 1,
                        "children": [
                          {"code": "BLDG.1.1", "name": "Architectural Design", "level": 2},
                          {"code": "BLDG.1.2", "name": "Engineering Design", "level": 2},
                          {"code": "BLDG.1.3", "name": "Approvals & Permits", "level": 2}
                        ]
                      },
                      {
                        "code": "BLDG.2",
                        "name": "Site Preparation",
                        "level": 1,
                        "children": [
                          {"code": "BLDG.2.1", "name": "Land Clearing", "level": 2},
                          {"code": "BLDG.2.2", "name": "Foundation Work", "level": 2}
                        ]
                      },
                      {
                        "code": "BLDG.3",
                        "name": "Construction",
                        "level": 1,
                        "children": [
                          {"code": "BLDG.3.1", "name": "Structural Work", "level": 2},
                          {"code": "BLDG.3.2", "name": "MEP Installation", "level": 2},
                          {"code": "BLDG.3.3", "name": "Interior Finishing", "level": 2}
                        ]
                      },
                      {
                        "code": "BLDG.4",
                        "name": "Commissioning & Handover",
                        "level": 1,
                        "children": [
                          {"code": "BLDG.4.1", "name": "Testing & Commissioning", "level": 2},
                          {"code": "BLDG.4.2", "name": "Final Inspection", "level": 2},
                          {"code": "BLDG.4.3", "name": "Handover", "level": 2}
                        ]
                      }
                    ]
                  }
                ]
                """;

            WbsTemplate buildingTemplate = new WbsTemplate();
            buildingTemplate.setCode("BUILDING");
            buildingTemplate.setName("Building Construction Project");
            buildingTemplate.setAssetClass(AssetClass.BUILDING);
            buildingTemplate.setDescription("Standard WBS template for building construction projects");
            buildingTemplate.setDefaultStructure(buildingStructure);
            buildingTemplate.setIsActive(true);
            wbsTemplateRepository.save(buildingTemplate);
            log.info("BUILDING template seeded");
        }

        // POWER template
        if (wbsTemplateRepository.findByCode("POWER").isEmpty()) {
            String powerStructure = """
                [
                  {
                    "code": "PWR",
                    "name": "Power Infrastructure Project",
                    "level": 0,
                    "children": [
                      {
                        "code": "PWR.1",
                        "name": "Engineering & Design",
                        "level": 1,
                        "children": [
                          {"code": "PWR.1.1", "name": "Survey & Analysis", "level": 2},
                          {"code": "PWR.1.2", "name": "Design & Engineering", "level": 2}
                        ]
                      },
                      {
                        "code": "PWR.2",
                        "name": "Land & Environmental",
                        "level": 1,
                        "children": [
                          {"code": "PWR.2.1", "name": "Land Acquisition", "level": 2},
                          {"code": "PWR.2.2", "name": "Environmental Clearance", "level": 2}
                        ]
                      },
                      {
                        "code": "PWR.3",
                        "name": "Infrastructure Construction",
                        "level": 1,
                        "children": [
                          {"code": "PWR.3.1", "name": "Generation Unit", "level": 2},
                          {"code": "PWR.3.2", "name": "Transmission Lines", "level": 2},
                          {"code": "PWR.3.3", "name": "Substation", "level": 2}
                        ]
                      },
                      {
                        "code": "PWR.4",
                        "name": "Commissioning",
                        "level": 1
                      }
                    ]
                  }
                ]
                """;

            WbsTemplate powerTemplate = new WbsTemplate();
            powerTemplate.setCode("POWER");
            powerTemplate.setName("Power Infrastructure Project");
            powerTemplate.setAssetClass(AssetClass.POWER);
            powerTemplate.setDescription("Standard WBS template for power infrastructure projects");
            powerTemplate.setDefaultStructure(powerStructure);
            powerTemplate.setIsActive(true);
            wbsTemplateRepository.save(powerTemplate);
            log.info("POWER template seeded");
        }
    }
}
