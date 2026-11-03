package com.ai.xbot.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.ai.xbot.audit.AuditService;
import com.ai.xbot.dto.ContentSettingsDto;
import com.ai.xbot.dto.ScheduleSettingsDto;
import com.ai.xbot.entity.ApplicationSetting;
import com.ai.xbot.entity.enums.ActorType;
import com.ai.xbot.exception.BadRequestException;
import com.ai.xbot.repository.ApplicationSettingRepository;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SettingsServiceTest {

    @Mock ApplicationSettingRepository repository;
    @Mock AuditService auditService;

    private final Map<String, ApplicationSetting> store = new HashMap<>();
    private SettingsService service;
    private final UUID actor = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        put(SettingsService.TIMEZONE, "Asia/Kolkata");
        put(SettingsService.MORNING, "08:00");
        put(SettingsService.AFTERNOON, "13:00");
        put(SettingsService.EVENING, "19:00");
        put(SettingsService.ENABLED_LANGUAGES, "ta,en");
        put(SettingsService.LANGUAGE_MIX, "ta:50,en:50");
        put(SettingsService.VARIATIONS, "2");
        put(SettingsService.LOOKBACK_DAYS, "30");
        put(SettingsService.PUBLISHING_ENABLED, "false");
        when(repository.findById(anyString())).thenAnswer(i -> Optional.ofNullable(store.get(i.<String>getArgument(0))));
        service = new SettingsService(repository, auditService);
    }

    private void put(String k, String v) { store.put(k, new ApplicationSetting(k, v, null)); }

    @Test
    void readsDefaults() {
        assertThat(service.getSchedule()).isEqualTo(new ScheduleSettingsDto("Asia/Kolkata", "08:00", "13:00", "19:00"));
        ContentSettingsDto content = service.getContent();
        assertThat(content.enabledLanguages()).containsExactly("ta", "en");
        assertThat(content.languageMix()).containsEntry("ta", 50).containsEntry("en", 50);
        assertThat(service.isPublishingEnabled()).isFalse();
    }

    @Test
    void updatesScheduleAndAudits() {
        ScheduleSettingsDto updated = service.updateSchedule(
                new ScheduleSettingsDto("Asia/Kolkata", "07:30", "12:45", "20:00"), actor);
        assertThat(updated.morning()).isEqualTo("07:30");
        verify(auditService).record(eq(actor), eq(ActorType.USER), eq("SETTINGS_SCHEDULE_UPDATED"),
                any(), isNull(), any(), any(), isNull());
    }

    @Test
    void rejectsUnknownTimezone() {
        assertThatThrownBy(() -> service.updateSchedule(
                new ScheduleSettingsDto("Mars/Base", "08:00", "13:00", "19:00"), actor))
                .isInstanceOf(BadRequestException.class);
        verifyNoInteractions(auditService);
    }

    @Test
    void rejectsDuplicateSlotTimes() {
        assertThatThrownBy(() -> service.updateSchedule(
                new ScheduleSettingsDto("Asia/Kolkata", "08:00", "08:00", "19:00"), actor))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("different");
    }

    @Test
    void acceptsTanglishMix() {
        service.updateContent(new ContentSettingsDto(List.of("ta", "en", "mixed"),
                ordered("ta", 40, "en", 40, "mixed", 20), 3, 30), actor);
        assertThat(store.get(SettingsService.LANGUAGE_MIX).getValue()).isEqualTo("ta:40,en:40,mixed:20");
    }

    @Test
    void rejectsMixNotTotalling100() {
        assertThatThrownBy(() -> service.updateContent(new ContentSettingsDto(List.of("ta", "en"),
                ordered("ta", 60, "en", 30), 2, 30), actor))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("100");
    }

    @Test
    void rejectsUnsupportedLanguage() {
        assertThatThrownBy(() -> service.updateContent(new ContentSettingsDto(List.of("ta", "hi"),
                ordered("ta", 50, "hi", 50), 2, 30), actor))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("hi");
    }

    @Test
    void rejectsMixForLanguageThatIsNotEnabled() {
        assertThatThrownBy(() -> service.updateContent(new ContentSettingsDto(List.of("ta"),
                ordered("ta", 50, "en", 50), 2, 30), actor))
                .isInstanceOf(BadRequestException.class);
    }

    private static Map<String, Integer> ordered(Object... kv) {
        Map<String, Integer> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) m.put((String) kv[i], (Integer) kv[i + 1]);
        return m;
    }
}
