package com.ai.xbot.service;

import com.ai.xbot.audit.AuditService;
import com.ai.xbot.dto.ContentSettingsDto;
import com.ai.xbot.dto.ScheduleSettingsDto;
import com.ai.xbot.entity.ApplicationSetting;
import com.ai.xbot.entity.enums.ActorType;
import com.ai.xbot.entity.enums.ContentLanguage;
import com.ai.xbot.exception.BadRequestException;
import com.ai.xbot.exception.NotFoundException;
import com.ai.xbot.repository.ApplicationSettingRepository;
import java.time.DateTimeException;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Reads and updates dashboard-editable settings. Every change is audited. */
@Service
public class SettingsService {

    public static final String TIMEZONE = "schedule.timezone";
    public static final String MORNING = "schedule.morning";
    public static final String AFTERNOON = "schedule.afternoon";
    public static final String EVENING = "schedule.evening";
    public static final String ENABLED_LANGUAGES = "content.enabled_languages";
    public static final String LANGUAGE_MIX = "content.language_mix";
    public static final String VARIATIONS = "content.variations_per_topic";
    public static final String LOOKBACK_DAYS = "duplicate.lookback_days";
    public static final String PUBLISHING_ENABLED = "publishing.enabled";
    public static final String MAX_GENERATIONS_PER_HOUR = "ai.max_generations_per_hour";

    private final ApplicationSettingRepository repository;
    private final AuditService auditService;

    public SettingsService(ApplicationSettingRepository repository, AuditService auditService) {
        this.repository = repository;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public ScheduleSettingsDto getSchedule() {
        return new ScheduleSettingsDto(value(TIMEZONE), value(MORNING), value(AFTERNOON), value(EVENING));
    }

    @Transactional
    public ScheduleSettingsDto updateSchedule(ScheduleSettingsDto dto, UUID actorId) {
        try {
            ZoneId.of(dto.timezone());
        } catch (DateTimeException e) {
            throw new BadRequestException("Unknown timezone: " + dto.timezone());
        }
        if (new HashSet<>(List.of(dto.morning(), dto.afternoon(), dto.evening())).size() < 3) {
            throw new BadRequestException("Morning, afternoon and evening times must be different");
        }
        ScheduleSettingsDto before = getSchedule();
        set(TIMEZONE, dto.timezone(), actorId);
        set(MORNING, dto.morning(), actorId);
        set(AFTERNOON, dto.afternoon(), actorId);
        set(EVENING, dto.evening(), actorId);
        auditService.record(actorId, ActorType.USER, "SETTINGS_SCHEDULE_UPDATED", "application_settings",
                null, before, dto, null);
        return getSchedule();
    }

    @Transactional(readOnly = true)
    public ContentSettingsDto getContent() {
        List<String> languages = Arrays.stream(value(ENABLED_LANGUAGES).split(","))
                .map(String::trim).filter(s -> !s.isEmpty()).toList();
        Map<String, Integer> mix = new LinkedHashMap<>();
        for (String part : value(LANGUAGE_MIX).split(",")) {
            String[] kv = part.split(":");
            if (kv.length == 2) mix.put(kv[0].trim(), Integer.parseInt(kv[1].trim()));
        }
        return new ContentSettingsDto(languages, mix,
                Integer.parseInt(value(VARIATIONS)), Integer.parseInt(value(LOOKBACK_DAYS)));
    }

    @Transactional
    public ContentSettingsDto updateContent(ContentSettingsDto dto, UUID actorId) {
        validateLanguages(dto);
        ContentSettingsDto before = getContent();
        set(ENABLED_LANGUAGES, String.join(",", dto.enabledLanguages()), actorId);
        set(LANGUAGE_MIX, dto.enabledLanguages().stream()
                .map(l -> l + ":" + dto.languageMix().get(l))
                .collect(Collectors.joining(",")), actorId);
        set(VARIATIONS, dto.variationsPerTopic().toString(), actorId);
        set(LOOKBACK_DAYS, dto.duplicateLookbackDays().toString(), actorId);
        auditService.record(actorId, ActorType.USER, "SETTINGS_CONTENT_UPDATED", "application_settings",
                null, before, dto, null);
        return getContent();
    }

    /** Master publishing switch. Read-only in Phase 1; becomes editable with the X integration (Phase 5). */
    @Transactional(readOnly = true)
    public boolean isPublishingEnabled() {
        return Boolean.parseBoolean(value(PUBLISHING_ENABLED));
    }

    /** Raw value of any setting, for settings without a typed accessor. */
    @Transactional(readOnly = true)
    public Optional<String> rawValue(String key) {
        return repository.findById(key).map(ApplicationSetting::getValue);
    }

    @Transactional(readOnly = true)
    public boolean flag(String key) {
        return rawValue(key).map(Boolean::parseBoolean).orElse(false);
    }

    @Transactional(readOnly = true)
    public int maxGenerationsPerHour() {
        return repository.findById(MAX_GENERATIONS_PER_HOUR)
                .map(ApplicationSetting::getValue).map(Integer::parseInt).orElse(30);
    }

    void validateLanguages(ContentSettingsDto dto) {
        Set<String> enabled = new LinkedHashSet<>();
        for (String code : dto.enabledLanguages()) {
            try {
                ContentLanguage.fromCode(code);
            } catch (IllegalArgumentException e) {
                throw new BadRequestException("Unsupported language: " + code + " (use ta, en or mixed)");
            }
            if (!enabled.add(code)) {
                throw new BadRequestException("Language listed twice: " + code);
            }
        }
        if (!dto.languageMix().keySet().equals(enabled)) {
            throw new BadRequestException("Language mix must list exactly the enabled languages");
        }
        int total = 0;
        for (Integer pct : dto.languageMix().values()) {
            if (pct == null || pct < 0 || pct > 100) {
                throw new BadRequestException("Each language share must be between 0 and 100");
            }
            total += pct;
        }
        if (total != 100) {
            throw new BadRequestException("Language mix must add up to 100 (got " + total + ")");
        }
    }

    private String value(String key) {
        return repository.findById(key)
                .map(ApplicationSetting::getValue)
                .orElseThrow(() -> new NotFoundException("Missing setting: " + key));
    }

    private void set(String key, String newValue, UUID actorId) {
        ApplicationSetting setting = repository.findById(key)
                .orElseThrow(() -> new NotFoundException("Missing setting: " + key));
        setting.update(newValue, actorId);
    }
}
