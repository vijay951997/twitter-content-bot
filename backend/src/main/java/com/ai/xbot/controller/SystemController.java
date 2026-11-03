package com.ai.xbot.controller;

import com.ai.xbot.config.AppProperties;
import com.ai.xbot.dto.SystemStatusDto;
import com.ai.xbot.service.SettingsService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/system")
public class SystemController {

    private final AppProperties properties;
    private final SettingsService settingsService;

    public SystemController(AppProperties properties, SettingsService settingsService) {
        this.properties = properties;
        this.settingsService = settingsService;
    }

    /** Integration readiness. Reports only whether values are present, never the values. */
    @GetMapping("/status")
    public SystemStatusDto status() {
        boolean openAi = properties.openai() != null && properties.openai().configured();
        boolean x = properties.x() != null && properties.x().configured();
        return new SystemStatusDto(
                "0.2.0",
                settingsService.getSchedule().timezone(),
                openAi,
                openAi ? properties.openai().model() : null,
                x,
                false, // X account connection arrives in Phase 5
                settingsService.isPublishingEnabled(),
                "PHASE_2");
    }
}
