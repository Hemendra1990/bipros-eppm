package com.bipros.permit.application.service;

import com.bipros.permit.application.dto.ApprovalStepTemplateDto;
import com.bipros.permit.application.dto.GasTestDto;
import com.bipros.permit.application.dto.IsolationPointDto;
import com.bipros.permit.application.dto.LifecycleEventDto;
import com.bipros.permit.application.dto.PermitApprovalDto;
import com.bipros.permit.application.dto.PermitPackDto;
import com.bipros.permit.application.dto.PermitTypeTemplateDto;
import com.bipros.permit.application.dto.PermitWorkerDto;
import com.bipros.permit.application.dto.PpeCheckDto;
import com.bipros.permit.application.dto.PpeItemTemplateDto;
import com.bipros.permit.domain.model.ApprovalStepTemplate;
import com.bipros.permit.domain.model.PermitApproval;
import com.bipros.permit.domain.model.PermitGasTest;
import com.bipros.permit.domain.model.PermitIsolationPoint;
import com.bipros.permit.domain.model.PermitLifecycleEvent;
import com.bipros.permit.domain.model.PermitPack;
import com.bipros.permit.domain.model.PermitPpeCheck;
import com.bipros.permit.domain.model.PermitTypeTemplate;
import com.bipros.permit.domain.model.PermitWorker;
import com.bipros.permit.domain.model.PpeItemTemplate;
import org.springframework.stereotype.Component;

@Component
public class PermitMapper {

    public PermitWorkerDto toDto(PermitWorker w) {
        return new PermitWorkerDto(w.getId(), w.getFullName(), w.getCivilId(), w.getNationality(),
                w.getTrade(), w.getRoleOnPermit(), w.getTrainingCertsJson());
    }

    public PermitApprovalDto toDto(PermitApproval a) {
        return new PermitApprovalDto(a.getId(), a.getStepNo(), a.getLabel(), a.getRole(),
                a.getStatus(), a.getReviewerId(), a.getReviewedAt(), a.getRemarks());
    }

    public PpeCheckDto toDto(PermitPpeCheck c, PpeItemTemplate item) {
        return new PpeCheckDto(c.getId(), c.getPpeItemTemplateId(),
                item != null ? item.getCode() : null,
                item != null ? item.getName() : null,
                c.isConfirmed(), c.getConfirmedBy(), c.getConfirmedAt());
    }

    public GasTestDto toDto(PermitGasTest g) {
        return new GasTestDto(g.getId(), g.getLelPct(), g.getO2Pct(), g.getH2sPpm(), g.getCoPpm(),
                g.getResult(), g.getTestedBy(), g.getTestedAt(), g.getInstrumentSerial());
    }

    public IsolationPointDto toDto(PermitIsolationPoint p) {
        return new IsolationPointDto(p.getId(), p.getIsolationType(), p.getPointLabel(),
                p.getLockNumber(), p.getAppliedAt(), p.getAppliedBy(), p.getRemovedAt(), p.getRemovedBy());
    }

    public LifecycleEventDto toDto(PermitLifecycleEvent e) {
        return new LifecycleEventDto(e.getId(), e.getEventType(), e.getPayloadJson(),
                e.getOccurredAt(), e.getActorUserId());
    }

    public PermitPackDto toDto(PermitPack p) {
        return new PermitPackDto(p.getId(), p.getCode(), p.getName(), p.getDescription(),
                p.isActive(), p.getSortOrder());
    }

    public PermitTypeTemplateDto toDto(PermitTypeTemplate t) {
        return new PermitTypeTemplateDto(t.getId(), t.getCode(), t.getName(), t.getDescription(),
                t.getDefaultRiskLevel(), t.isJsaRequired(), t.isGasTestRequired(),
                t.isIsolationRequired(), t.isBlastingRequired(), t.isDivingRequired(),
                t.getNightWorkPolicy(), t.getMaxDurationHours(), t.getMinApprovalRole(),
                t.getColorHex(), t.getIconKey(), t.getSortOrder());
    }

    public PpeItemTemplateDto toDto(PpeItemTemplate i) {
        return new PpeItemTemplateDto(i.getId(), i.getCode(), i.getName(), i.getIconKey(),
                i.isMandatory(), i.getSortOrder());
    }

    public ApprovalStepTemplateDto toDto(ApprovalStepTemplate s) {
        return new ApprovalStepTemplateDto(s.getId(), s.getPermitTypeTemplateId(), s.getStepNo(),
                s.getLabel(), s.getRole(), s.getRequiredForRiskLevels(), s.isOptional());
    }
}
