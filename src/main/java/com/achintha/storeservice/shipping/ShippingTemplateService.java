package com.achintha.storeservice.shipping;

import com.achintha.storeservice.audit.AuditService;
import com.achintha.storeservice.common.PageResponse;
import com.achintha.storeservice.common.PublicIdGenerator;
import com.achintha.storeservice.common.TextSanitizer;
import com.achintha.storeservice.courier.CourierService;
import com.achintha.storeservice.exception.ApiException;
import com.achintha.storeservice.exception.ErrorCode;
import com.achintha.storeservice.exception.NotFoundException;
import com.achintha.storeservice.security.Actor;
import com.achintha.storeservice.shipping.ShippingDtos.ShippingEstimateResponse;
import com.achintha.storeservice.shipping.ShippingDtos.ShippingTemplateRequest;
import com.achintha.storeservice.shipping.ShippingDtos.ShippingTemplateResponse;
import com.achintha.storeservice.store.Store;
import com.achintha.storeservice.store.StoreService;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Shipping templates (owner only, one per enabled courier) and the shipping estimate for order-service. */
@Service
@RequiredArgsConstructor
public class ShippingTemplateService {

    private final ShippingTemplateRepository repository;
    private final CourierService courierService;
    private final StoreService storeService;
    private final PublicIdGenerator publicIds;
    private final AuditService auditService;
    private final Clock clock;

    @Transactional(readOnly = true)
    public PageResponse<ShippingTemplateResponse> list(Actor actor, Pageable pageable) {
        return PageResponse.from(repository.findAllByStoreId(StoreService.storeIdOf(actor), pageable),
                ShippingTemplateResponse::from);
    }

    @Transactional(readOnly = true)
    public ShippingTemplateResponse get(Actor actor, String publicId) {
        return ShippingTemplateResponse.from(require(actor, publicId));
    }

    @Transactional
    public ShippingTemplateResponse create(Actor actor, ShippingTemplateRequest request) {
        Store store = storeService.requireOwnStore(actor);
        courierService.requireEnabled(store.getId(), request.courierCode());
        if (repository.existsByStoreIdAndCourierCode(store.getId(), request.courierCode())) {
            throw templateExists();
        }
        Instant now = clock.instant();
        ShippingTemplate template = new ShippingTemplate();
        template.setId(UUID.randomUUID());
        template.setPublicId(publicIds.generateUnique(PublicIdGenerator.SHIPPING_TEMPLATE_PREFIX,
                repository::existsByPublicId));
        template.setStoreId(store.getId());
        template.setCreatedAt(now);
        apply(template, request, now);
        template = save(template);
        auditService.record(actor, "SHIPPING_TEMPLATE_CREATED", AuditService.TARGET_SHIPPING_TEMPLATE,
                template.getPublicId(), null, snapshot(template), null);
        return ShippingTemplateResponse.from(template);
    }

    @Transactional
    public ShippingTemplateResponse update(Actor actor, String publicId, ShippingTemplateRequest request) {
        ShippingTemplate template = require(actor, publicId);
        Map<String, Object> before = snapshot(template);
        if (!template.getCourierCode().equals(request.courierCode())) {
            courierService.requireEnabled(template.getStoreId(), request.courierCode());
            if (repository.existsByStoreIdAndCourierCode(template.getStoreId(), request.courierCode())) {
                throw templateExists();
            }
        }
        apply(template, request, clock.instant());
        template = save(template);
        auditService.record(actor, "SHIPPING_TEMPLATE_UPDATED", AuditService.TARGET_SHIPPING_TEMPLATE,
                template.getPublicId(), before, snapshot(template), null);
        return ShippingTemplateResponse.from(template);
    }

    @Transactional
    public void delete(Actor actor, String publicId) {
        ShippingTemplate template = require(actor, publicId);
        repository.delete(template);
        auditService.record(actor, "SHIPPING_TEMPLATE_DELETED", AuditService.TARGET_SHIPPING_TEMPLATE,
                template.getPublicId(), snapshot(template), null, null);
    }

    /**
     * Estimated courier charge for order-service. The courier must be enabled for the store and have an active
     * template; otherwise 422 {@code SHIPPING_ESTIMATE_UNAVAILABLE}.
     */
    @Transactional(readOnly = true)
    public ShippingEstimateResponse estimate(UUID storeId, String courierCode, String district, BigDecimal weightKg) {
        courierService.requireEnabled(storeId, courierCode);
        ShippingTemplate template = repository.findByStoreIdAndCourierCode(storeId, courierCode)
                .filter(ShippingTemplate::isActive)
                .orElseThrow(() -> unavailable("The store has no active shipping template for " + courierCode));
        BigDecimal amount = switch (template.getRuleType()) {
            case FLAT -> template.getBaseAmount();
            case PER_DISTRICT -> {
                if (district == null || district.isBlank()) {
                    throw ApiException.badRequest(ErrorCode.BAD_REQUEST, "district is required for this courier");
                }
                yield template.getRates().stream()
                        .filter(rate -> rate.district().equalsIgnoreCase(district.strip()))
                        .map(ShippingRate::amount)
                        .findFirst()
                        .orElse(template.getBaseAmount());
            }
            case WEIGHT_BAND -> {
                if (weightKg == null || weightKg.signum() <= 0) {
                    throw ApiException.badRequest(ErrorCode.BAD_REQUEST, "weight (kg, > 0) is required for this "
                            + "courier");
                }
                yield template.getRates().stream()
                        .filter(rate -> weightKg.compareTo(rate.maxWeightKg()) <= 0)
                        .map(ShippingRate::amount)
                        .findFirst()
                        .orElse(template.getBaseAmount());
            }
        };
        if (amount == null) {
            throw unavailable("No shipping rate matches this destination or weight");
        }
        return new ShippingEstimateResponse(courierCode, template.getRuleType(), district, weightKg, amount, "LKR",
                true);
    }

    // ------------------------------------------------------------------------------------------------- helpers

    private ShippingTemplate require(Actor actor, String publicId) {
        return repository.findByPublicIdAndStoreId(publicId, StoreService.storeIdOf(actor))
                .orElseThrow(() -> new NotFoundException("Shipping template not found"));
    }

    private ShippingTemplate save(ShippingTemplate template) {
        try {
            return repository.saveAndFlush(template);
        } catch (DataIntegrityViolationException e) {
            throw templateExists();
        }
    }

    private static void apply(ShippingTemplate template, ShippingTemplateRequest request, Instant now) {
        String name = TextSanitizer.cleanLine(request.name());
        if (name == null) {
            throw invalid("name must not be blank");
        }
        List<ShippingRate> rates = request.rates() == null ? List.of() : request.rates();
        List<ShippingRate> normalized = switch (request.ruleType()) {
            case FLAT -> {
                if (request.baseAmount() == null) {
                    throw invalid("baseAmount is required for FLAT templates");
                }
                if (!rates.isEmpty()) {
                    throw invalid("FLAT templates have no rates");
                }
                yield List.of();
            }
            case PER_DISTRICT -> perDistrict(rates);
            case WEIGHT_BAND -> weightBands(rates);
        };
        template.setCourierCode(request.courierCode());
        template.setName(name);
        template.setRuleType(request.ruleType());
        template.setBaseAmount(request.baseAmount());
        template.setRates(normalized);
        template.setActive(request.active() == null || request.active());
        template.setUpdatedAt(now);
    }

    private static List<ShippingRate> perDistrict(List<ShippingRate> rates) {
        if (rates.isEmpty()) {
            throw invalid("PER_DISTRICT templates need at least one district rate");
        }
        Set<String> seen = new HashSet<>();
        List<ShippingRate> result = new ArrayList<>();
        for (ShippingRate rate : rates) {
            String district = TextSanitizer.cleanLine(rate.district());
            if (district == null || rate.maxWeightKg() != null) {
                throw invalid("every PER_DISTRICT rate needs a district (and no maxWeightKg)");
            }
            if (!seen.add(district.toLowerCase(Locale.ROOT))) {
                throw invalid("district " + district + " appears twice");
            }
            result.add(new ShippingRate(district, null, rate.amount()));
        }
        return List.copyOf(result);
    }

    private static List<ShippingRate> weightBands(List<ShippingRate> rates) {
        if (rates.isEmpty()) {
            throw invalid("WEIGHT_BAND templates need at least one band");
        }
        Set<BigDecimal> seen = new HashSet<>();
        List<ShippingRate> result = new ArrayList<>();
        for (ShippingRate rate : rates) {
            if (rate.maxWeightKg() == null || rate.district() != null) {
                throw invalid("every WEIGHT_BAND rate needs maxWeightKg (and no district)");
            }
            if (!seen.add(rate.maxWeightKg().stripTrailingZeros())) {
                throw invalid("band " + rate.maxWeightKg() + " kg appears twice");
            }
            result.add(new ShippingRate(null, rate.maxWeightKg(), rate.amount()));
        }
        result.sort(Comparator.comparing(ShippingRate::maxWeightKg));
        return List.copyOf(result);
    }

    private static ApiException invalid(String message) {
        return ApiException.badRequest(ErrorCode.VALIDATION_FAILED, message);
    }

    private static ApiException templateExists() {
        return ApiException.conflict(ErrorCode.SHIPPING_TEMPLATE_EXISTS,
                "The store already has a shipping template for this courier");
    }

    private static ApiException unavailable(String message) {
        return new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, ErrorCode.SHIPPING_ESTIMATE_UNAVAILABLE, message);
    }

    private static Map<String, Object> snapshot(ShippingTemplate template) {
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("publicId", template.getPublicId());
        state.put("courierCode", template.getCourierCode());
        state.put("name", template.getName());
        state.put("ruleType", template.getRuleType());
        state.put("baseAmount", template.getBaseAmount());
        state.put("rates", template.getRates());
        state.put("active", template.isActive());
        return state;
    }
}
