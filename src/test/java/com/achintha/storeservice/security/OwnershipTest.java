package com.achintha.storeservice.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.achintha.storeservice.bank.Bank;
import com.achintha.storeservice.courier.Courier;
import com.achintha.storeservice.events.OrderEvent;
import com.achintha.storeservice.events.OrderEventHandler;
import com.achintha.storeservice.store.Store;
import com.achintha.storeservice.support.IntegrationTest;
import com.achintha.storeservice.support.TestUser;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Object-level authorization (BOLA, section 3.4): merchant-side data is scoped by the JWT storeId, so another store's
 * ids answer 404, never 403. Assistants need the route's permission and never reach owner-only routes.
 */
class OwnershipTest extends IntegrationTest {

    @Autowired
    private OrderEventHandler orderEventHandler;

    private TestUser merchantA;
    private TestUser merchantB;
    private Store storeB;
    private String accountOfB;
    private String templateOfB;
    private String reviewOfB;

    @BeforeEach
    void setUp() throws Exception {
        merchantA = merchant();
        createStore(merchantA);
        merchantB = merchant();
        storeB = createStore(merchantB);
        Bank bank = bank();
        Courier courier = courier();

        String tokenB = tokenFor(merchantB);
        accountOfB = read(mockMvc.perform(json(withBearer(post("/api/merchant/owner/bank-accounts"), tokenB),
                        "{\"bankCode\":\"%s\",\"branch\":\"Galle\",\"accountName\":\"B\",\"accountNumber\":\"9876543210\"}"
                                .formatted(bank.getCode())))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.publicId");
        mockMvc.perform(json(withBearer(put("/api/merchant/owner/couriers"), tokenB),
                "{\"courierCodes\":[\"%s\"]}".formatted(courier.getCode()))).andExpect(status().isOk());
        templateOfB = read(mockMvc.perform(json(withBearer(post("/api/merchant/owner/shipping-templates"), tokenB),
                        "{\"courierCode\":\"%s\",\"name\":\"Flat\",\"ruleType\":\"FLAT\",\"baseAmount\":400}"
                                .formatted(courier.getCode())))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.publicId");

        // A completed order of store B, reviewed by its customer
        TestUser customer = customer();
        String orderPublicId = "ORD-2610-" + suffix();
        orderEventHandler.handle(new OrderEvent(UUID.randomUUID(), "OrderCompleted", Instant.now(),
                UUID.randomUUID(), orderPublicId, storeB.getId(), storeB.getPublicId(), customer.id(), "COMPLETED",
                null, null, new BigDecimal("1000.00"), null, null, null, null));
        reviewOfB = read(mockMvc.perform(json(withBearer(post("/api/customer/stores/" + storeB.getPublicId()
                        + "/reviews"), tokenFor(customer)), "{\"orderPublicId\":\"%s\",\"rating\":4}"
                        .formatted(orderPublicId)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.publicId");
    }

    @Test
    void merchantCannotReadOrChangeAnotherStoresBankAccounts() throws Exception {
        String tokenA = tokenFor(merchantA);
        mockMvc.perform(withBearer(get("/api/merchant/owner/bank-accounts/" + accountOfB), tokenA))
                .andExpect(status().isNotFound());
        mockMvc.perform(json(withBearer(put("/api/merchant/owner/bank-accounts/" + accountOfB), tokenA),
                        "{\"bankCode\":\"BOC\",\"branch\":\"x\",\"accountName\":\"x\",\"active\":false}"))
                .andExpect(status().isNotFound());
        mockMvc.perform(withBearer(delete("/api/merchant/owner/bank-accounts/" + accountOfB), tokenA))
                .andExpect(status().isNotFound());
        mockMvc.perform(withBearer(get("/api/merchant/owner/bank-accounts"), tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
        // Still there for its owner
        mockMvc.perform(withBearer(get("/api/merchant/owner/bank-accounts/" + accountOfB), tokenFor(merchantB)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(true));
    }

    @Test
    void merchantCannotReadOrChangeAnotherStoresShippingTemplates() throws Exception {
        String tokenA = tokenFor(merchantA);
        mockMvc.perform(withBearer(get("/api/merchant/owner/shipping-templates/" + templateOfB), tokenA))
                .andExpect(status().isNotFound());
        mockMvc.perform(withBearer(delete("/api/merchant/owner/shipping-templates/" + templateOfB), tokenA))
                .andExpect(status().isNotFound());
        mockMvc.perform(withBearer(get("/api/merchant/owner/shipping-templates"), tokenA))
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void merchantCannotReplyToAnotherStoresReview() throws Exception {
        mockMvc.perform(json(withBearer(post("/api/merchant/reviews/" + reviewOfB + "/reply"), tokenFor(merchantA)),
                        "{\"text\":\"Not my review\"}"))
                .andExpect(status().isNotFound());
        mockMvc.perform(withBearer(get("/api/merchant/reviews"), tokenFor(merchantA)))
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void assistantNeedsReviewReplyPermissionAndOnlyReachesItsOwnStore() throws Exception {
        TestUser withoutPermission = assistant(merchantB, AssistantPermission.ORDER_VIEW,
                AssistantPermission.PRODUCT_EDIT);
        mockMvc.perform(json(withBearer(post("/api/merchant/reviews/" + reviewOfB + "/reply"),
                        tokenFor(withoutPermission)), "{\"text\":\"Thanks\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

        TestUser otherStoreAssistant = assistant(merchantA, AssistantPermission.REVIEW_REPLY);
        mockMvc.perform(json(withBearer(post("/api/merchant/reviews/" + reviewOfB + "/reply"),
                        tokenFor(otherStoreAssistant)), "{\"text\":\"Thanks\"}"))
                .andExpect(status().isNotFound());

        TestUser replier = assistant(merchantB, AssistantPermission.REVIEW_REPLY);
        mockMvc.perform(json(withBearer(post("/api/merchant/reviews/" + reviewOfB + "/reply"), tokenFor(replier)),
                        "{\"text\":\"Thank you <script>x</script>for shopping!\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.reply.text").value("Thank you for shopping!"));
        // One reply per review
        mockMvc.perform(json(withBearer(post("/api/merchant/reviews/" + reviewOfB + "/reply"), tokenFor(merchantB)),
                        "{\"text\":\"Again\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("REPLY_ALREADY_EXISTS"));
    }

    @Test
    void ownerOnlyRoutesRefuseAssistantsWithEveryPermission() throws Exception {
        String token = tokenFor(assistant(merchantB, AssistantPermission.values()));
        for (String path : new String[] {"/api/merchant/owner/bank-accounts", "/api/merchant/owner/couriers",
                "/api/merchant/owner/shipping-templates", "/api/merchant/owner/metrics", "/api/merchant/store"}) {
            mockMvc.perform(withBearer(get(path), token)).andExpect(status().isForbidden());
        }
    }

    @Test
    void ownerRoutesNeedAStoreProfileFirst() throws Exception {
        TestUser newcomer = merchant();
        mockMvc.perform(json(withBearer(post("/api/merchant/owner/bank-accounts"), tokenFor(newcomer)),
                        "{\"bankCode\":\"%s\",\"branch\":\"x\",\"accountName\":\"x\",\"accountNumber\":\"12345678\"}"
                                .formatted(bank().getCode())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("STORE_PROFILE_REQUIRED"));
    }
}
