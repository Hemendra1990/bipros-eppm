package com.bipros.ai.voice.dpr;

import com.bipros.activity.domain.model.Activity;
import com.bipros.activity.domain.repository.ActivityRepository;
import com.bipros.ai.provider.LlmProvider;
import com.bipros.ai.provider.LlmProviderConfig;
import com.bipros.ai.provider.LlmProviderConfigRepository;
import com.bipros.ai.provider.ModelCapabilityRegistry;
import com.bipros.ai.provider.OpenAiCompatibleProvider;
import com.bipros.ai.voice.SpeechToTextService;
import com.bipros.project.domain.model.BoqItem;
import com.bipros.project.domain.repository.BoqItemRepository;
import com.bipros.resource.application.dto.role.EquipmentRoleVariantResponse;
import com.bipros.resource.application.dto.role.ManpowerRoleRateResponse;
import com.bipros.resource.application.dto.role.MaterialRoleVariantResponse;
import com.bipros.resource.application.dto.role.RoleAssignmentResponse;
import com.bipros.resource.application.service.role.RoleAssignmentService;
import com.bipros.resource.application.service.role.RoleRateService;
import com.bipros.resource.domain.model.ResourceStatus;
import com.bipros.resource.domain.model.Resource;
import com.bipros.resource.domain.repository.ProjectResourceRepository;
import com.bipros.resource.domain.repository.ResourceRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class DprVoiceFillServiceTest {

    private SpeechToTextService speechToTextService;
    private LlmProviderConfigRepository providerConfigRepository;
    private OpenAiCompatibleProvider provider;
    private DprVoiceFillSchema schemaBuilder;
    private ObjectMapper objectMapper;
    private ResourceRepository resourceRepository;
    private ProjectResourceRepository projectResourceRepository;
    private ActivityRepository activityRepository;
    private BoqItemRepository boqItemRepository;
    private RoleRateService roleRateService;
    private RoleAssignmentService roleAssignmentService;
    private ModelCapabilityRegistry capabilityRegistry;
    private DprVoiceFillService service;

    private final UUID projectId = UUID.randomUUID();
    private final UUID activityId = UUID.randomUUID();
    private final UUID manpowerRoleId = UUID.randomUUID();
    private final UUID manpowerVariantId = UUID.randomUUID();
    private final UUID equipmentRoleId = UUID.randomUUID();
    private final UUID equipmentVariantId = UUID.randomUUID();
    private final UUID materialRoleId = UUID.randomUUID();
    private final UUID materialVariantId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        speechToTextService = mock(SpeechToTextService.class);
        providerConfigRepository = mock(LlmProviderConfigRepository.class);
        provider = mock(OpenAiCompatibleProvider.class);
        schemaBuilder = new DprVoiceFillSchema(new ObjectMapper());
        objectMapper = new ObjectMapper();
        resourceRepository = mock(ResourceRepository.class);
        projectResourceRepository = mock(ProjectResourceRepository.class);
        activityRepository = mock(ActivityRepository.class);
        boqItemRepository = mock(BoqItemRepository.class);
        roleRateService = mock(RoleRateService.class);
        roleAssignmentService = mock(RoleAssignmentService.class);
        capabilityRegistry = new ModelCapabilityRegistry();

        service = new DprVoiceFillService(
            speechToTextService, providerConfigRepository, provider, schemaBuilder,
            objectMapper, resourceRepository, projectResourceRepository,
            activityRepository, boqItemRepository,
            roleRateService, roleAssignmentService, capabilityRegistry);

        when(speechToTextService.transcribe(any(), any(), any())).thenReturn("5 masons, 1 JCB");
        when(providerConfigRepository.findByIsDefaultTrueAndIsActiveTrue())
            .thenReturn(Optional.of(providerConfig("gpt-4o")));
        when(resourceRepository.findByResourceType_CodeAndStatus(any(), any()))
            .thenReturn(List.of());
        when(projectResourceRepository.findByProjectId(any())).thenReturn(List.of());
        when(activityRepository.findByProjectId(any())).thenReturn(List.of());
        when(boqItemRepository.findByProjectIdOrderByItemNoAsc(any())).thenReturn(List.of());
    }

    private LlmProviderConfig providerConfig(String model) {
        LlmProviderConfig cfg = new LlmProviderConfig();
        cfg.setModel(model);
        cfg.setMaxTokens(4096);
        cfg.setTimeoutMs(60000);
        return cfg;
    }

    private DprVoiceFillRequest requestWithActivity() {
        ObjectNode state = objectMapper.createObjectNode();
        state.put("activityId", activityId.toString());
        return new DprVoiceFillRequest(state, List.of(), null);
    }

    @Test
    void loadsManpowerRolesFromRateBookAndPlannedAssignments() {
        RoleAssignmentResponse planned = new RoleAssignmentResponse(
            UUID.randomUUID(), activityId, "Excavation", projectId,
            manpowerRoleId, "Mason", "MANPOWER", manpowerVariantId, "Grade I",
            5, BigDecimal.ONE, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
            BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
            new BigDecimal("500"), "DAY", "RATE", null, null, false);
        when(roleAssignmentService.listForActivity(activityId)).thenReturn(List.of(planned));

        ManpowerRoleRateResponse bookEntry = new ManpowerRoleRateResponse(
            UUID.randomUUID(), manpowerRoleId, "Mason", UUID.randomUUID(), "Skilled",
            UUID.randomUUID(), "Grade II", "DAY", new BigDecimal("500"), true);
        when(roleRateService.listAllManpower()).thenReturn(List.of(bookEntry));

        when(roleRateService.listAllEquipment()).thenReturn(List.of());
        when(roleRateService.listAllMaterial()).thenReturn(List.of());

        when(provider.chat(any(), any())).thenReturn(llmResponse("{}"));

        service.fill(projectId, emptyAudio(), requestWithActivity());

        ArgumentCaptor<LlmProvider.ChatRequest> captor = ArgumentCaptor.forClass(LlmProvider.ChatRequest.class);
        verify(provider).chat(any(), captor.capture());
        String prompt = captor.getValue().messages().get(1).content();
        assertThat(prompt).contains("Manpower roles");
        assertThat(prompt).contains(manpowerVariantId.toString());
        assertThat(prompt).contains(bookEntry.id().toString());
    }

    @Test
    void loadsEquipmentAndMaterialRolesFromRateBook() {
        when(roleAssignmentService.listForActivity(activityId)).thenReturn(List.of());

        EquipmentRoleVariantResponse eqBook = new EquipmentRoleVariantResponse(
            equipmentVariantId, equipmentRoleId, "JCB", "JCB", "3DX", "HOUR",
            new BigDecimal("800"), new BigDecimal("40"), true);
        when(roleRateService.listAllEquipment()).thenReturn(List.of(eqBook));

        MaterialRoleVariantResponse matBook = new MaterialRoleVariantResponse(
            materialVariantId, materialRoleId, "Aggregate", "20mm", "Cum",
            new BigDecimal("1200"), true);
        when(roleRateService.listAllMaterial()).thenReturn(List.of(matBook));

        when(roleRateService.listAllManpower()).thenReturn(List.of());
        when(provider.chat(any(), any())).thenReturn(llmResponse("{}"));

        service.fill(projectId, emptyAudio(), requestWithActivity());

        ArgumentCaptor<LlmProvider.ChatRequest> captor = ArgumentCaptor.forClass(LlmProvider.ChatRequest.class);
        verify(provider).chat(any(), captor.capture());
        String prompt = captor.getValue().messages().get(1).content();
        assertThat(prompt).contains("Equipment roles");
        assertThat(prompt).contains(equipmentVariantId.toString());
        assertThat(prompt).contains("Material roles");
        assertThat(prompt).contains(materialVariantId.toString());
    }

    @Test
    void loadsRateBookEvenWhenActivityIdIsNull() {
        ObjectNode state = objectMapper.createObjectNode();
        DprVoiceFillRequest req = new DprVoiceFillRequest(state, List.of(), null);

        ManpowerRoleRateResponse bookEntry = new ManpowerRoleRateResponse(
            manpowerVariantId, manpowerRoleId, "Mason", UUID.randomUUID(), "Skilled",
            UUID.randomUUID(), "Grade I", "DAY", new BigDecimal("500"), true);
        when(roleRateService.listAllManpower()).thenReturn(List.of(bookEntry));
        when(roleRateService.listAllEquipment()).thenReturn(List.of());
        when(roleRateService.listAllMaterial()).thenReturn(List.of());
        when(roleAssignmentService.listForActivity(any())).thenReturn(List.of());
        when(provider.chat(any(), any())).thenReturn(llmResponse("{}"));

        service.fill(projectId, emptyAudio(), req);

        verify(roleRateService).listAllManpower();
        ArgumentCaptor<LlmProvider.ChatRequest> captor = ArgumentCaptor.forClass(LlmProvider.ChatRequest.class);
        verify(provider).chat(any(), captor.capture());
        String prompt = captor.getValue().messages().get(1).content();
        assertThat(prompt).contains(manpowerVariantId.toString());
    }

    private org.springframework.web.multipart.MultipartFile emptyAudio() {
        org.springframework.web.multipart.MultipartFile audio =
            mock(org.springframework.web.multipart.MultipartFile.class);
        when(audio.isEmpty()).thenReturn(false);
        when(audio.getOriginalFilename()).thenReturn("audio.webm");
        when(audio.getContentType()).thenReturn("audio/webm");
        try {
            when(audio.getBytes()).thenReturn(new byte[]{0, 1, 2});
        } catch (java.io.IOException e) {
            throw new RuntimeException(e);
        }
        return audio;
    }

    private LlmProvider.ChatResponse llmResponse(String content) {
        return new LlmProvider.ChatResponse(content, List.of(), null, "gpt-4o");
    }
}
