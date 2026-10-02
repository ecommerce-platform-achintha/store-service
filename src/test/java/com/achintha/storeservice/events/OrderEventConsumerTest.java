package com.achintha.storeservice.events;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.achintha.storeservice.fee.FeeLedgerRepository;
import com.achintha.storeservice.fee.LedgerEntryType;
import com.achintha.storeservice.ports.NotificationPort;
import com.achintha.storeservice.score.MerchantMetrics;
import com.achintha.storeservice.score.MerchantMetricsRepository;
import com.achintha.storeservice.score.MerchantScoreEventRepository;
import com.achintha.storeservice.score.ScoreEventType;
import com.achintha.storeservice.store.Store;
import com.achintha.storeservice.support.IntegrationTest;
import com.achintha.storeservice.support.TestUser;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.mockito.Mockito;

/** order-events consumer: score deltas from settings, metrics, fee ledger, review eligibility, idempotency. */
class OrderEventConsumerTest extends IntegrationTest {

    @Autowired
    private OrderEventHandler handler;
    @Autowired
    private MerchantMetricsRepository metricsRepository;
    @Autowired
    private MerchantScoreEventRepository scoreEventRepository;
    @Autowired
    private FeeLedgerRepository feeLedgerRepository;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @MockitoSpyBean
    private NotificationPort notifications;

    private TestUser merchant;
    private Store store;

    @BeforeEach
    void setUp() throws Exception {
        merchant = merchant();
        store = createStore(merchant);
    }

    private OrderEvent event(String type, UUID orderId, String orderPublicId) {
        return new OrderEvent(UUID.randomUUID(), type, Instant.now(), orderId, orderPublicId, store.getId(),
                store.getPublicId(), UUID.randomUUID(), null, null, null, null, null, null, null, null);
    }

    private OrderEvent completed(UUID orderId, String orderPublicId, UUID customerId, String total) {
        return new OrderEvent(UUID.randomUUID(), "OrderCompleted", Instant.now(), orderId, orderPublicId,
                store.getId(), store.getPublicId(), customerId, "COMPLETED", null, null, new BigDecimal(total), null,
                null, null, null);
    }

    private MerchantMetrics metrics() {
        return metricsRepository.findById(store.getId()).orElseThrow();
    }

    @Test
    void completionAddsScoreRevenueFeeAndReviewEligibilityOnce() {
        UUID orderId = UUID.randomUUID();
        String orderPublicId = "ORD-2610-" + suffix();
        OrderEvent event = completed(orderId, orderPublicId, UUID.randomUUID(), "2500.00");

        assertThat(handler.handle(event)).isTrue();
        // Same event delivered again (at-least-once): ignored
        assertThat(handler.handle(event)).isFalse();
        // Same order completed under another event id: score and fee still applied once
        assertThat(handler.handle(completed(orderId, orderPublicId, UUID.randomUUID(), "2500.00"))).isTrue();

        MerchantMetrics metrics = metrics();
        assertThat(metrics.getScore()).isEqualTo(1);
        assertThat(scoreEventRepository.findAllByStoreId(store.getId(), Pageable.unpaged()).getContent())
                .extracting(e -> e.getEventType()).containsExactly(ScoreEventType.ORDER_COMPLETED);
        assertThat(feeLedgerRepository.countByOrderIdAndEntryType(orderId, LedgerEntryType.ORDER_FEE)).isOne();
        assertThat(feeLedgerRepository.balance(store.getId())).isEqualByComparingTo("10.00");
        assertThat(jdbcTemplate.queryForObject("select count(*) from review_eligibility where order_id = ?",
                Long.class, orderId)).isOne();
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from processed_events where event_id = ? and consumer = ?", Long.class,
                event.eventId(), OrderEventHandler.CONSUMER)).isOne();
    }

    @Test
    void penaltiesUseTheConfiguredDeltasAndApplyOncePerOrder() {
        UUID order1 = UUID.randomUUID();
        UUID order2 = UUID.randomUUID();
        UUID order3 = UUID.randomUUID();
        // Merchant timeout: -2 (default)
        handler.handle(new OrderEvent(UUID.randomUUID(), "OrderCancelled", Instant.now(), order1, "ORD-2610-AAAAA1",
                store.getId(), store.getPublicId(), null, "EXPIRED_MERCHANT", "EXPIRED_MERCHANT", null, null, null,
                null, null, null));
        // Late shipment via the explicit hint, sent twice in different events: -1 once
        OrderEvent late = new OrderEvent(UUID.randomUUID(), "OrderShipmentOverdue", Instant.now(), order2,
                "ORD-2610-AAAAA2", store.getId(), store.getPublicId(), null, "READY_TO_SHIP", null, null, null, null,
                null, "LATE_SHIPMENT", null);
        handler.handle(late);
        handler.handle(new OrderEvent(UUID.randomUUID(), "OrderShipped", Instant.now(), order2, "ORD-2610-AAAAA2",
                store.getId(), store.getPublicId(), null, "SHIPPED", null, null, null, null, null, "LATE_SHIPMENT",
                null));
        // Upheld complaint: -3
        handler.handle(new OrderEvent(UUID.randomUUID(), "ComplaintDecided", Instant.now(), order3,
                "ORD-2610-AAAAA3", store.getId(), store.getPublicId(), null, null, null, null, null, null, null,
                null, "UPHELD"));

        MerchantMetrics metrics = metrics();
        assertThat(metrics.getScore()).isEqualTo(-2 - 1 - 3);
        assertThat(metrics.getLateShipments()).isEqualTo(1);
        assertThat(metrics.getComplaintsUpheld()).isEqualTo(1);
        assertThat(metrics.getCancelledOrders()).isEqualTo(1);
    }

    @Test
    void deltasFollowTheSettings() throws Exception {
        jdbcTemplate.update("update platform_settings set value = '-7' where setting_key = 'score.merchant.late-verification'");
        settingsService.invalidate();
        try {
            handler.handle(new OrderEvent(UUID.randomUUID(), "OrderVerificationOverdue", Instant.now(),
                    UUID.randomUUID(), "ORD-2610-" + suffix(), store.getId(), store.getPublicId(), null, null, null,
                    null, null, null, null, null, null));
            assertThat(metrics().getScore()).isEqualTo(-7);
            assertThat(metrics().getLateVerifications()).isEqualTo(1);
        } finally {
            jdbcTemplate.update("update platform_settings set value = '-1' where setting_key = 'score.merchant.late-verification'");
            settingsService.invalidate();
        }
    }

    @Test
    void fallingBelowTheThresholdFlagsTheMerchantAtRiskAndAlertsAdmins() throws Exception {
        Mockito.clearInvocations(notifications);
        // -2 x 3 = -6 < -5
        for (int i = 0; i < 3; i++) {
            handler.handle(new OrderEvent(UUID.randomUUID(), "OrderCancelled", Instant.now(), UUID.randomUUID(),
                    "ORD-2610-" + suffix(), store.getId(), store.getPublicId(), null, null, "EXPIRED_MERCHANT", null,
                    null, null, null, null, null));
        }
        assertThat(metrics().getScore()).isEqualTo(-6);
        assertThat(metrics().getAtRiskSince()).isNotNull();
        Mockito.verify(notifications, Mockito.times(1)).notifyAdmins(Mockito.eq("MERCHANT_AT_RISK"), Mockito.anyString());

        mockMvc.perform(withBearer(get("/api/admin/merchants/at-risk").param("size", "100"), tokenFor(admin())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.storePublicId == '%s')].score".formatted(store.getPublicId()))
                        .value(-6));
        mockMvc.perform(withBearer(get("/api/admin/stores/" + store.getPublicId() + "/score-events"),
                        tokenFor(admin())))
                .andExpect(jsonPath("$.totalElements").value(3));
    }

    @Test
    void metricsReflectTheOrderLifecycle() throws Exception {
        Instant placed = Instant.parse("2026-10-01T08:00:00Z");
        for (int i = 0; i < 4; i++) {
            handler.handle(event("OrderPlaced", UUID.randomUUID(), "ORD-2610-" + suffix()));
        }
        // Two first quotes (30 and 90 minutes) and one re-quote (not a new response)
        handler.handle(quoted(placed, placed.plus(Duration.ofMinutes(30)), 1));
        handler.handle(quoted(placed, placed.plus(Duration.ofMinutes(90)), 1));
        handler.handle(quoted(placed, placed.plus(Duration.ofMinutes(500)), 2));
        // One rejection after 60 minutes
        handler.handle(new OrderEvent(UUID.randomUUID(), "OrderCancelled", placed.plus(Duration.ofMinutes(60)),
                UUID.randomUUID(), "ORD-2610-" + suffix(), store.getId(), store.getPublicId(), null,
                "REJECTED_BY_MERCHANT", "REJECTED_BY_MERCHANT", "OUT_OF_STOCK", null, placed, null, null, null));
        handler.handle(completed(UUID.randomUUID(), "ORD-2610-" + suffix(), UUID.randomUUID(), "1500.50"));

        mockMvc.perform(withBearer(get("/api/merchant/owner/metrics"), tokenFor(merchant)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.score").value(1))
                .andExpect(jsonPath("$.ordersReceived").value(4))
                .andExpect(jsonPath("$.ordersQuoted").value(2))
                .andExpect(jsonPath("$.acceptanceRatePercent").value(50.0))
                .andExpect(jsonPath("$.averageResponseMinutes").value(60.0))
                .andExpect(jsonPath("$.cancelledOrders").value(1))
                .andExpect(jsonPath("$.cancellationRatePercent").value(25.0))
                .andExpect(jsonPath("$.completedOrders").value(1))
                .andExpect(jsonPath("$.revenue").value(1500.50));
    }

    private OrderEvent quoted(Instant placedAt, Instant at, int revision) {
        return new OrderEvent(UUID.randomUUID(), "OrderQuoted", at, UUID.randomUUID(), "ORD-2610-" + suffix(),
                store.getId(), store.getPublicId(), null, "AWAITING_CUSTOMER_CONFIRMATION", null, null, null,
                placedAt, revision, null, null);
    }
}
