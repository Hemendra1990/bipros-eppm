package com.bipros.api.config.seeder;

import com.bipros.admin.domain.model.GlobalSetting;
import com.bipros.admin.domain.repository.GlobalSettingRepository;
import com.bipros.project.application.dto.BoqOperationDto;
import com.bipros.project.application.dto.SplitBoqItemRequest;
import com.bipros.project.application.service.BoqCalculator;
import com.bipros.project.application.service.BoqOperationService;
import com.bipros.project.domain.model.BoqItem;
import com.bipros.project.domain.model.BoqStatus;
import com.bipros.project.domain.model.Project;
import com.bipros.project.domain.repository.BoqItemRepository;
import com.bipros.project.domain.repository.ProjectRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Seeds the client's contract BOQ ("Requirements final - 01 Aug 2026.xlsx", BOQ + BOQ1 sheets)
 * into {@code project.boq_items} / {@code project.boq_operations}. Target project = the first
 * code in the JSON's {@code targetProjectCodes} that exists (production: KHASAB-2026, local dev:
 * KHASAB-001) — the same jar works in both environments with nothing to reconfigure.
 *
 * <p><b>Runs in every profile including production</b> (deliberately NOT gated behind the demo
 * profiles). Safety properties, mirroring {@link ClientRateBookSeeder}:
 * <ul>
 *   <li>Insert-only by (project, itemNo) — rows the client edited or created in-app are NEVER
 *       touched on redeploy.</li>
 *   <li>Splits are applied ONLY to lines this seeder created in the same run. A pre-existing
 *       line (possibly carrying DPR history, an earlier split, or linked activities) is never
 *       split — it is logged and skipped. This makes every {@link BoqOperationService} history
 *       validation trivially pass: no legacyWeight, no activity re-pointing at boot.</li>
 *   <li>Fast-skip marker: GlobalSetting {@code client_boq_version} records the applied version;
 *       warm boots do one lookup. Bump {@code version} in the JSON to apply a revised book
 *       (still insert-only).</li>
 *   <li>Single transaction — a failure seeds nothing halfway and leaves the marker unwritten.
 *       Exceptions from the split service are deliberately NOT caught: catching them would mark
 *       the shared transaction rollback-only and the commit would fail anyway.</li>
 * </ul>
 *
 * <p>Quantities are owner-approved DEFAULTS (the workbook has no quantity column):
 * LS/PS/Included = 1, monthly = 12, measured units = 100. BOQ amounts therefore stay placeholder
 * money until the client enters real quantities in-app. Qty 0 was rejected because the first
 * approved DPR would flip the line to OVERRUN and raise the VO banner (see BoqCalculator /
 * BoqStatus derivation).
 *
 * <p>Order 75: after {@link ClientRateBookSeeder} (70) — no data dependency, just a stable boot
 * story for the two client-book seeders.
 */
@Slf4j
@Component
@Order(75)
@RequiredArgsConstructor
public class ClientBoqSeeder implements CommandLineRunner {

  static final String VERSION_KEY = "client_boq_version";

  private final ProjectRepository projectRepository;
  private final BoqItemRepository boqItemRepository;
  private final BoqOperationService boqOperationService;
  private final GlobalSettingRepository globalSettingRepository;
  private final ObjectMapper objectMapper;

  @Override
  @Transactional
  public void run(String... args) {
    ClientBoq book;
    try {
      book = ClientBoq.load(objectMapper);
    } catch (IOException e) {
      log.error("[ClientBoqSeeder] failed to load {} — client BOQ not seeded",
          ClientBoq.RESOURCE_PATH, e);
      return;
    }

    int applied = globalSettingRepository.findBySettingKey(VERSION_KEY)
        .map(s -> parseIntSafe(s.getSettingValue()))
        .orElse(0);
    if (applied >= book.version()) {
      log.debug("[ClientBoqSeeder] version {} already applied — skipping", applied);
      return;
    }

    Project project = book.targetProjectCodes().stream()
        .map(projectRepository::findByCode)
        .flatMap(Optional::stream)
        .findFirst()
        .orElse(null);
    if (project == null) {
      log.warn("[ClientBoqSeeder] none of the target projects {} exist — BOQ not seeded "
          + "(will retry next boot)", book.targetProjectCodes());
      return;
    }

    int inserted = 0;
    List<String> skippedExisting = new ArrayList<>();
    Map<String, BoqItem> createdThisRun = new HashMap<>();
    for (ClientBoq.Item i : book.items()) {
      if (boqItemRepository.findByProjectIdAndItemNo(project.getId(), i.itemNo()).isPresent()) {
        skippedExisting.add(i.itemNo());
        continue;
      }
      BoqItem item = BoqItem.builder()
          .projectId(project.getId())
          .itemNo(i.itemNo())
          .description(i.description())
          .unit(i.unit())
          .chapter(i.chapter())
          .boqQty(new BigDecimal(i.qty()))
          .boqRate(i.rate() == null ? null : new BigDecimal(i.rate()))
          // budgetedRate deliberately NOT seeded (owner 2026-08-12). It is the project team's
          // internal rate AND the weight of every line in Overall Progress / EV
          // (costPct = Σ earned ÷ Σ budgetedAmount). Copying the contract rate here put
          // 181.6M of placeholder value into that denominator and crushed a 24%-complete
          // project to 0.43%. Left null, seeded lines carry zero EV weight until the team
          // enters the real quantity + budgeted rate, and existing EV numbers are untouched.
          .status(BoqStatus.PENDING)
          .build();
      BoqCalculator.recompute(item);
      createdThisRun.put(i.itemNo(), boqItemRepository.save(item));
      inserted++;
    }

    int splitsApplied = 0;
    List<String> splitsSkipped = new ArrayList<>();
    for (ClientBoq.Split s : book.splits()) {
      BoqItem parent = createdThisRun.get(s.itemNo());
      if (parent == null) {
        // Pre-existing line: never split what this run didn't create — it may carry DPR
        // history, linked activities, or an earlier split (split manually in-app if wanted).
        splitsSkipped.add(s.itemNo());
        continue;
      }
      List<BoqOperationDto> ops = new ArrayList<>();
      int order = 1;
      for (ClientBoq.Op op : s.operations()) {
        ops.add(new BoqOperationDto(null, op.code(), op.name(), parent.getUnit(),
            parent.getBoqQty(), new BigDecimal(op.weight()), op.measure(), null, order++,
            null, null));
      }
      boqOperationService.split(project.getId(), parent.getId(),
          new SplitBoqItemRequest(s.mode(), null, ops, Map.of(), null));
      splitsApplied++;
    }

    GlobalSetting marker = globalSettingRepository.findBySettingKey(VERSION_KEY)
        .orElseGet(GlobalSetting::new);
    marker.setSettingKey(VERSION_KEY);
    marker.setSettingValue(String.valueOf(book.version()));
    marker.setDescription("Applied client BOQ version (source: " + book.source() + ")");
    marker.setCategory("SEEDER");
    globalSettingRepository.save(marker);

    log.info("[ClientBoqSeeder] v{} applied to {} — items inserted={} existing-skipped={} | "
            + "splits applied={} skipped={}",
        book.version(), project.getCode(), inserted, skippedExisting.size(),
        splitsApplied, splitsSkipped.size());
    if (!skippedExisting.isEmpty()) {
      log.info("[ClientBoqSeeder] existing item numbers left untouched: {}", skippedExisting);
    }
    if (!splitsSkipped.isEmpty()) {
      log.info("[ClientBoqSeeder] splits NOT applied (line pre-existed this run — split "
          + "manually in-app if wanted): {}", splitsSkipped);
    }
  }

  private static int parseIntSafe(String s) {
    try {
      return Integer.parseInt(s.trim());
    } catch (RuntimeException e) {
      return 0;
    }
  }
}
