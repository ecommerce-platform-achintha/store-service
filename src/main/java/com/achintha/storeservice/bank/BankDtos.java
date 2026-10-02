package com.achintha.storeservice.bank;

import com.achintha.storeservice.common.Validation;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;

public final class BankDtos {

    private BankDtos() {
    }

    public record CreateBankRequest(
            @NotBlank @Pattern(regexp = Validation.CODE_REGEX, message = Validation.CODE_MESSAGE) String code,
            @NotBlank @Size(max = 100) String name,
            Boolean active) {
    }

    public record UpdateBankRequest(@NotBlank @Size(max = 100) String name, @NotNull Boolean active) {
    }

    public record BankResponse(String code, String name, boolean active, Instant createdAt, Instant updatedAt) {

        public static BankResponse from(Bank bank) {
            return new BankResponse(bank.getCode(), bank.getName(), bank.isActive(), bank.getCreatedAt(),
                    bank.getUpdatedAt());
        }
    }
}
