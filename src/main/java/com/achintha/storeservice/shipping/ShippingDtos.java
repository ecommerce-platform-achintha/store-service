package com.achintha.storeservice.shipping;

import com.achintha.storeservice.common.Validation;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public final class ShippingDtos {

    private ShippingDtos() {
    }

    public record ShippingTemplateRequest(
            @NotBlank @Pattern(regexp = Validation.CODE_REGEX, message = Validation.CODE_MESSAGE) String courierCode,
            @NotBlank @Size(max = 100) String name,
            @NotNull ShippingRuleType ruleType,
            @DecimalMin("0.00") @DecimalMax("1000000") @Digits(integer = 7, fraction = 2) BigDecimal baseAmount,
            @Size(max = 50) List<@Valid @NotNull ShippingRate> rates,
            Boolean active) {
    }

    public record ShippingTemplateResponse(
            String publicId,
            String courierCode,
            String name,
            ShippingRuleType ruleType,
            BigDecimal baseAmount,
            List<ShippingRate> rates,
            boolean active,
            Instant createdAt,
            Instant updatedAt) {

        static ShippingTemplateResponse from(ShippingTemplate template) {
            return new ShippingTemplateResponse(template.getPublicId(), template.getCourierCode(),
                    template.getName(), template.getRuleType(), template.getBaseAmount(), template.getRates(),
                    template.isActive(), template.getCreatedAt(), template.getUpdatedAt());
        }
    }

    /** An estimate only: the merchant enters the real courier charge in the quote. */
    public record ShippingEstimateResponse(
            String courierCode,
            ShippingRuleType ruleType,
            String district,
            BigDecimal weightKg,
            BigDecimal amount,
            String currency,
            boolean estimate) {
    }
}
