package com.achintha.storeservice.fee;

import com.achintha.storeservice.common.PageResponse;
import com.achintha.storeservice.common.Validation;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;

public final class FeeDtos {

    private FeeDtos() {
    }

    /** @param amount LKR, positive, at most 2 decimals */
    public record CreditRequest(
            @NotNull @DecimalMin("0.01") @DecimalMax("1000000") @Digits(integer = 7, fraction = 2) BigDecimal amount,
            @NotBlank @Size(max = Validation.REASON_MAX) String reason) {
    }

    public record CreditResponse(String publicId, String storePublicId, BigDecimal amount, String reason,
                                 Instant createdAt, BigDecimal balance) {
    }

    public record LedgerEntryResponse(LedgerEntryType type, BigDecimal amount, String orderPublicId,
                                      String description, Instant createdAt) {

        static LedgerEntryResponse from(FeeLedgerEntry entry) {
            return new LedgerEntryResponse(entry.getEntryType(), entry.getAmount(), entry.getOrderPublicId(),
                    entry.getDescription(), entry.getCreatedAt());
        }
    }

    /**
     * @param balance  sum of all lines (fees minus credits); informational only
     * @param enforced always {@code false} (D11)
     */
    public record LedgerResponse(String storePublicId, BigDecimal balance, boolean enforced,
                                 PageResponse<LedgerEntryResponse> entries) {
    }
}
