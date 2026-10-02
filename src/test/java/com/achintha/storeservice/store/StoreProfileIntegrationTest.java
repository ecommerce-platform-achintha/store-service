package com.achintha.storeservice.store;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.achintha.storeservice.outbox.OutboxMessage;
import com.achintha.storeservice.outbox.OutboxRepository;
import com.achintha.storeservice.security.UserStatus;
import com.achintha.storeservice.support.IntegrationTest;
import com.achintha.storeservice.support.TestUser;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** Store profile: creation under the JWT storeId, unique names, sanitizing, visibility and public views. */
class StoreProfileIntegrationTest extends IntegrationTest {

    @Autowired
    private OutboxRepository outboxRepository;

    @Test
    void activeMerchantCreatesAPublishedStoreUnderTheTokenStoreId() throws Exception {
        TestUser merchant = merchant();
        String name = "Lanka Crafts " + next();

        String body = mockMvc.perform(json(withBearer(put("/api/merchant/store"), tokenFor(merchant)),
                        storeProfileJson(name)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value(name))
                .andExpect(jsonPath("$.published").value(true))
                .andExpect(jsonPath("$.profileComplete").value(true))
                // HTML is stripped from free text, emails are normalised
                .andExpect(jsonPath("$.description").value("Hand-made goods"))
                .andExpect(jsonPath("$.contactEmail").value("shop@example.test"))
                // BAN_GRACE must stay invisible to the merchant: no acceptingOrders / owner status in their view
                .andExpect(jsonPath("$.acceptingOrders").doesNotExist())
                .andExpect(jsonPath("$.ownerStatus").doesNotExist())
                .andReturn().getResponse().getContentAsString();

        Store store = storeRepository.findById(merchant.storeId()).orElseThrow();
        assertThat(store.getPublicId()).matches("^STR-\\d{4}-[0-9A-HJKMNP-TV-Z]{6}$").isEqualTo(read(body, "$.publicId"));
        assertThat(store.getOwnerUserId()).isEqualTo(merchant.id());
        assertThat(store.isVisible()).isTrue();
        assertThat(store.getSlug()).startsWith("lanka-crafts-");

        List<OutboxMessage> events = outboxRepository.findAllByAggregateIdOrderByIdAsc(store.getId());
        assertThat(events).extracting(OutboxMessage::getEventType).containsExactly("StoreVisibilityChanged");
        assertThat(events.getFirst().getPayload()).contains("\"visible\":true").doesNotContain("account");

        // Public views: visible, public fields only
        mockMvc.perform(get("/api/public/stores/" + store.getPublicId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value(name))
                .andExpect(jsonPath("$.district").value("Kandy"))
                .andExpect(jsonPath("$.contactPhone").doesNotExist())
                .andExpect(jsonPath("$.id").doesNotExist());
        mockMvc.perform(get("/api/public/stores").param("q", name))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].publicId").value(store.getPublicId()));
    }

    @Test
    void pendingMerchantMayEditButIsNotPublished() throws Exception {
        TestUser merchant = merchant(UserStatus.PENDING_APPROVAL);
        Store store = createStore(merchant);

        assertThat(store.isPublished()).isFalse();
        assertThat(store.isAcceptingOrders()).isFalse();
        mockMvc.perform(get("/api/public/stores/" + store.getPublicId())).andExpect(status().isNotFound());
        mockMvc.perform(withBearer(get("/api/merchant/store"), tokenFor(merchant)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.published").value(false));
    }

    @Test
    void incompleteProfileIsNotPublished() throws Exception {
        TestUser merchant = merchant();
        mockMvc.perform(json(withBearer(put("/api/merchant/store"), tokenFor(merchant)),
                        "{\"name\":\"Draft Store " + next() + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.published").value(false))
                .andExpect(jsonPath("$.profileComplete").value(false));
    }

    @Test
    void storeNamesAreUniqueIgnoringCase() throws Exception {
        String name = "Unique Name " + next();
        mockMvc.perform(json(withBearer(put("/api/merchant/store"), tokenFor(merchant())), storeProfileJson(name)))
                .andExpect(status().isOk());

        mockMvc.perform(json(withBearer(put("/api/merchant/store"), tokenFor(merchant())),
                        storeProfileJson(name.toUpperCase())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("STORE_NAME_TAKEN"));
    }

    @Test
    void ownerTogglesCod() throws Exception {
        TestUser merchant = merchant();
        createStore(merchant);
        mockMvc.perform(json(withBearer(put("/api/merchant/store/cod"), tokenFor(merchant)), "{\"enabled\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.codEnabled").value(true));
        assertThat(storeRepository.findById(merchant.storeId()).orElseThrow().isCodEnabled()).isTrue();
    }

    @Test
    void validationErrorsUseTheSharedErrorShape() throws Exception {
        mockMvc.perform(json(withBearer(put("/api/merchant/store"), tokenFor(merchant())),
                        "{\"name\":\"x\",\"contactPhone\":\"12\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.path").value("/api/merchant/store"))
                .andExpect(jsonPath("$.fieldErrors").isArray());
    }
}
