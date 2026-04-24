package com.bipros.api.config.seeder;

import com.bipros.project.domain.model.BoqItem;
import com.bipros.project.domain.model.Project;
import com.bipros.project.domain.model.Stretch;
import com.bipros.project.domain.model.StretchActivityLink;
import com.bipros.project.domain.model.StretchStatus;
import com.bipros.project.domain.repository.BoqItemRepository;
import com.bipros.project.domain.repository.ProjectRepository;
import com.bipros.project.domain.repository.StretchActivityLinkRepository;
import com.bipros.project.domain.repository.StretchRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/**
 * Seeds 5 x 4-km stretches on NHAI NH-48 (Km 145..165) so PMS MasterData Screen 06 renders
 * realistic data out of the box. Runs after NhaiRoadProjectSeeder has created the project, and
 * distributes the project's BOQ items across stretches in round-robin so the stretch progress
 * rollup endpoint has something to compute on.
 */
@Slf4j
@Component
@Profile("dev")
@Order(960)
@RequiredArgsConstructor
public class IcpmsStretchSeeder implements CommandLineRunner {

    private final ProjectRepository projectRepository;
    private final StretchRepository stretchRepository;
    private final StretchActivityLinkRepository linkRepository;
    private final BoqItemRepository boqItemRepository;

    @Override
    @Transactional
    public void run(String... args) {
        // Seed stretches per project with chainage. Idempotent: projects that already have
        // stretches are skipped, so re-runs after partial seeds don't double-insert.
        int seededProjects = 0;
        for (Project project : projectRepository.findAll()) {
            if (project.getFromChainageM() == null) continue;
            if (!stretchRepository.findByProjectIdOrderByFromChainageM(project.getId()).isEmpty()) {
                continue;
            }
            if (seedForProject(project)) seededProjects++;
        }
        if (seededProjects == 0) {
            log.info("[IC-PMS Stretch] all eligible projects already have stretches, skipping");
        }
    }

    private boolean seedForProject(Project project) {
        if (project.getToChainageM() == null) return false;
        long total = project.getToChainageM() - project.getFromChainageM();
        if (total <= 0) return false;
        int segments = Math.min(5, (int) Math.max(1, total / 4000));
        long segmentSize = total / segments;
        LocalDate baseTarget = project.getPlannedFinishDate() != null
            ? project.getPlannedFinishDate() : LocalDate.now().plusYears(1);

        java.util.List<Stretch> created = new java.util.ArrayList<>(segments);
        for (int i = 0; i < segments; i++) {
            long from = project.getFromChainageM() + (long) i * segmentSize;
            long to = i == segments - 1 ? project.getToChainageM() : from + segmentSize;
            Stretch s = Stretch.builder()
                .projectId(project.getId())
                .stretchCode(String.format("STR-%03d", i + 1))
                .name(String.format("Stretch %d (km %d..%d)", i + 1, from / 1000, to / 1000))
                .fromChainageM(from)
                .toChainageM(to)
                .lengthM(to - from)
                .packageCode("PKG-" + (char) ('A' + i))
                .status(i == 0 ? StretchStatus.ACTIVE : StretchStatus.NOT_STARTED)
                .milestoneName(String.format("Substantial completion — Stretch %d", i + 1))
                .targetDate(baseTarget.minusMonths(segments - i - 1L))
                .build();
            created.add(stretchRepository.save(s));
        }
        linkBoqItems(project, created);
        log.info("[IC-PMS Stretch] seeded {} stretches for project {}", segments, project.getCode());
        return true;
    }

    /**
     * Round-robin the project's BOQ items across stretches so each stretch has a representative
     * slice of the scope. Every BOQ item ends up linked to exactly one stretch, giving the
     * stretch-progress endpoint a meaningful weighted rollup.
     */
    private void linkBoqItems(Project project, List<Stretch> stretches) {
        if (stretches.isEmpty()) return;
        List<BoqItem> items = boqItemRepository.findByProjectIdOrderByItemNoAsc(project.getId());
        if (items.isEmpty()) return;
        int i = 0;
        for (BoqItem item : items) {
            Stretch target = stretches.get(i % stretches.size());
            StretchActivityLink link = new StretchActivityLink();
            link.setStretchId(target.getId());
            link.setBoqItemId(item.getId());
            linkRepository.save(link);
            i++;
        }
    }
}
