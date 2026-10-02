package com.achintha.storeservice.store;

import com.achintha.storeservice.audit.AuditService;
import com.achintha.storeservice.common.PublicIdGenerator;
import com.achintha.storeservice.common.Slugs;
import com.achintha.storeservice.common.TextSanitizer;
import com.achintha.storeservice.exception.ApiException;
import com.achintha.storeservice.exception.ErrorCode;
import com.achintha.storeservice.exception.NotFoundException;
import com.achintha.storeservice.outbox.StoreEventPublisher;
import com.achintha.storeservice.ports.ImageStoragePort;
import com.achintha.storeservice.security.Actor;
import com.achintha.storeservice.security.SecurityState;
import com.achintha.storeservice.security.UserStatus;
import com.achintha.storeservice.store.StoreRequests.StoreProfileRequest;
import com.achintha.storeservice.store.StoreResponses.MerchantStoreResponse;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Store profile (owner only), COD switch, and the visibility reactions to the owner's status. */
@Slf4j
@Service
@RequiredArgsConstructor
public class StoreService {

    private final StoreRepository storeRepository;
    private final PublicIdGenerator publicIds;
    private final StoreEventPublisher events;
    private final AuditService auditService;
    private final ImageStoragePort imageStorage;
    private final StoreMapper mapper;
    private final Clock clock;

    // ---------------------------------------------------------------------------------------------- merchant side

    /** The caller's store (from the JWT {@code storeId}), or 409 {@code STORE_PROFILE_REQUIRED}. */
    @Transactional(readOnly = true)
    public Store requireOwnStore(Actor actor) {
        return storeRepository.findById(storeIdOf(actor)).orElseThrow(() -> ApiException.conflict(
                ErrorCode.STORE_PROFILE_REQUIRED, "Create the store profile first (PUT /api/merchant/store)"));
    }

    @Transactional(readOnly = true)
    public MerchantStoreResponse getOwn(Actor actor) {
        return storeRepository.findById(storeIdOf(actor)).map(mapper::toMerchant)
                .orElseThrow(() -> new NotFoundException("The store profile has not been created yet"));
    }

    /**
     * Creates the store under the JWT {@code storeId} the first time, then replaces the profile. A pending merchant
     * may edit, but the store is published only once the owner is {@code ACTIVE} and the profile is complete.
     *
     * @param ownerState the owner's real status as resolved by the SecurityStateFilter, if known
     */
    @Transactional
    public MerchantStoreResponse upsertProfile(Actor actor, StoreProfileRequest request, SecurityState ownerState) {
        UUID storeId = storeIdOf(actor);
        String name = TextSanitizer.cleanLine(request.name());
        if (name == null || name.length() < 3) {
            throw ApiException.badRequest(ErrorCode.VALIDATION_FAILED, "name must contain at least 3 characters");
        }
        if (request.logoKey() != null && !imageStorage.exists(request.logoKey())) {
            throw ApiException.badRequest(ErrorCode.VALIDATION_FAILED, "logoKey does not refer to an uploaded image");
        }
        if (storeRepository.nameTakenByOther(name, storeId)) {
            throw ApiException.conflict(ErrorCode.STORE_NAME_TAKEN, "A store with this name already exists");
        }

        Instant now = clock.instant();
        Store store = storeRepository.findById(storeId).orElse(null);
        boolean created = store == null;
        Map<String, Object> before = created ? null : snapshot(store);
        if (created) {
            store = new Store();
            store.setId(storeId);
            store.setPublicId(publicIds.generateUnique(PublicIdGenerator.STORE_PREFIX,
                    storeRepository::existsByPublicId));
            store.setOwnerUserId(actor.id());
            // Unknown (user-service unreachable): treated as pending until a UserStatusChanged event arrives
            store.setOwnerStatus(UserStatus.PENDING_APPROVAL);
            store.setCreatedAt(now);
        } else if (!store.getOwnerUserId().equals(actor.id())) {
            // A storeId belongs to exactly one merchant; never let another account take a store over
            throw new NotFoundException("Store not found");
        }
        if (ownerState != null && ownerState.status() != null) {
            store.setOwnerStatus(ownerState.status());
        }
        if (created || !name.equalsIgnoreCase(store.getName())) {
            store.setSlug(uniqueSlug(name, store.getSlug()));
        }
        store.setName(name);
        store.setDescription(TextSanitizer.clean(request.description()));
        store.setContactPhone(request.contactPhone());
        store.setContactEmail(request.contactEmail() == null ? null
                : request.contactEmail().strip().toLowerCase(Locale.ROOT));
        store.setAddressLine(TextSanitizer.cleanLine(request.addressLine()));
        store.setCity(TextSanitizer.cleanLine(request.city()));
        store.setDistrict(TextSanitizer.cleanLine(request.district()));
        store.setLogoKey(request.logoKey());
        store.setUpdatedAt(now);
        boolean visibilityChanged = store.recomputeVisibility();

        try {
            store = storeRepository.saveAndFlush(store);
        } catch (DataIntegrityViolationException e) {
            throw ApiException.conflict(ErrorCode.STORE_NAME_TAKEN, "A store with this name already exists");
        }
        if (created || visibilityChanged) {
            events.visibilityChanged(store, created ? "CREATED" : "PROFILE_UPDATED");
        }
        auditService.record(actor, created ? "STORE_CREATED" : "STORE_PROFILE_UPDATED", AuditService.TARGET_STORE,
                store.getPublicId(), before, snapshot(store), null);
        return mapper.toMerchant(store);
    }

    @Transactional
    public MerchantStoreResponse setCod(Actor actor, boolean enabled) {
        Store store = requireOwnStore(actor);
        if (store.isCodEnabled() != enabled) {
            Map<String, Object> before = Map.of("codEnabled", store.isCodEnabled());
            store.setCodEnabled(enabled);
            store.setUpdatedAt(clock.instant());
            storeRepository.save(store);
            auditService.record(actor, enabled ? "STORE_COD_ENABLED" : "STORE_COD_DISABLED",
                    AuditService.TARGET_STORE, store.getPublicId(), before, Map.of("codEnabled", enabled), null);
        }
        return mapper.toMerchant(store);
    }

    // ------------------------------------------------------------------------------------------- user-events side

    /**
     * Reacts to the owner's {@code UserStatusChanged}: {@code BAN_GRACE} stops new orders (still published),
     * {@code BANNED} unpublishes, {@code ACTIVE} (approval, unban) publishes a complete profile again. Emits
     * {@code StoreVisibilityChanged} when the visibility flags change.
     *
     * @return whether the visibility changed
     */
    @Transactional
    public boolean applyOwnerStatus(UUID storeId, UUID ownerUserId, UserStatus status) {
        Store store = storeRepository.findById(storeId).orElse(null);
        if (store == null) {
            // No profile yet: the status is read from user-service when the profile is first saved
            return false;
        }
        if (!store.getOwnerUserId().equals(ownerUserId)) {
            log.warn("Ignoring a status change for store {}: user {} is not its owner", storeId, ownerUserId);
            return false;
        }
        UserStatus previous = store.getOwnerStatus();
        store.setOwnerStatus(status);
        boolean changed = store.recomputeVisibility();
        if (changed || previous != status) {
            store.setUpdatedAt(clock.instant());
            storeRepository.save(store);
        }
        if (changed) {
            events.visibilityChanged(store, "OWNER_" + status.name());
            auditService.record(Actor.system(), "STORE_VISIBILITY_CHANGED", AuditService.TARGET_STORE,
                    store.getPublicId(), Map.of("ownerStatus", previous),
                    Map.of("ownerStatus", status, "published", store.isPublished(),
                            "acceptingOrders", store.isAcceptingOrders()), "Owner status changed to " + status);
        }
        return changed;
    }

    // ------------------------------------------------------------------------------------------------- helpers

    public static UUID storeIdOf(Actor actor) {
        if (actor.storeId() == null) {
            throw ApiException.forbidden(ErrorCode.ACCESS_DENIED, "This account has no store");
        }
        return actor.storeId();
    }

    /** The name's slug, or the slug plus a short random suffix when another store already uses it. */
    private String uniqueSlug(String name, String current) {
        String base = Slugs.from(name);
        String candidate = base;
        for (int attempt = 0; !candidate.equals(current) && storeRepository.existsBySlug(candidate); attempt++) {
            if (attempt == 10) {
                throw new IllegalStateException("Could not generate a unique slug");
            }
            candidate = base + "-" + publicIds.randomSuffix(4).toLowerCase(Locale.ROOT);
        }
        return candidate;
    }

    static Map<String, Object> snapshot(Store store) {
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("publicId", store.getPublicId());
        state.put("name", store.getName());
        state.put("slug", store.getSlug());
        state.put("contactPhone", store.getContactPhone());
        state.put("contactEmail", store.getContactEmail());
        state.put("addressLine", store.getAddressLine());
        state.put("city", store.getCity());
        state.put("district", store.getDistrict());
        state.put("logoKey", store.getLogoKey());
        state.put("codEnabled", store.isCodEnabled());
        state.put("published", store.isPublished());
        return state;
    }
}
