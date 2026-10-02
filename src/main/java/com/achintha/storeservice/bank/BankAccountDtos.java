package com.achintha.storeservice.bank;

import com.achintha.storeservice.common.Validation;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;

/** Bank account DTOs. Account numbers are masked everywhere except the internal (ROLE_SERVICE) view. */
public final class BankAccountDtos {

    private BankAccountDtos() {
    }

    /** @param accountNumber digits only; spaces and dashes are removed first */
    public record CreateBankAccountRequest(
            @NotBlank @Pattern(regexp = Validation.CODE_REGEX, message = Validation.CODE_MESSAGE) String bankCode,
            @NotBlank @Size(max = 100) String branch,
            @NotBlank @Size(max = 150) String accountName,
            @NotBlank @Size(max = 40) String accountNumber,
            Boolean active) {

        @Override
        public String toString() {
            return "CreateBankAccountRequest[bankCode=" + bankCode + ", accountNumber=****]";
        }
    }

    /** @param accountNumber optional: omit to keep the current number */
    public record UpdateBankAccountRequest(
            @NotBlank @Pattern(regexp = Validation.CODE_REGEX, message = Validation.CODE_MESSAGE) String bankCode,
            @NotBlank @Size(max = 100) String branch,
            @NotBlank @Size(max = 150) String accountName,
            @Size(max = 40) String accountNumber,
            @NotNull Boolean active) {

        @Override
        public String toString() {
            return "UpdateBankAccountRequest[bankCode=" + bankCode + ", accountNumber=****]";
        }
    }

    public record BankAccountResponse(
            String publicId,
            String bankCode,
            String bankName,
            String branch,
            String accountName,
            String accountNumberMasked,
            boolean active,
            Instant createdAt,
            Instant updatedAt) {
    }

    /**
     * Internal view for order-service: the full number, shown only to the order's customer for the transfer.
     */
    public record InternalBankAccount(
            UUID id,
            String publicId,
            String bankCode,
            String bankName,
            String branch,
            String accountName,
            String accountNumber,
            String accountNumberMasked) {

        @Override
        public String toString() {
            return "InternalBankAccount[publicId=" + publicId + ", accountNumber=" + accountNumberMasked + "]";
        }
    }
}
