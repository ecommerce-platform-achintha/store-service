package com.achintha.storeservice.fee;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MerchantCreditRepository extends JpaRepository<MerchantCredit, UUID> {

    boolean existsByPublicId(String publicId);
}
