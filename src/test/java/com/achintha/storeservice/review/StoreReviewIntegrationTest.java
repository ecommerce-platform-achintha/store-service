package com.achintha.storeservice.review;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.achintha.storeservice.events.OrderEvent;
import com.achintha.storeservice.events.OrderEventHandler;
import com.achintha.storeservice.security.Role;
import com.achintha.storeservice.security.UserStatus;
import com.achintha.storeservice.store.Store;
import com.achintha.storeservice.support.IntegrationTest;
import com.achintha.storeservice.support.TestUser;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** Store reviews: eligibility from OrderCompleted, one per order, rating aggregates, moderation. */
class StoreReviewIntegrationTest extends IntegrationTest {

    @Autowired
    private OrderEventHandler orderEventHandler;

    private Store store;
    private TestUser customer;

    @BeforeEach
    void setUp() throws Exception {
        store = createStore(merchant());
        customer = customer();
    }

    private String completedOrder(TestUser buyer) {
        String orderPublicId = "ORD-2610-" + suffix();
        orderEventHandler.handle(new OrderEvent(UUID.randomUUID(), "OrderCompleted", Instant.now(),
                UUID.randomUUID(), orderPublicId, store.getId(), store.getPublicId(), buyer.id(), "COMPLETED", null,
                null, new BigDecimal("100.00"), null, null, null, null));
        return orderPublicId;
    }

    private String review(TestUser buyer, String orderPublicId, int rating) throws Exception {
        return mockMvc.perform(json(withBearer(post("/api/customer/stores/" + store.getPublicId() + "/reviews"),
                        tokenFor(buyer)), """
                        {"orderPublicId":"%s","rating":%d,"text":"Great <img src=x onerror=alert(1)>service",
                         "attachmentKeys":["reviews/photo-1.jpg"]}""".formatted(orderPublicId, rating)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
    }

    private Store reloadStore() {
        return storeRepository.findById(store.getId()).orElseThrow();
    }

    @Test
    void customerReviewsACompletedOrderOnceAndTheRatingIsAggregated() throws Exception {
        String body = review(customer, completedOrder(customer), 5);
        assertThat(read(body, "$.publicId")).startsWith("REV-");
        assertThat(read(body, "$.text")).isEqualTo("Great service");
        assertThat(read(body, "$.attachmentUrls[0]")).endsWith("reviews/photo-1.jpg");
        review(customer, completedOrder(customer), 2);

        assertThat(reloadStore().getRatingCount()).isEqualTo(2);
        assertThat(reloadStore().getRatingAvg()).isEqualByComparingTo("3.50");

        mockMvc.perform(get("/api/public/stores/" + store.getPublicId() + "/reviews").param("sort", "rating,desc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[0].rating").value(5))
                .andExpect(jsonPath("$.content[0].orderPublicId").doesNotExist())
                .andExpect(jsonPath("$.content[0].customerId").doesNotExist());
    }

    @Test
    void onlyTheBuyerOfACompletedOrderMayReviewAndOnlyOnce() throws Exception {
        String order = completedOrder(customer);
        String path = "/api/customer/stores/" + store.getPublicId() + "/reviews";
        // Someone else's order
        mockMvc.perform(json(withBearer(post(path), tokenFor(customer())),
                        "{\"orderPublicId\":\"%s\",\"rating\":1}".formatted(order)))
                .andExpect(status().isNotFound());
        // An order that never completed
        mockMvc.perform(json(withBearer(post(path), tokenFor(customer)),
                        "{\"orderPublicId\":\"ORD-2610-%s\",\"rating\":1}".formatted(suffix())))
                .andExpect(status().isNotFound());
        // Wrong store
        Store other = createStore(merchant());
        mockMvc.perform(json(withBearer(post("/api/customer/stores/" + other.getPublicId() + "/reviews"),
                        tokenFor(customer)), "{\"orderPublicId\":\"%s\",\"rating\":1}".formatted(order)))
                .andExpect(status().isNotFound());
        // Rating out of range
        mockMvc.perform(json(withBearer(post(path), tokenFor(customer)),
                        "{\"orderPublicId\":\"%s\",\"rating\":6}".formatted(order)))
                .andExpect(status().isBadRequest());

        review(customer, order, 4);
        mockMvc.perform(json(withBearer(post(path), tokenFor(customer)),
                        "{\"orderPublicId\":\"%s\",\"rating\":5}".formatted(order)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("REVIEW_ALREADY_EXISTS"));
    }

    @Test
    void bannedCustomerCannotReview() throws Exception {
        TestUser banned = user(Role.ROLE_CUSTOMER, UserStatus.BANNED, null, null);
        String order = completedOrder(banned);
        mockMvc.perform(json(withBearer(post("/api/customer/stores/" + store.getPublicId() + "/reviews"),
                        tokenFor(banned)), "{\"orderPublicId\":\"%s\",\"rating\":1}".formatted(order)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCOUNT_BANNED"));
    }

    @Test
    void adminHidesAndUnhidesWithAReasonAndTheRatingFollows() throws Exception {
        String reviewId = read(review(customer, completedOrder(customer), 1), "$.publicId");
        review(customer, completedOrder(customer), 5);
        String admin = tokenFor(admin());

        mockMvc.perform(json(withBearer(post("/api/admin/stores/reviews/" + reviewId + "/hide"), admin), "{}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(json(withBearer(post("/api/admin/stores/reviews/" + reviewId + "/hide"), admin),
                        "{\"reason\":\"Abusive language\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hidden").value(true))
                .andExpect(jsonPath("$.hiddenReason").value("Abusive language"));
        assertThat(reloadStore().getRatingCount()).isEqualTo(1);
        assertThat(reloadStore().getRatingAvg()).isEqualByComparingTo("5.00");
        mockMvc.perform(get("/api/public/stores/" + store.getPublicId() + "/reviews"))
                .andExpect(jsonPath("$.totalElements").value(1));
        // The admin still sees it
        mockMvc.perform(withBearer(get("/api/admin/stores/" + store.getPublicId() + "/reviews"), admin))
                .andExpect(jsonPath("$.totalElements").value(2));

        mockMvc.perform(json(withBearer(post("/api/admin/stores/reviews/" + reviewId + "/unhide"), admin),
                        "{\"reason\":\"Appeal accepted\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hidden").value(false));
        assertThat(reloadStore().getRatingAvg()).isEqualByComparingTo("3.00");
    }
}
