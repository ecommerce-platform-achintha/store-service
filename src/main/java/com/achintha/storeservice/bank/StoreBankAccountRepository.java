package com.achintha.storeservice.bank;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/** Every query is scoped by store (BOLA): a foreign account is simply not found. */
public interface StoreBankAccountRepository extends JpaRepository<StoreBankAccount, UUID> {

    Optional<StoreBankAccount> findByPublicIdAndStoreIdAndDeletedAtIsNull(String publicId, UUID storeId);

    Page<StoreBankAccount> findAllByStoreIdAndDeletedAtIsNull(UUID storeId, Pageable pageable);

    List<StoreBankAccount> findAllByStoreIdAndActiveTrueAndDeletedAtIsNullOrderByCreatedAtAsc(UUID storeId);

    long countByStoreIdAndDeletedAtIsNull(UUID storeId);

    boolean existsByPublicId(String publicId);

    boolean existsByBankCode(String bankCode);
}
