package com.bipros.api.config;

import com.bipros.common.security.DataScope;
import com.bipros.security.domain.model.Profile;
import com.bipros.security.domain.repository.ProfileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

/**
 * Seeds the ten Sarooj client profiles, transcribed row-by-row from the client's
 * "Requirements final - 01 Aug 2026" workbook, sheets Access-Input / Access-Output
 * (transcription rule: post → CREATE, edit → UPDATE, view → READ, download → EXPORT).
 *
 * <p><b>Insert-only</b>: a profile is created only when its code is absent — unlike the
 * system-default {@link ProfileSeeder} there is NO self-healing, because these are custom
 * ({@code systemDefault=false}) profiles the admin is expected to tune; re-adding codes on
 * boot would fight those edits.
 *
 * <p>Interpretations beyond the sheets (flagged in the design doc, owner-approved):
 * DPR.APPROVE for Project Control / Site Manager / PM (the approval chain needs holders);
 * DOCUMENT.READ for every profile (site staff need specs/method statements); AI.READ for
 * Project Control / Site Manager / PM only; the ISSUE.* family instead of DPR.* on concerns
 * so PM/QS can edit concerns without gaining DPR edit rights. Engineer deliberately has
 * COST.READ but NOT COST.EXPORT (Access-Output: Engineer may view but not download the
 * Activity Costing report). Material/store surfaces run on RESOURCE.* (verified against
 * the bipros-resource controllers' guards). Full ACTIVITY control (create/update/delete/
 * lock/unlock) for PM and Construction Manager: owner request 2026-08-12.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class SaroojProfileSeeder {

    private final ProfileRepository profileRepository;

    public void seed() {
        int created = 0;
        for (ClientProfile cp : PROFILES) {
            if (profileRepository.findByCode(cp.code).isPresent()) continue;
            Profile p = new Profile(cp.code, cp.name, cp.description, cp.legacyRole,
                    false, cp.permissions);
            p.setDataScope(cp.scope.name());
            profileRepository.save(p);
            log.info("Seeded Sarooj profile {} (scope {}, {} permissions)",
                    cp.code, cp.scope, cp.permissions.size());
            created++;
        }
        if (created > 0) log.info("Seeded {} Sarooj client profiles", created);
    }

    private record ClientProfile(String code, String name, String description,
                                 String legacyRole, DataScope scope, Set<String> permissions) {}

    private static final List<ClientProfile> PROFILES = List.of(
            new ClientProfile("SAROOJ_SUPERVISOR", "Sarooj — Supervisor",
                    "Field data entry: files DPRs, deployment, photos, plans, material; sees own work only.",
                    "SUPERVISOR", DataScope.OWN, Set.of(
                    "PROJECT.READ", "PROJECT_MEMBER.READ", "ACTIVITY.READ",
                    "DPR.CREATE", "DPR.READ", "DPR.EXPORT",
                    "ISSUE.CREATE", "ISSUE.READ",
                    "RESOURCE.READ", "RESOURCE.CREATE", "RESOURCE.UPDATE",
                    "DOCUMENT.READ",
                    "REPORT.READ", "REPORT.EXPORT",
                    "DBS.READ", "DBS.EXPORT")),

            new ClientProfile("SAROOJ_ENGINEER", "Sarooj — Engineer",
                    "Files and corrects field data, approves their supervisors' DPRs; sees their "
                            + "Team-tab downline's data. May view but not download Activity Costing.",
                    "SITE_ENGINEER", DataScope.TEAM, Set.of(
                    "PROJECT.READ", "PROJECT_MEMBER.READ", "ACTIVITY.READ", "ACTIVITY.UPDATE", "SCHEDULE.READ",
                    "DPR.CREATE", "DPR.READ", "DPR.UPDATE", "DPR.EXPORT", "DPR.APPROVE",
                    "ISSUE.CREATE", "ISSUE.READ", "ISSUE.UPDATE",
                    "RESOURCE.READ",
                    "DOCUMENT.READ",
                    "COST.READ",
                    "REPORT.READ", "REPORT.EXPORT",
                    "DBS.READ", "DBS.EXPORT")),

            new ClientProfile("SAROOJ_PROJECT_CONTROL", "Sarooj — Project Control Engineer",
                    "The corrections + masters owner: norms, unit rates, BOQ, specs; edits all operational data.",
                    "PROJECT_ENGINEER", DataScope.PROJECT, Set.of(
                    "PROJECT.READ", "PROJECT_MEMBER.READ", "PROJECT.UPDATE",
                    "ACTIVITY.CREATE", "ACTIVITY.READ", "ACTIVITY.UPDATE", "ACTIVITY.DELETE",
                    "ACTIVITY.LOCK", "ACTIVITY.UNLOCK",
                    "SCHEDULE.READ", "SCHEDULE.UPDATE", "BASELINE.READ",
                    "DPR.READ", "DPR.UPDATE", "DPR.EXPORT", "DPR.APPROVE",
                    "ISSUE.CREATE", "ISSUE.READ", "ISSUE.UPDATE",
                    "ADMIN_MASTER.READ", "ADMIN_MASTER.UPDATE",
                    "RESOURCE.CREATE", "RESOURCE.READ", "RESOURCE.UPDATE",
                    "DOCUMENT.CREATE", "DOCUMENT.READ", "DOCUMENT.UPDATE", "DOCUMENT.DELETE",
                    "COST.READ", "COST.EXPORT", "EVM.READ", "EVM.EXPORT",
                    "REPORT.READ", "REPORT.EXPORT",
                    "DBS.READ", "DBS.EXPORT", "DBS.RECOMPUTE",
                    "AI.READ")),

            new ClientProfile("SAROOJ_QS", "Sarooj — Quantity Surveyor",
                    "Sub-contract rates, BOQ rates, drawings; views and downloads everything.",
                    "FINANCE", DataScope.PROJECT, Set.of(
                    "PROJECT.READ", "PROJECT.UPDATE", "PROJECT_MEMBER.READ", "ACTIVITY.READ", "BASELINE.READ",
                    "DPR.READ", "DPR.EXPORT",
                    "ISSUE.READ", "ISSUE.UPDATE",
                    "ADMIN_MASTER.READ", "ADMIN_MASTER.UPDATE",
                    "RESOURCE.READ", "RESOURCE.UPDATE",
                    "DOCUMENT.CREATE", "DOCUMENT.READ", "DOCUMENT.UPDATE", "DOCUMENT.DELETE",
                    "COST.READ", "COST.EXPORT", "EVM.READ",
                    "REPORT.READ", "REPORT.EXPORT",
                    "DBS.READ", "DBS.EXPORT")),

            new ClientProfile("SAROOJ_SITE_MANAGER", "Sarooj — Site Manager",
                    "Field oversight: posts photos, corrects field data, approves; views everything.",
                    "SITE_MANAGER", DataScope.PROJECT, Set.of(
                    "PROJECT.READ", "PROJECT_MEMBER.READ", "ACTIVITY.READ", "ACTIVITY.UPDATE", "BASELINE.READ",
                    "DPR.READ", "DPR.UPDATE", "DPR.EXPORT", "DPR.APPROVE",
                    "ISSUE.CREATE", "ISSUE.READ", "ISSUE.UPDATE",
                    "RESOURCE.READ",
                    "DOCUMENT.CREATE", "DOCUMENT.READ",
                    "COST.READ", "COST.EXPORT", "EVM.READ",
                    "REPORT.READ", "REPORT.EXPORT",
                    "DBS.READ", "DBS.EXPORT",
                    "AI.READ")),

            new ClientProfile("SAROOJ_PM", "Sarooj — Project Manager",
                    "Reader: views and downloads everywhere, posts almost nothing (per the client matrix).",
                    "PROJECT_MANAGER", DataScope.PROJECT, Set.of(
                    "PROJECT.READ", "PROJECT_MEMBER.READ",
                    "ACTIVITY.CREATE", "ACTIVITY.READ", "ACTIVITY.UPDATE", "ACTIVITY.DELETE",
                    "ACTIVITY.LOCK", "ACTIVITY.UNLOCK",
                    "SCHEDULE.READ", "BASELINE.READ",
                    "DPR.READ", "DPR.EXPORT", "DPR.APPROVE",
                    "ISSUE.READ", "ISSUE.UPDATE",
                    "RESOURCE.READ",
                    "DOCUMENT.READ",
                    "COST.READ", "COST.EXPORT", "EVM.READ", "EVM.EXPORT",
                    "RISK.READ",
                    "REPORT.READ", "REPORT.EXPORT",
                    "DBS.READ", "DBS.EXPORT", "DBS.RECOMPUTE",
                    "AI.READ")),

            new ClientProfile("SAROOJ_CONSTRUCTION_MANAGER", "Sarooj — Construction Manager",
                    "PM-level visibility plus field corrections: edits DPRs/activities, approves; "
                            + "no recompute/EVM export/risk/AI (PM-only surfaces).",
                    "CONSTRUCTION_MANAGER", DataScope.PROJECT, Set.of(
                    "PROJECT.READ", "PROJECT_MEMBER.READ",
                    "ACTIVITY.CREATE", "ACTIVITY.READ", "ACTIVITY.UPDATE", "ACTIVITY.DELETE",
                    "ACTIVITY.LOCK", "ACTIVITY.UNLOCK",
                    "SCHEDULE.READ", "BASELINE.READ",
                    "DPR.READ", "DPR.UPDATE", "DPR.EXPORT", "DPR.APPROVE",
                    "ISSUE.CREATE", "ISSUE.READ", "ISSUE.UPDATE",
                    "RESOURCE.READ",
                    "DOCUMENT.CREATE", "DOCUMENT.READ",
                    "COST.READ", "COST.EXPORT", "EVM.READ",
                    "REPORT.READ", "REPORT.EXPORT",
                    "DBS.READ", "DBS.EXPORT")),

            new ClientProfile("SAROOJ_QUALITY_ENGINEER", "Sarooj — Quality Engineer",
                    "Weather + specs/ITP owner: documents, NCRs, checklists.",
                    "QA_QC_ENGINEER", DataScope.PROJECT, Set.of(
                    "PROJECT.READ", "PROJECT_MEMBER.READ", "ACTIVITY.READ",
                    "DPR.CREATE", "DPR.READ",
                    "ISSUE.CREATE", "ISSUE.READ",
                    "DOCUMENT.CREATE", "DOCUMENT.READ", "DOCUMENT.UPDATE", "DOCUMENT.DELETE",
                    "NCR.CREATE", "NCR.READ", "NCR.UPDATE", "NCR.APPROVE",
                    "CHECKLIST.CREATE", "CHECKLIST.READ", "CHECKLIST.UPDATE", "CHECKLIST.APPROVE",
                    "REPORT.READ", "REPORT.EXPORT")),

            new ClientProfile("SAROOJ_STORE_KEEPER", "Sarooj — Store Keeper",
                    "Material store only: receipts (GRN), issue slips, consumption; own store screens.",
                    "STORE_MANAGER", DataScope.OWN, Set.of(
                    "PROJECT.READ", "PROJECT_MEMBER.READ",
                    "RESOURCE.CREATE", "RESOURCE.READ", "RESOURCE.UPDATE",
                    "PROCUREMENT_REQUEST.READ", "PROCUREMENT_REQUEST.APPROVE",
                    "DOCUMENT.READ",
                    "REPORT.READ")),

            new ClientProfile("SAROOJ_DESIGN_COORDINATOR", "Sarooj — Design Coordinator",
                    "Drawings and documents.",
                    "BIM_DATA_COORDINATOR", DataScope.PROJECT, Set.of(
                    "PROJECT.READ", "PROJECT_MEMBER.READ",
                    "DOCUMENT.CREATE", "DOCUMENT.READ", "DOCUMENT.UPDATE", "DOCUMENT.DELETE",
                    "DATA_QUALITY.READ",
                    "REPORT.READ"))
    );
}
