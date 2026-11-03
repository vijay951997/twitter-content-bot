package com.ai.xbot.controller;

import com.ai.xbot.dto.ContentSettingsDto;
import com.ai.xbot.dto.ScheduleSettingsDto;
import com.ai.xbot.security.AuthenticatedUser;
import com.ai.xbot.service.SettingsService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/settings")
public class SettingsController {

    private final SettingsService settingsService;

    public SettingsController(SettingsService settingsService) {
        this.settingsService = settingsService;
    }

    @GetMapping("/schedule")
    public ScheduleSettingsDto schedule() {
        return settingsService.getSchedule();
    }

    @PutMapping("/schedule")
    public ScheduleSettingsDto updateSchedule(@Valid @RequestBody ScheduleSettingsDto body,
                                              @AuthenticationPrincipal AuthenticatedUser user) {
        return settingsService.updateSchedule(body, user.getId());
    }

    @GetMapping("/focus")
    public com.ai.xbot.dto.ContentFocusDto focus() {
        return settingsService.getFocus();
    }

    @PutMapping("/focus")
    public com.ai.xbot.dto.ContentFocusDto updateFocus(@Valid @RequestBody com.ai.xbot.dto.ContentFocusDto body,
                                                       @AuthenticationPrincipal AuthenticatedUser user) {
        return settingsService.updateFocus(body, user.getId());
    }

    @GetMapping("/publishing")
    public com.ai.xbot.dto.PublishingSettingsDto publishing() {
        return new com.ai.xbot.dto.PublishingSettingsDto(settingsService.isPublishingEnabled());
    }

    @PutMapping("/publishing")
    public com.ai.xbot.dto.PublishingSettingsDto updatePublishing(@Valid @RequestBody com.ai.xbot.dto.PublishingSettingsDto body,
                                                                 @AuthenticationPrincipal AuthenticatedUser user) {
        return new com.ai.xbot.dto.PublishingSettingsDto(settingsService.setPublishingEnabled(body.enabled(), user.getId()));
    }

    @GetMapping("/content")
    public ContentSettingsDto content() {
        return settingsService.getContent();
    }

    @PutMapping("/content")
    public ContentSettingsDto updateContent(@Valid @RequestBody ContentSettingsDto body,
                                            @AuthenticationPrincipal AuthenticatedUser user) {
        return settingsService.updateContent(body, user.getId());
    }
}
