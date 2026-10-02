package com.achintha.storeservice.outbox;

import com.achintha.storeservice.store.Store;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

/**
 * Records {@code store-events} in the outbox, inside the caller's transaction ({@code MANDATORY}): the event is
 * stored if and only if the change commits. {@link OutboxRelay} publishes it to Kafka afterwards (at-least-once).
 * Nothing is ever sent to Kafka directly from business code.
 */
@Component
public class StoreEventPublisher {

    /** Aggregate id of platform-wide events (settings, holidays): one key keeps them in order on one partition. */
    static final UUID PLATFORM_AGGREGATE = new UUID(0L, 0L);

    private final OutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final String topic;

    public StoreEventPublisher(OutboxRepository outboxRepository, ObjectMapper objectMapper, Clock clock,
                               @Value("${app.kafka.topics.store-events}") String topic) {
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
        this.clock = clock;
        this.topic = topic;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void visibilityChanged(Store store, String change) {
        append(new StoreEvent(UUID.randomUUID(), StoreEventType.StoreVisibilityChanged, clock.instant(),
                store.getId(), store.getId(), store.getPublicId(), store.getName(), store.isPublished(),
                store.isAcceptingOrders(), store.isVisible(), store.isCodEnabled(), null, null, null, change));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void settingsChanged(String key, String value) {
        append(new StoreEvent(UUID.randomUUID(), StoreEventType.SettingsChanged, clock.instant(), PLATFORM_AGGREGATE,
                null, null, null, null, null, null, null, key, value, null, "UPDATED"));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void holidaysChanged(LocalDate date, String change) {
        append(new StoreEvent(UUID.randomUUID(), StoreEventType.HolidaysChanged, clock.instant(), PLATFORM_AGGREGATE,
                null, null, null, null, null, null, null, null, null, date, change));
    }

    private void append(StoreEvent event) {
        Instant now = event.occurredAt();
        OutboxMessage message = new OutboxMessage();
        message.setEventId(event.eventId());
        message.setAggregateId(event.aggregateId());
        message.setEventType(event.eventType().name());
        message.setTopic(topic);
        message.setMessageKey(event.aggregateId().toString());
        message.setPayload(objectMapper.writeValueAsString(event));
        message.setCreatedAt(now);
        outboxRepository.save(message);
    }
}
