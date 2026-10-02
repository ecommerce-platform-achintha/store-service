package com.achintha.storeservice.courier;

import com.achintha.storeservice.audit.AuditService;
import com.achintha.storeservice.common.PageResponse;
import com.achintha.storeservice.common.TextSanitizer;
import com.achintha.storeservice.courier.CourierDtos.CourierRequest;
import com.achintha.storeservice.courier.CourierDtos.CourierResponse;
import com.achintha.storeservice.courier.CourierDtos.StoreCourierResponse;
import com.achintha.storeservice.courier.CourierDtos.UpdateCourierRequest;
import com.achintha.storeservice.exception.ApiException;
import com.achintha.storeservice.exception.ErrorCode;
import com.achintha.storeservice.exception.NotFoundException;
import com.achintha.storeservice.security.Actor;
import com.achintha.storeservice.shipping.ShippingTemplateRepository;
import com.achintha.storeservice.store.Store;
import com.achintha.storeservice.store.StoreService;
import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Courier master data (super admin) and the couriers each store ships with (owner). */
@Service
@RequiredArgsConstructor
public class CourierService {

    private final CourierRepository courierRepository;
    private final StoreCourierRepository storeCourierRepository;
    private final ShippingTemplateRepository shippingTemplateRepository;
    private final StoreService storeService;
    private final AuditService auditService;
    private final Clock clock;

    // ------------------------------------------------------------------------------------------- master data

    @Transactional(readOnly = true)
    public PageResponse<CourierResponse> list(Boolean active, Pageable pageable) {
        return PageResponse.from(active == null ? courierRepository.findAll(pageable)
                : courierRepository.findAllByActive(active, pageable), CourierResponse::from);
    }

    @Transactional(readOnly = true)
    public List<CourierResponse> activeCouriers() {
        return courierRepository.findAllByActiveTrueOrderByNameAsc().stream().map(CourierResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public CourierResponse get(String code) {
        return CourierResponse.from(require(code));
    }

    @Transactional
    public CourierResponse create(Actor actor, CourierRequest request) {
        if (courierRepository.existsById(request.code())) {
            throw ApiException.conflict(ErrorCode.COURIER_CODE_TAKEN, "A courier with this code already exists");
        }
        Instant now = clock.instant();
        Courier courier = new Courier();
        courier.setCode(request.code());
        courier.setCreatedAt(now);
        apply(courier, request.name(), request.trackingUrlTemplate(), request.trackingNumberRegex(),
                request.codSupported(), request.active() == null || request.active(), now);
        courier = courierRepository.save(courier);
        auditService.record(actor, "COURIER_CREATED", AuditService.TARGET_COURIER, courier.getCode(), null,
                snapshot(courier), null);
        return CourierResponse.from(courier);
    }

    @Transactional
    public CourierResponse update(Actor actor, String code, UpdateCourierRequest request) {
        Courier courier = require(code);
        Map<String, Object> before = snapshot(courier);
        apply(courier, request.name(), request.trackingUrlTemplate(), request.trackingNumberRegex(),
                request.codSupported(), request.active(), clock.instant());
        auditService.record(actor, "COURIER_UPDATED", AuditService.TARGET_COURIER, courier.getCode(), before,
                snapshot(courier), null);
        return CourierResponse.from(courier);
    }

    @Transactional
    public void delete(Actor actor, String code) {
        Courier courier = require(code);
        if (storeCourierRepository.existsByCourierCode(code) || shippingTemplateRepository.existsByCourierCode(code)) {
            throw ApiException.conflict(ErrorCode.COURIER_IN_USE, "Stores use this courier; deactivate it instead");
        }
        courierRepository.delete(courier);
        auditService.record(actor, "COURIER_DELETED", AuditService.TARGET_COURIER, code, snapshot(courier), null,
                null);
    }

    // ------------------------------------------------------------------------------------------ store couriers

    @Transactional(readOnly = true)
    public List<StoreCourierResponse> storeCouriers(UUID storeId) {
        Map<String, Courier> couriers = courierRepository.findAll().stream()
                .collect(Collectors.toMap(Courier::getCode, Function.identity()));
        return storeCourierRepository.findAllByStoreIdOrderByCourierCodeAsc(storeId).stream()
                .map(sc -> {
                    Courier c = couriers.get(sc.getCourierCode());
                    return new StoreCourierResponse(c.getCode(), c.getName(), c.getTrackingUrlTemplate(),
                            c.getTrackingNumberRegex(), c.isCodSupported(), c.isActive(), sc.getEnabledAt());
                })
                .toList();
    }

    @Transactional(readOnly = true)
    public List<StoreCourierResponse> ownCouriers(Actor actor) {
        return storeCouriers(StoreService.storeIdOf(actor));
    }

    /** Replaces the set of enabled couriers; every code must be an active master courier. */
    @Transactional
    public List<StoreCourierResponse> replaceOwnCouriers(Actor actor, List<String> codes) {
        Store store = storeService.requireOwnStore(actor);
        Set<String> wanted = new LinkedHashSet<>(codes);
        for (String code : wanted) {
            if (courierRepository.findById(code).filter(Courier::isActive).isEmpty()) {
                throw ApiException.badRequest(ErrorCode.COURIER_NOT_AVAILABLE,
                        "Unknown or inactive courier: " + code);
            }
        }
        List<StoreCourier> current = storeCourierRepository.findAllByStoreIdOrderByCourierCodeAsc(store.getId());
        List<String> before = current.stream().map(StoreCourier::getCourierCode).toList();
        Instant now = clock.instant();
        current.stream().filter(sc -> !wanted.contains(sc.getCourierCode())).forEach(storeCourierRepository::delete);
        for (String code : wanted) {
            if (!before.contains(code)) {
                StoreCourier storeCourier = new StoreCourier();
                storeCourier.setStoreId(store.getId());
                storeCourier.setCourierCode(code);
                storeCourier.setEnabledAt(now);
                storeCourierRepository.save(storeCourier);
            }
        }
        storeCourierRepository.flush();
        auditService.record(actor, "STORE_COURIERS_REPLACED", AuditService.TARGET_STORE, store.getPublicId(),
                Map.of("couriers", before), Map.of("couriers", List.copyOf(wanted)), null);
        return storeCouriers(store.getId());
    }

    /** Fails with 400 {@code COURIER_NOT_ENABLED} unless the store has enabled this courier. */
    @Transactional(readOnly = true)
    public void requireEnabled(UUID storeId, String courierCode) {
        if (!storeCourierRepository.existsByStoreIdAndCourierCode(storeId, courierCode)) {
            throw ApiException.badRequest(ErrorCode.COURIER_NOT_ENABLED,
                    "Courier " + courierCode + " is not enabled for this store");
        }
    }

    // ------------------------------------------------------------------------------------------------- helpers

    private Courier require(String code) {
        return courierRepository.findById(code).orElseThrow(() -> new NotFoundException("Courier not found"));
    }

    private static void apply(Courier courier, String rawName, String template, String regex, boolean codSupported,
                              boolean active, Instant now) {
        String name = TextSanitizer.cleanLine(rawName);
        if (name == null) {
            throw ApiException.badRequest(ErrorCode.VALIDATION_FAILED, "name must not be blank");
        }
        courier.setName(name);
        courier.setTrackingUrlTemplate(validateTemplate(template));
        courier.setTrackingNumberRegex(validateRegex(regex));
        courier.setCodSupported(codSupported);
        courier.setActive(active);
        courier.setUpdatedAt(now);
    }

    static String validateTemplate(String raw) {
        String template = raw.strip();
        if (!template.contains(Courier.TRACKING_PLACEHOLDER)) {
            throw ApiException.badRequest(ErrorCode.VALIDATION_FAILED,
                    "trackingUrlTemplate must contain " + Courier.TRACKING_PLACEHOLDER);
        }
        try {
            URI uri = URI.create(template.replace(Courier.TRACKING_PLACEHOLDER, "X"));
            if (!"https".equals(uri.getScheme()) || uri.getHost() == null) {
                throw new IllegalArgumentException("not https");
            }
        } catch (IllegalArgumentException e) {
            throw ApiException.badRequest(ErrorCode.VALIDATION_FAILED,
                    "trackingUrlTemplate must be an absolute https URL");
        }
        return template;
    }

    static String validateRegex(String raw) {
        String regex = raw.strip();
        try {
            Pattern.compile(regex);
        } catch (PatternSyntaxException e) {
            throw ApiException.badRequest(ErrorCode.VALIDATION_FAILED,
                    "trackingNumberRegex is not a valid regular expression");
        }
        return regex;
    }

    private static Map<String, Object> snapshot(Courier courier) {
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("code", courier.getCode());
        state.put("name", courier.getName());
        state.put("trackingUrlTemplate", courier.getTrackingUrlTemplate());
        state.put("trackingNumberRegex", courier.getTrackingNumberRegex());
        state.put("codSupported", courier.isCodSupported());
        state.put("active", courier.isActive());
        return state;
    }
}
