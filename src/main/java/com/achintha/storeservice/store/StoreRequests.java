package com.achintha.storeservice.store;

import com.achintha.storeservice.common.Validation;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Request bodies of the merchant store endpoints. Server-controlled fields (status, ratings, ids) are not here. */
public final class StoreRequests {

    private StoreRequests() {
    }

    /**
     * Full replacement of the store profile ({@code PUT}). Contact phone/email, address and district are optional
     * while drafting, but the store is published only once they are all set (and the owner is {@code ACTIVE}).
     */
    public record StoreProfileRequest(
            @NotBlank @Size(min = 3, max = 100) String name,
            @Size(max = Validation.TEXT_MAX) String description,
            @Pattern(regexp = Validation.PHONE_REGEX, message = Validation.PHONE_MESSAGE) String contactPhone,
            @Email @Size(max = 254) String contactEmail,
            @Size(max = 300) String addressLine,
            @Size(max = 100) String city,
            @Size(max = 50) String district,
            @Pattern(regexp = Validation.STORAGE_KEY_REGEX) String logoKey) {
    }

    public record CodRequest(@NotNull Boolean enabled) {
    }
}
