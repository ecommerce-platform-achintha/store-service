package com.achintha.storeservice.bank;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A store's bank account for bank-transfer payments. The account number is stored AES-GCM encrypted; reads show it
 * masked. Deleting sets {@code deletedAt} (orders may still point at the account).
 */
@Entity
@Table(name = "store_bank_accounts")
@Getter
@Setter
@NoArgsConstructor
public class StoreBankAccount {

    @Id
    private UUID id;

    @Column(name = "public_id", nullable = false, unique = true, length = 20)
    private String publicId;

    @Column(name = "store_id", nullable = false)
    private UUID storeId;

    @Column(name = "bank_code", nullable = false, length = 20)
    private String bankCode;

    @Column(nullable = false, length = 100)
    private String branch;

    @Column(name = "account_name", nullable = false, length = 150)
    private String accountName;

    @Column(name = "account_number_enc", nullable = false, length = 512)
    private String accountNumberEncrypted;

    @Column(name = "account_last4", nullable = false, length = 4)
    private String accountLast4;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private long version;

    @Override
    public String toString() {
        return "StoreBankAccount[publicId=" + publicId + ", bankCode=" + bankCode + ", account=****" + accountLast4
                + "]";
    }
}
