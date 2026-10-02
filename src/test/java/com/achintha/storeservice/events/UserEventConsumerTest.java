package com.achintha.storeservice.events;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.achintha.storeservice.outbox.OutboxMessage;
import com.achintha.storeservice.outbox.OutboxRepository;
import com.achintha.storeservice.security.UserStatus;
import com.achintha.storeservice.store.Store;
import com.achintha.storeservice.support.IntegrationTest;
import com.achintha.storeservice.support.TestUser;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;

/**
 * user-events consumer: the store reacts to its owner's status (BAN_GRACE stops new orders, BANNED unpublishes,
 * ACTIVE republishes) and announces it with StoreVisibilityChanged through the outbox.
 */
class UserEventConsumerTest extends IntegrationTest {

    @Autowired
    private UserEventHandler handler;
    @Autowired
    private OutboxRepository outboxRepository;
    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    private UserEvent statusChanged(TestUser merchant, UserStatus status, long tokenVersion) {
        return new UserEvent(UUID.randomUUID(), "UserStatusChanged", Instant.now(), merchant.id(),
                merchant.publicId(), "ROLE_MERCHANT", status.name(), null, merchant.storeId(), tokenVersion, null);
    }

    private List<String> visibilityEvents(Store store) {
        return outboxRepository.findAllByAggregateIdOrderByIdAsc(store.getId()).stream()
                .filter(m -> m.getEventType().equals("StoreVisibilityChanged"))
                .map(OutboxMessage::getPayload).toList();
    }

    @Test
    void banGraceStopsNewOrdersSilentlyThenBanUnpublishesAndUnbanRestores() throws Exception {
        TestUser merchant = merchant();
        Store store = createStore(merchant);
        assertThat(store.isVisible()).isTrue();

        assertThat(handler.handle(statusChanged(merchant, UserStatus.BAN_GRACE, 2))).isTrue();
        Store grace = storeRepository.findById(store.getId()).orElseThrow();
        assertThat(grace.isPublished()).isTrue();
        assertThat(grace.isAcceptingOrders()).isFalse();
        assertThat(grace.getOwnerStatus()).isEqualTo(UserStatus.BAN_GRACE);
        // Gone from public search and detail, but the merchant sees nothing unusual
        mockMvc.perform(get("/api/public/stores/" + store.getPublicId())).andExpect(status().isNotFound());
        mockMvc.perform(withBearer(get("/api/merchant/store"), tokenFor(merchant.withTokenVersion(2))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.published").value(true));
        // order-service sees it is not accepting orders
        mockMvc.perform(withBearer(get("/internal/stores/" + store.getId()), serviceToken()))
                .andExpect(jsonPath("$.acceptingOrders").value(false))
                .andExpect(jsonPath("$.visible").value(false));

        handler.handle(statusChanged(merchant, UserStatus.BANNED, 3));
        Store banned = storeRepository.findById(store.getId()).orElseThrow();
        assertThat(banned.isPublished()).isFalse();
        assertThat(banned.isAcceptingOrders()).isFalse();

        handler.handle(statusChanged(merchant, UserStatus.ACTIVE, 4));
        assertThat(storeRepository.findById(store.getId()).orElseThrow().isVisible()).isTrue();

        assertThat(visibilityEvents(store)).hasSize(4) // CREATED + 3 changes
                .satisfies(events -> {
                    assertThat(events.get(1)).contains("\"acceptingOrders\":false", "\"published\":true",
                            "\"change\":\"OWNER_BAN_GRACE\"");
                    assertThat(events.get(2)).contains("\"published\":false", "\"change\":\"OWNER_BANNED\"");
                    assertThat(events.get(3)).contains("\"visible\":true", "\"change\":\"OWNER_ACTIVE\"");
                });
    }

    @Test
    void duplicateEventsAreIgnored() throws Exception {
        TestUser merchant = merchant();
        Store store = createStore(merchant);
        UserEvent event = statusChanged(merchant, UserStatus.BAN_GRACE, 2);

        assertThat(handler.handle(event)).isTrue();
        assertThat(handler.handle(event)).isFalse();
        assertThat(visibilityEvents(store)).hasSize(2);
    }

    @Test
    void approvalPublishesAPendingMerchantsCompleteStore() throws Exception {
        TestUser merchant = merchant(UserStatus.PENDING_APPROVAL);
        Store store = createStore(merchant);
        assertThat(store.isPublished()).isFalse();

        handler.handle(statusChanged(merchant, UserStatus.ACTIVE, 1));
        assertThat(storeRepository.findById(store.getId()).orElseThrow().isVisible()).isTrue();
    }

    @Test
    void eventsForOtherUsersOrRolesDoNotTouchTheStore() throws Exception {
        TestUser merchant = merchant();
        Store store = createStore(merchant);
        // A customer event with this storeId, and a merchant event from someone who does not own the store
        handler.handle(new UserEvent(UUID.randomUUID(), "UserStatusChanged", Instant.now(), UUID.randomUUID(),
                "USR-2610-AAAAAA", "ROLE_CUSTOMER", "BANNED", null, store.getId(), 2L, null));
        handler.handle(new UserEvent(UUID.randomUUID(), "UserStatusChanged", Instant.now(), UUID.randomUUID(),
                "USR-2610-AAAAAB", "ROLE_MERCHANT", "BANNED", null, store.getId(), 2L, null));
        assertThat(storeRepository.findById(store.getId()).orElseThrow().isVisible()).isTrue();
    }

    @Test
    void consumesFromKafka() throws Exception {
        TestUser merchant = merchant();
        Store store = createStore(merchant);
        String payload = """
                {"eventId":"%s","eventType":"UserStatusChanged","occurredAt":"2026-10-01T10:00:00Z",
                 "userId":"%s","publicId":"%s","role":"ROLE_MERCHANT","status":"BAN_GRACE",
                 "previousStatus":"ACTIVE","storeId":"%s","tokenVersion":5,"banEffectiveAt":"2026-10-15T10:00:00Z"}
                """.formatted(UUID.randomUUID(), merchant.id(), merchant.publicId(), merchant.storeId());
        kafkaTemplate.send("user-events", merchant.id().toString(), payload).get();
        // A malformed message on the same topic is skipped, not retried forever
        kafkaTemplate.send("user-events", "x", "not json").get();

        await().atMost(Duration.ofSeconds(20)).untilAsserted(() ->
                assertThat(storeRepository.findById(store.getId()).orElseThrow().isAcceptingOrders()).isFalse());
        assertThat(securityStateCache.cached(merchant.id()).orElseThrow().tokenVersion()).isEqualTo(5);
    }
}
