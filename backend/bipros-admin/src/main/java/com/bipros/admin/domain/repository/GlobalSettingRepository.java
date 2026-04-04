package com.bipros.admin.domain.repository;

import com.bipros.admin.domain.model.GlobalSetting;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface GlobalSettingRepository extends JpaRepository<GlobalSetting, UUID> {
    Optional<GlobalSetting> findBySettingKey(String settingKey);

    List<GlobalSetting> findByCategory(String category);
}
