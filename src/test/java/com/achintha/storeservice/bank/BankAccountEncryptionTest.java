package com.achintha.storeservice.bank;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.achintha.storeservice.audit.AuditLogEntry;
import com.achintha.storeservice.audit.AuditLogRepository;
import com.achintha.storeservice.store.Store;
import com.achintha.storeservice.support.IntegrationTest;
import com.achintha.storeservice.support.TestUser;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/** Store bank accounts: AES-GCM at rest, masked on every merchant read and in the audit log, full only internally. */
class BankAccountEncryptionTest extends IntegrationTest {

    private static final String NUMBER = "0081234567890";

    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private AuditLogRepository auditLogRepository;

    @Test
    void accountNumberIsEncryptedAtRestAndMaskedOnRead() throws Exception {
        TestUser merchant = merchant();
        Store store = createStore(merchant);
        Bank bank = bank();
        String token = tokenFor(merchant);

        String created = mockMvc.perform(json(withBearer(post("/api/merchant/owner/bank-accounts"), token),
                        "{\"bankCode\":\"%s\",\"branch\":\"Colombo 03\",\"accountName\":\"Lanka Crafts\","
                                .formatted(bank.getCode()) + "\"accountNumber\":\"008 1234-567890\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accountNumberMasked").value("****7890"))
                .andExpect(jsonPath("$.bankName").value(bank.getName()))
                .andReturn().getResponse().getContentAsString();
        assertThat(created).doesNotContain(NUMBER).doesNotContain("1234567890");
        String publicId = read(created, "$.publicId");

        String stored = jdbcTemplate.queryForObject(
                "select account_number_enc from store_bank_accounts where public_id = ?", String.class, publicId);
        assertThat(stored).startsWith("v1:").doesNotContain(NUMBER).doesNotContain("7890");

        String list = mockMvc.perform(withBearer(get("/api/merchant/owner/bank-accounts"), token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].accountNumberMasked").value("****7890"))
                .andReturn().getResponse().getContentAsString();
        assertThat(list).doesNotContain(NUMBER);

        // Change without a new number keeps the old one; every change is audited, masked
        mockMvc.perform(json(withBearer(put("/api/merchant/owner/bank-accounts/" + publicId), token),
                        "{\"bankCode\":\"%s\",\"branch\":\"Kandy\",\"accountName\":\"Lanka Crafts\",\"active\":true}"
                                .formatted(bank.getCode())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.branch").value("Kandy"))
                .andExpect(jsonPath("$.accountNumberMasked").value("****7890"));
        List<AuditLogEntry> audit = auditLogRepository.findAll().stream()
                .filter(e -> publicId.equals(e.getTargetId())).toList();
        assertThat(audit).extracting(AuditLogEntry::getAction)
                .containsExactly("BANK_ACCOUNT_CREATED", "BANK_ACCOUNT_UPDATED");
        assertThat(audit).allSatisfy(entry -> {
            assertThat(entry.getActorRole()).isEqualTo("ROLE_MERCHANT");
            assertThat(entry.getActorStoreId()).isEqualTo(store.getId());
            assertThat(String.valueOf(entry.getBeforeState()) + entry.getAfterState()).doesNotContain(NUMBER)
                    .doesNotContain("1234567890");
        });

        // Only the internal (ROLE_SERVICE) endpoint returns the full number, for the order's customer
        mockMvc.perform(withBearer(get("/internal/stores/" + store.getId() + "/bank-accounts"), serviceToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].accountNumber").value(NUMBER))
                .andExpect(jsonPath("$[0].accountNumberMasked").value("****7890"));
        // Never on public endpoints
        String publicView = mockMvc.perform(get("/api/public/stores/" + store.getPublicId()))
                .andReturn().getResponse().getContentAsString();
        assertThat(publicView).doesNotContain("7890").doesNotContain("bank");
    }

    @Test
    void inactiveOrUnknownBanksAreRefusedAndNumbersAreValidated() throws Exception {
        TestUser merchant = merchant();
        createStore(merchant);
        String token = tokenFor(merchant);
        mockMvc.perform(json(withBearer(post("/api/merchant/owner/bank-accounts"), token),
                        "{\"bankCode\":\"NOPE\",\"branch\":\"x\",\"accountName\":\"x\",\"accountNumber\":\"12345678\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BANK_NOT_AVAILABLE"));
        String body = mockMvc.perform(json(withBearer(post("/api/merchant/owner/bank-accounts"), token),
                        "{\"bankCode\":\"%s\",\"branch\":\"x\",\"accountName\":\"x\",\"accountNumber\":\"12AB5678\"}"
                                .formatted(bank().getCode())))
                .andExpect(status().isBadRequest())
                .andReturn().getResponse().getContentAsString();
        assertThat(body).doesNotContain("12AB5678");
    }
}
