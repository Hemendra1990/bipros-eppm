package com.bipros.api.config.seeder;

import com.bipros.admin.domain.model.GlobalSetting;
import com.bipros.admin.domain.repository.GlobalSettingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * IC-PMS — seeds global application settings the /admin/settings page renders.
 *
 * <p>Idempotency: {@code findBySettingKey} is used per-row so re-runs only insert
 * missing keys and never overwrite operator customisations.
 */
@Slf4j
@Component
@Profile("dev")
@Order(109)
@RequiredArgsConstructor
public class IcpmsGlobalSettingsSeeder implements CommandLineRunner {

    private final GlobalSettingRepository settingRepository;

    @Override
    @Transactional
    public void run(String... args) {
        int inserted = 0;
        inserted += upsert("currency", "INR",
                "Default currency for all cost, budget and EVM values (ISO 4217 code).",
                "FINANCIAL");
        inserted += upsert("evm_technique", "EARNED_VALUE",
                "Earned-value technique applied across M9 reports: EARNED_VALUE | EARNED_SCHEDULE.",
                "SCHEDULING");
        inserted += upsert("scheduling_default_option", "RETAINED_LOGIC",
                "Default progress-override behaviour for the CPM scheduler: RETAINED_LOGIC | PROGRESS_OVERRIDE.",
                "SCHEDULING");
        inserted += upsert("default_calendar_code", "Standard",
                "Calendar code used when an activity or WBS does not explicitly specify one.",
                "SCHEDULING");

        if (inserted == 0) {
            log.info("[IC-PMS Settings] all default settings already present, skipping");
        } else {
            log.info("[IC-PMS Settings] seeded {} global settings (total now {})",
                    inserted, settingRepository.count());
        }
    }

    private int upsert(String key, String value, String description, String category) {
        if (settingRepository.findBySettingKey(key).isPresent()) {
            return 0;
        }
        GlobalSetting s = new GlobalSetting();
        s.setSettingKey(key);
        s.setSettingValue(value);
        s.setDescription(description);
        s.setCategory(category);
        settingRepository.save(s);
        return 1;
    }
}
