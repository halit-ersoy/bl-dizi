package com.ses.bldizi.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
@SuppressWarnings({"SqlResolve", "SqlNoDataSourceInspection", "unused"})
public class SystemSettingService {
    private static final Logger logger = LoggerFactory.getLogger(SystemSettingService.class);

    @Autowired(required = false)
    private JdbcTemplate jdbcTemplate;

    private volatile boolean cachedMaintenance = false;
    private volatile long lastCheckTime = 0;

    public boolean isMaintenanceMode() {
        long now = System.currentTimeMillis();
        // 3 saniye yerel onbellek (performans)
        if (now - lastCheckTime < 3000) {
            return cachedMaintenance;
        }

        if (jdbcTemplate != null) {
            try {
                String val = jdbcTemplate.queryForObject(
                        "SELECT SettingValue FROM SystemSettings WHERE SettingKey = 'maintenance_mode_bldizi'",
                        String.class
                );
                cachedMaintenance = "true".equalsIgnoreCase(val);
                lastCheckTime = now;
                return cachedMaintenance;
            } catch (Exception e) {
                logger.debug("SystemSettings bldizi bakim modu okunamadi: {}", e.getMessage());
            }
        }

        lastCheckTime = now;
        return cachedMaintenance;
    }

    public void setMaintenanceMode(boolean active) {
        if (jdbcTemplate != null) {
            try {
                int updated = jdbcTemplate.update(
                        "UPDATE SystemSettings SET SettingValue = ? WHERE SettingKey = 'maintenance_mode_bldizi'",
                        String.valueOf(active)
                );
                if (updated == 0) {
                    jdbcTemplate.update(
                            "INSERT INTO SystemSettings (SettingKey, SettingValue) VALUES ('maintenance_mode_bldizi', ?)",
                            String.valueOf(active)
                    );
                }
            } catch (Exception e) {
                logger.error("DB bldizi bakim modu guncelleme hatasi: {}", e.getMessage());
            }
        }
        this.cachedMaintenance = active;
        this.lastCheckTime = System.currentTimeMillis();
    }

    public boolean isRegistrationEnabled() {
        if (jdbcTemplate != null) {
            try {
                String val = jdbcTemplate.queryForObject(
                        "SELECT SettingValue FROM SystemSettings WHERE SettingKey = 'registration_enabled'",
                        String.class
                );
                return val == null || "true".equalsIgnoreCase(val);
            } catch (Exception e) {
                return true;
            }
        }
        return true;
    }

    public boolean isAdVideoEnabled() {
        if (jdbcTemplate != null) {
            try {
                String val = jdbcTemplate.queryForObject(
                        "SELECT SettingValue FROM SystemSettings WHERE SettingKey = 'ad_video_enabled'",
                        String.class
                );
                return val == null || !"false".equalsIgnoreCase(val.trim());
            } catch (Exception e) {
                return true;
            }
        }
        return true;
    }

    public int getAdProbability() {
        if (jdbcTemplate != null) {
            try {
                String val = jdbcTemplate.queryForObject(
                        "SELECT SettingValue FROM SystemSettings WHERE SettingKey = 'ad_probability'",
                        String.class
                );
                if (val != null) {
                    return Integer.parseInt(val.trim());
                }
            } catch (Exception e) {
                logger.debug("SystemSettings ad_probability okunamadi: {}", e.getMessage());
            }
        }
        return 30;
    }
}
