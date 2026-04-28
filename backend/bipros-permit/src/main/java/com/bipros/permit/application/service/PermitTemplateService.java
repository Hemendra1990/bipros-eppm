package com.bipros.permit.application.service;

import com.bipros.common.exception.ResourceNotFoundException;
import com.bipros.permit.application.dto.ApprovalStepTemplateDto;
import com.bipros.permit.application.dto.PermitPackDto;
import com.bipros.permit.application.dto.PermitTypeTemplateDto;
import com.bipros.permit.application.dto.PpeItemTemplateDto;
import com.bipros.permit.domain.model.ApprovalStepTemplate;
import com.bipros.permit.domain.model.PermitPack;
import com.bipros.permit.domain.model.PermitPackType;
import com.bipros.permit.domain.model.PermitTypePpe;
import com.bipros.permit.domain.model.PermitTypeTemplate;
import com.bipros.permit.domain.model.PpeItemTemplate;
import com.bipros.permit.domain.repository.ApprovalStepTemplateRepository;
import com.bipros.permit.domain.repository.PermitPackRepository;
import com.bipros.permit.domain.repository.PermitPackTypeRepository;
import com.bipros.permit.domain.repository.PermitTypePpeRepository;
import com.bipros.permit.domain.repository.PermitTypeTemplateRepository;
import com.bipros.permit.domain.repository.PpeItemTemplateRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class PermitTemplateService {

    private final PermitPackRepository packRepository;
    private final PermitPackTypeRepository packTypeRepository;
    private final PermitTypeTemplateRepository typeRepository;
    private final PpeItemTemplateRepository ppeItemRepository;
    private final PermitTypePpeRepository typePpeRepository;
    private final ApprovalStepTemplateRepository approvalStepRepository;
    private final PermitMapper mapper;

    @Transactional(readOnly = true)
    public List<PermitPackDto> listPacks() {
        return packRepository.findAllByActiveTrueOrderBySortOrderAsc().stream().map(mapper::toDto).toList();
    }

    @Transactional(readOnly = true)
    public List<PermitTypeTemplateDto> listTypesForPack(String packCode) {
        PermitPack pack = packRepository.findByCode(packCode)
                .orElseThrow(() -> new ResourceNotFoundException("PermitPack", packCode));
        List<PermitPackType> links = packTypeRepository.findByPackIdOrderBySortOrderAsc(pack.getId());
        return links.stream()
                .map(l -> typeRepository.findById(l.getPermitTypeTemplateId()).orElse(null))
                .filter(t -> t != null)
                .map(mapper::toDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<PermitTypeTemplateDto> listAllTypes() {
        return typeRepository.findAllByOrderBySortOrderAsc().stream().map(mapper::toDto).toList();
    }

    @Transactional(readOnly = true)
    public PermitTypeTemplateDto getType(UUID id) {
        PermitTypeTemplate t = typeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("PermitTypeTemplate", id));
        return mapper.toDto(t);
    }

    @Transactional(readOnly = true)
    public List<PpeItemTemplateDto> listPpeItemsForType(UUID typeId) {
        if (!typeRepository.existsById(typeId)) {
            throw new ResourceNotFoundException("PermitTypeTemplate", typeId);
        }
        List<PermitTypePpe> links = typePpeRepository.findByPermitTypeTemplateId(typeId);
        List<UUID> itemIds = links.stream().map(PermitTypePpe::getPpeItemTemplateId).toList();
        return ppeItemRepository.findAllById(itemIds).stream().map(mapper::toDto).toList();
    }

    @Transactional(readOnly = true)
    public List<PpeItemTemplateDto> listAllPpeItems() {
        return ppeItemRepository.findAllByOrderBySortOrderAsc().stream().map(mapper::toDto).toList();
    }

    @Transactional(readOnly = true)
    public List<ApprovalStepTemplateDto> listApprovalStepsForType(UUID typeId) {
        if (!typeRepository.existsById(typeId)) {
            throw new ResourceNotFoundException("PermitTypeTemplate", typeId);
        }
        return approvalStepRepository.findByPermitTypeTemplateIdOrderByStepNoAsc(typeId).stream()
                .map(mapper::toDto).toList();
    }

    // ── Admin CRUD ────────────────────────────────────────────────────────

    public PermitPackDto createPack(PermitPackDto dto) {
        PermitPack p = new PermitPack();
        applyPack(p, dto);
        return mapper.toDto(packRepository.save(p));
    }

    public PermitPackDto updatePack(UUID id, PermitPackDto dto) {
        PermitPack p = packRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("PermitPack", id));
        applyPack(p, dto);
        return mapper.toDto(packRepository.save(p));
    }

    private void applyPack(PermitPack p, PermitPackDto dto) {
        p.setCode(dto.code());
        p.setName(dto.name());
        p.setDescription(dto.description());
        p.setActive(dto.active());
        p.setSortOrder(dto.sortOrder());
    }

    public PermitTypeTemplateDto createType(PermitTypeTemplateDto dto) {
        PermitTypeTemplate t = new PermitTypeTemplate();
        applyType(t, dto);
        return mapper.toDto(typeRepository.save(t));
    }

    public PermitTypeTemplateDto updateType(UUID id, PermitTypeTemplateDto dto) {
        PermitTypeTemplate t = typeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("PermitTypeTemplate", id));
        applyType(t, dto);
        return mapper.toDto(typeRepository.save(t));
    }

    private void applyType(PermitTypeTemplate t, PermitTypeTemplateDto dto) {
        t.setCode(dto.code());
        t.setName(dto.name());
        t.setDescription(dto.description());
        t.setDefaultRiskLevel(dto.defaultRiskLevel());
        t.setJsaRequired(dto.jsaRequired());
        t.setGasTestRequired(dto.gasTestRequired());
        t.setIsolationRequired(dto.isolationRequired());
        t.setBlastingRequired(dto.blastingRequired());
        t.setDivingRequired(dto.divingRequired());
        t.setNightWorkPolicy(dto.nightWorkPolicy());
        t.setMaxDurationHours(dto.maxDurationHours());
        t.setMinApprovalRole(dto.minApprovalRole());
        t.setColorHex(dto.colorHex());
        t.setIconKey(dto.iconKey());
        t.setSortOrder(dto.sortOrder());
    }

    public PpeItemTemplateDto createPpeItem(PpeItemTemplateDto dto) {
        PpeItemTemplate i = new PpeItemTemplate();
        applyPpe(i, dto);
        return mapper.toDto(ppeItemRepository.save(i));
    }

    public PpeItemTemplateDto updatePpeItem(UUID id, PpeItemTemplateDto dto) {
        PpeItemTemplate i = ppeItemRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("PpeItemTemplate", id));
        applyPpe(i, dto);
        return mapper.toDto(ppeItemRepository.save(i));
    }

    private void applyPpe(PpeItemTemplate i, PpeItemTemplateDto dto) {
        i.setCode(dto.code());
        i.setName(dto.name());
        i.setIconKey(dto.iconKey());
        i.setMandatory(dto.mandatory());
        i.setSortOrder(dto.sortOrder());
    }

    /** Admin-only: link a permit type into a pack. */
    public void addTypeToPack(UUID packId, UUID typeId, int sortOrder) {
        if (!packRepository.existsById(packId)) throw new ResourceNotFoundException("PermitPack", packId);
        if (!typeRepository.existsById(typeId)) throw new ResourceNotFoundException("PermitTypeTemplate", typeId);
        packTypeRepository.findByPackIdAndPermitTypeTemplateId(packId, typeId).orElseGet(() -> {
            PermitPackType l = new PermitPackType();
            l.setPackId(packId);
            l.setPermitTypeTemplateId(typeId);
            l.setSortOrder(sortOrder);
            return packTypeRepository.save(l);
        });
    }

    /** Admin-only: link a PPE item to a permit type. */
    public void addPpeToType(UUID typeId, UUID ppeId, boolean required) {
        if (!typeRepository.existsById(typeId)) throw new ResourceNotFoundException("PermitTypeTemplate", typeId);
        if (!ppeItemRepository.existsById(ppeId)) throw new ResourceNotFoundException("PpeItemTemplate", ppeId);
        typePpeRepository.findByPermitTypeTemplateIdAndPpeItemTemplateId(typeId, ppeId).orElseGet(() -> {
            PermitTypePpe l = new PermitTypePpe();
            l.setPermitTypeTemplateId(typeId);
            l.setPpeItemTemplateId(ppeId);
            l.setRequired(required);
            return typePpeRepository.save(l);
        });
    }

    /** Admin-only: define an approval step for a permit type. */
    public void addApprovalStep(UUID typeId, int stepNo, String label, String role,
                                String requiredForRiskLevels, boolean optional) {
        if (!typeRepository.existsById(typeId)) throw new ResourceNotFoundException("PermitTypeTemplate", typeId);
        ApprovalStepTemplate s = new ApprovalStepTemplate();
        s.setPermitTypeTemplateId(typeId);
        s.setStepNo(stepNo);
        s.setLabel(label);
        s.setRole(role);
        s.setRequiredForRiskLevels(requiredForRiskLevels);
        s.setOptional(optional);
        approvalStepRepository.save(s);
    }
}
