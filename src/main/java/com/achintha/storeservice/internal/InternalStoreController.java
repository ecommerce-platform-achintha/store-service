package com.achintha.storeservice.internal;

import com.achintha.storeservice.bank.BankAccountDtos.InternalBankAccount;
import com.achintha.storeservice.bank.BankAccountService;
import com.achintha.storeservice.courier.CourierDtos.StoreCourierResponse;
import com.achintha.storeservice.courier.CourierService;
import com.achintha.storeservice.exception.NotFoundException;
import com.achintha.storeservice.shipping.ShippingDtos.ShippingEstimateResponse;
import com.achintha.storeservice.shipping.ShippingTemplateService;
import com.achintha.storeservice.store.Store;
import com.achintha.storeservice.store.StoreRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Store data for order-service ({@code ROLE_SERVICE} only; never routed by the gateway; not in the public docs). */
@RestController
@RequestMapping("/internal/stores")
@PreAuthorize("hasAuthority('ROLE_SERVICE')")
@RequiredArgsConstructor
public class InternalStoreController {

    private final StoreRepository storeRepository;
    private final BankAccountService bankAccountService;
    private final CourierService courierService;
    private final ShippingTemplateService shippingTemplateService;

    @GetMapping("/{id}")
    @Transactional(readOnly = true)
    public InternalStoreResponse store(@PathVariable UUID id) {
        return InternalStoreResponse.from(require(id));
    }

    /** Checkout sends the store's public id; this resolves it. */
    @GetMapping("/by-public-id/{publicId}")
    @Transactional(readOnly = true)
    public InternalStoreResponse storeByPublicId(@PathVariable String publicId) {
        return storeRepository.findByPublicId(publicId).map(InternalStoreResponse::from)
                .orElseThrow(() -> new NotFoundException("Store not found"));
    }

    /**
     * Active accounts with full account numbers. order-service shows them only to the order's customer, for the
     * transfer. Never cached by intermediaries.
     */
    @GetMapping("/{id}/bank-accounts")
    public ResponseEntity<List<InternalBankAccount>> bankAccounts(@PathVariable UUID id) {
        require(id);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(bankAccountService.internalActiveAccounts(id));
    }

    /** Enabled couriers with tracking URL template and tracking number regex. */
    @GetMapping("/{id}/couriers")
    public List<StoreCourierResponse> couriers(@PathVariable UUID id) {
        require(id);
        return courierService.storeCouriers(id);
    }

    /** @param weight parcel weight in kg (needed for weight-band templates) */
    @GetMapping("/{id}/shipping-estimate")
    public ShippingEstimateResponse shippingEstimate(@PathVariable UUID id, @RequestParam String courier,
                                                     @RequestParam(required = false) String district,
                                                     @RequestParam(required = false) BigDecimal weight) {
        require(id);
        return shippingTemplateService.estimate(id, courier, district, weight);
    }

    private Store require(UUID id) {
        return storeRepository.findById(id).orElseThrow(() -> new NotFoundException("Store not found"));
    }
}
