package com.bipros.project.application.startup;

import com.bipros.project.application.service.WbsTemplateService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class ApplicationStartupListener {

    private final WbsTemplateService wbsTemplateService;

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        log.info("Application startup listener triggered - seeding default WBS templates");
        try {
            wbsTemplateService.seedDefaultTemplates();
            log.info("Default WBS templates seeded successfully");
        } catch (Exception e) {
            log.warn("Error seeding default WBS templates: {}", e.getMessage(), e);
            // Don't fail startup if seeding fails
        }
    }
}
