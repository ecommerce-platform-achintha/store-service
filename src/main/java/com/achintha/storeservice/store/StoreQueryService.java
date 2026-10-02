package com.achintha.storeservice.store;

import com.achintha.storeservice.common.PageResponse;
import com.achintha.storeservice.common.Pagination;
import com.achintha.storeservice.exception.NotFoundException;
import com.achintha.storeservice.score.MerchantMetrics;
import com.achintha.storeservice.score.MerchantMetricsRepository;
import com.achintha.storeservice.store.StoreResponses.AdminStoreResponse;
import com.achintha.storeservice.store.StoreResponses.PublicStoreResponse;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Read side: public search/detail (visible stores only) and the admin store list. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StoreQueryService {

    private final StoreRepository storeRepository;
    private final MerchantMetricsRepository metricsRepository;
    private final StoreMapper mapper;

    public PageResponse<PublicStoreResponse> searchPublic(String q, Pageable pageable) {
        Page<Store> page = q == null || q.isBlank()
                ? storeRepository.findVisible(pageable)
                : storeRepository.searchVisible(Pagination.likeContains(q), pageable);
        return PageResponse.from(page, mapper::toPublic);
    }

    public PublicStoreResponse getPublic(String publicId) {
        return mapper.toPublic(requireVisible(publicId));
    }

    /** A published store that accepts orders, or 404 (hidden and banned stores look like unknown ones). */
    public Store requireVisible(String publicId) {
        return storeRepository.findVisibleByPublicId(publicId)
                .orElseThrow(() -> new NotFoundException("Store not found"));
    }

    public Store requireAny(String publicId) {
        return storeRepository.findByPublicId(publicId).orElseThrow(() -> new NotFoundException("Store not found"));
    }

    public PageResponse<AdminStoreResponse> searchAdmin(String q, Pageable pageable) {
        Page<Store> page = q == null || q.isBlank()
                ? storeRepository.findAll(pageable)
                : storeRepository.searchAll(Pagination.likeContains(q), pageable);
        Map<UUID, Integer> scores = metricsRepository
                .findAllById(page.getContent().stream().map(Store::getId).toList()).stream()
                .collect(Collectors.toMap(MerchantMetrics::getStoreId, MerchantMetrics::getScore));
        return PageResponse.from(page, store -> mapper.toAdmin(store, scores.getOrDefault(store.getId(), 0)));
    }

    public AdminStoreResponse getAdmin(String publicId) {
        Store store = requireAny(publicId);
        Integer score = metricsRepository.findById(store.getId()).map(MerchantMetrics::getScore).orElse(0);
        return mapper.toAdmin(store, score);
    }
}
