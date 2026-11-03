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
    private final com.ai.xbot.service.xaccount.XAccountService xAccounts;

    public SystemController(AppProperties properties, SettingsService settingsService,
                            com.ai.xbot.service.xaccount.XAccountService xAccounts) {
        this.properties = properties;
        this.settingsService = settingsService;
        this.xAccounts = xAccounts;
    }

    /** Integration readiness. Reports only whether values are present, never the values. */
    @GetMapping("/status")
    public SystemStatusDto status() {
        boolean openAi = properties.openai() != null && properties.openai().configured();
        var xs = xAccounts.status();
        boolean x = xs.appConfigured() && xs.encryptionKeyConfigured();
        return new SystemStatusDto(
                "0.5.0",
                settingsService.getSchedule().timezone(),
                openAi,
                openAi ? properties.openai().model() : null,
                x,
                xs.connected(),
                settingsService.isPublishingEnabled(),
                "PHASE_5");
    }
}
