package com.ai.xbot.repository;

import com.ai.xbot.entity.ApplicationSetting;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ApplicationSettingRepository extends JpaRepository<ApplicationSetting, String> {
}
