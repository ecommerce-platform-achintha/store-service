package com.achintha.storeservice.courier;

import com.achintha.storeservice.common.Validation;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;

public final class CourierDtos {

    private CourierDtos() {
    }

    /**
     * @param trackingUrlTemplate an https URL containing {@code {trackingNumber}}
     * @param trackingNumberRegex a Java regular expression every tracking number must match in full
     */
    public record CourierRequest(
            @NotBlank @Pattern(regexp = Validation.CODE_REGEX, message = Validation.CODE_MESSAGE) String code,
            @NotBlank @Size(max = 100) String name,
            @NotBlank @Size(max = 500) String trackingUrlTemplate,
            @NotBlank @Size(max = 200) String trackingNumberRegex,
            @NotNull Boolean codSupported,
            Boolean active) {
    }

    public record UpdateCourierRequest(
            @NotBlank @Size(max = 100) String name,
            @NotBlank @Size(max = 500) String trackingUrlTemplate,
            @NotBlank @Size(max = 200) String trackingNumberRegex,
            @NotNull Boolean codSupported,
            @NotNull Boolean active) {
    }

    public record CourierResponse(String code, String name, String trackingUrlTemplate, String trackingNumberRegex,
                                  boolean codSupported, boolean active, Instant createdAt, Instant updatedAt) {

        public static CourierResponse from(Courier courier) {
            return new CourierResponse(courier.getCode(), courier.getName(), courier.getTrackingUrlTemplate(),
                    courier.getTrackingNumberRegex(), courier.isCodSupported(), courier.isActive(),
                    courier.getCreatedAt(), courier.getUpdatedAt());
        }
    }

    /** Replaces the store's enabled couriers. An empty list disables all. */
    public record StoreCouriersRequest(
            @NotNull @Size(max = 20) List<@NotBlank @Pattern(regexp = Validation.CODE_REGEX,
                    message = Validation.CODE_MESSAGE) String> courierCodes) {
    }

    /** A courier the store ships with, as order-service needs it to validate and link tracking numbers. */
    public record StoreCourierResponse(String code, String name, String trackingUrlTemplate,
                                       String trackingNumberRegex, boolean codSupported, boolean active,
                                       Instant enabledAt) {
    }
}
