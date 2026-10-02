package com.achintha.storeservice.settings;

import com.achintha.storeservice.audit.AuditService;
import com.achintha.storeservice.common.PageResponse;
import com.achintha.storeservice.common.TextSanitizer;
import com.achintha.storeservice.exception.ApiException;
import com.achintha.storeservice.exception.ErrorCode;
import com.achintha.storeservice.outbox.StoreEventPublisher;
import com.achintha.storeservice.security.Actor;
import com.achintha.storeservice.settings.SettingsDtos.InternalSetting;
import com.achintha.storeservice.settings.SettingsDtos.SettingHistoryResponse;
import com.achintha.storeservice.settings.SettingsDtos.SettingResponse;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Platform settings (section 7): super-admin updates with type/range validation, history, audit and a
 * {@code SettingsChanged} event; reads go through an in-memory snapshot cached for {@code app.settings.cache-ttl}
 * (60 s). An update on this replica drops the snapshot as soon as it commits; other replicas pick it up within the
 * TTL. A key missing from the table falls back to its contract default.
 */
@Service
public class PlatformSettingsService {

    private final PlatformSettingRepository settingRepository;
    private final PlatformSettingHistoryRepository historyRepository;
    private final AuditService auditService;
    private final StoreEventPublisher events;
    private final Clock clock;
    private final Duration cacheTtl;

    private volatile Snapshot snapshot;

    public PlatformSettingsService(PlatformSettingRepository settingRepository,
                                   PlatformSettingHistoryRepository historyRepository, AuditService auditService,
                                   StoreEventPublisher events, Clock clock,
                                   @Value("${app.settings.cache-ttl:PT60S}") Duration cacheTtl) {
        this.settingRepository = settingRepository;
        this.historyRepository = historyRepository;
        this.auditService = auditService;
        this.events = events;
        this.clock = clock;
        this.cacheTtl = cacheTtl;
    }

    private record Snapshot(Map<String, PlatformSetting> settings, Instant loadedAt) {
    }

    // ---------------------------------------------------------------------------------------------------- reads

    public int intValue(SettingKey key) {
        return Integer.parseInt(value(key));
    }

    public BigDecimal decimalValue(SettingKey key) {
        return new BigDecimal(value(key));
    }

    public boolean booleanValue(SettingKey key) {
        return Boolean.parseBoolean(value(key));
    }

    public String value(SettingKey key) {
        PlatformSetting setting = current().settings().get(key.key());
        return setting != null ? setting.getValue() : key.defaultValue();
    }

    public List<SettingResponse> list() {
        Map<String, PlatformSetting> settings = current().settings();
        return Arrays.stream(SettingKey.values()).map(key -> toResponse(key, settings.get(key.key()))).toList();
    }

    public List<InternalSetting> internalList() {
        return Arrays.stream(SettingKey.values())
                .map(key -> new InternalSetting(key.key(), value(key), key.type()))
                .toList();
    }

    @Transactional(readOnly = true)
    public PageResponse<SettingHistoryResponse> history(String key, Pageable pageable) {
        SettingKey settingKey = require(key);
        return PageResponse.from(historyRepository.findAllByKey(settingKey.key(), pageable),
                SettingHistoryResponse::from);
    }

    // --------------------------------------------------------------------------------------------------- update

    @Transactional
    public SettingResponse update(Actor actor, String key, String rawValue, String rawReason) {
        SettingKey settingKey = require(key);
        String value = SettingValidator.normalize(settingKey, rawValue);
        String reason = TextSanitizer.cleanLine(rawReason);
        Instant now = clock.instant();

        PlatformSetting setting = settingRepository.findById(settingKey.key()).orElseGet(() -> {
            PlatformSetting created = new PlatformSetting();
            created.setKey(settingKey.key());
            created.setValueType(settingKey.type());
            return created;
        });
        String oldValue = setting.getValue();
        if (value.equals(oldValue)) {
            return toResponse(settingKey, setting);
        }
        setting.setValue(value);
        setting.setUpdatedAt(now);
        setting.setUpdatedBy(actor.publicId());
        setting = settingRepository.save(setting);

        PlatformSettingHistory history = new PlatformSettingHistory();
        history.setKey(settingKey.key());
        history.setOldValue(oldValue);
        history.setNewValue(value);
        history.setChangedBy(actor.publicId());
        history.setChangedAt(now);
        history.setReason(reason);
        historyRepository.save(history);

        auditService.record(actor, "SETTING_UPDATED", AuditService.TARGET_SETTING, settingKey.key(),
                oldValue == null ? null : Map.of("value", oldValue), Map.of("value", value), reason);
        events.settingsChanged(settingKey.key(), value);
        invalidateAfterCommit();
        return toResponse(settingKey, setting);
    }

    /** Drops the cached snapshot (also used by tests). */
    public void invalidate() {
        snapshot = null;
    }

    // ------------------------------------------------------------------------------------------------- helpers

    private Snapshot current() {
        Snapshot current = snapshot;
        Instant now = clock.instant();
        if (current == null || !now.isBefore(current.loadedAt().plus(cacheTtl))) {
            Map<String, PlatformSetting> settings = settingRepository.findAll().stream()
                    .collect(Collectors.toUnmodifiableMap(PlatformSetting::getKey, Function.identity()));
            current = new Snapshot(settings, now);
            snapshot = current;
        }
        return current;
    }

    private void invalidateAfterCommit() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    invalidate();
                }
            });
        } else {
            invalidate();
        }
    }

    private static SettingKey require(String key) {
        return SettingKey.fromKey(key).orElseThrow(() -> new ApiException(
                HttpStatus.NOT_FOUND, ErrorCode.UNKNOWN_SETTING, "Unknown setting: " + key));
    }

    private static SettingResponse toResponse(SettingKey key, PlatformSetting setting) {
        return new SettingResponse(key.key(), setting != null ? setting.getValue() : key.defaultValue(), key.type(),
                key.defaultValue(), key.min(), key.max(), setting != null ? setting.getUpdatedAt() : null,
                setting != null ? setting.getUpdatedBy() : null);
    }
}
