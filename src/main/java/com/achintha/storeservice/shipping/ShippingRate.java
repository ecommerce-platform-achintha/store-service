package com.achintha.storeservice.shipping;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/**
 * One rate line of a template: {@code district} for PER_DISTRICT, {@code maxWeightKg} for WEIGHT_BAND.
 *
 * @param amount LKR
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ShippingRate(
        @Size(max = 50) String district,
        @DecimalMin(value = "0.001") @DecimalMax("1000") @Digits(integer = 4, fraction = 3) BigDecimal maxWeightKg,
        @NotNull @DecimalMin("0.00") @DecimalMax("1000000") @Digits(integer = 7, fraction = 2) BigDecimal amount) {
}
