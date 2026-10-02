package com.achintha.storeservice.score;

import com.achintha.storeservice.common.PageResponse;
import com.achintha.storeservice.score.MetricsDtos.AtRiskMerchantResponse;
import com.achintha.storeservice.score.MetricsDtos.MetricsResponse;
import com.achintha.storeservice.score.MetricsDtos.ScoreEventResponse;
import com.achintha.storeservice.security.Actor;
import com.achintha.storeservice.settings.PlatformSettingsService;
import com.achintha.storeservice.settings.SettingKey;
import com.achintha.storeservice.store.Store;
import com.achintha.storeservice.store.StoreRepository;
import com.achintha.storeservice.store.StoreService;
import java.time.Clock;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Read side of score and metrics: the owner's own metrics, the admin at-risk list and score details. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MetricsQueryService {

    private final MerchantMetricsRepository metricsRepository;
    private final MerchantScoreEventRepository scoreEventRepository;
    private final StoreRepository storeRepository;
    private final PlatformSettingsService settings;
    private final Clock clock;

    public MetricsResponse ownMetrics(Actor actor) {
        return metricsOf(StoreService.storeIdOf(actor));
    }

    public MetricsResponse metricsOf(UUID storeId) {
        return MetricsResponse.from(metricsRepository.findById(storeId)
                .orElseGet(() -> MerchantMetrics.empty(storeId, clock.instant())));
    }

    public PageResponse<ScoreEventResponse> scoreEvents(UUID storeId, Pageable pageable) {
        return PageResponse.from(scoreEventRepository.findAllByStoreId(storeId, pageable),
                ScoreEventResponse::from);
    }

    public PageResponse<AtRiskMerchantResponse> atRisk(Pageable pageable) {
        int threshold = settings.intValue(SettingKey.SCORE_MERCHANT_AT_RISK_THRESHOLD);
        Page<MerchantMetrics> page = metricsRepository.findAtRisk(threshold, pageable);
        Map<UUID, Store> stores = storeRepository.findAllById(page.getContent().stream()
                        .map(MerchantMetrics::getStoreId).toList()).stream()
                .collect(Collectors.toMap(Store::getId, Function.identity()));
        return PageResponse.from(page, m -> {
            Store store = stores.get(m.getStoreId());
            return new AtRiskMerchantResponse(store != null ? store.getPublicId() : null,
                    store != null ? store.getName() : null, m.getScore(), threshold, m.getAtRiskSince());
        });
    }
}
