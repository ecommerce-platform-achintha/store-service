package com.achintha.storeservice.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.achintha.storeservice.store.Store;
import com.achintha.storeservice.support.IntegrationTest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.transaction.IllegalTransactionStateException;

/** Transactional outbox (section 9): written with the change, relayed to Kafka keyed by the store, at least once. */
class OutboxRelayIntegrationTest extends IntegrationTest {

    @Autowired
    private OutboxRelay relay;
    @Autowired
    private OutboxRepository outboxRepository;
    @Autowired
    private StoreEventPublisher events;
    @Autowired
    private EmbeddedKafkaBroker broker;

    @Test
    void relayPublishesStoreEventsKeyedByStore() throws Exception {
        Store store = createStore(merchant());
        try (Consumer<String, String> consumer = consumer()) {
            broker.consumeFromAnEmbeddedTopic(consumer, "store-events");
            int published;
            do {
                published = relay.publishPending();
            } while (published > 0);

            assertThat(outboxRepository.findAllByAggregateIdOrderByIdAsc(store.getId()))
                    .allSatisfy(m -> assertThat(m.getPublishedAt()).isNotNull());
            List<ConsumerRecord<String, String>> records = new ArrayList<>();
            long deadline = System.currentTimeMillis() + 20_000;
            while (records.stream().noneMatch(r -> store.getId().toString().equals(r.key()))
                    && System.currentTimeMillis() < deadline) {
                KafkaTestUtils.getRecords(consumer, Duration.ofSeconds(2)).forEach(records::add);
            }
            ConsumerRecord<String, String> record = records.stream()
                    .filter(r -> store.getId().toString().equals(r.key())).findFirst().orElseThrow();
            assertThat(record.value()).contains("\"eventType\":\"StoreVisibilityChanged\"")
                    .contains("\"storePublicId\":\"" + store.getPublicId() + "\"")
                    .contains("\"eventId\"").contains("\"occurredAt\"");
        }
    }

    @Test
    void eventsCanOnlyBeRecordedInsideATransaction() {
        assertThatThrownBy(() -> events.settingsChanged("orders.max-quote-revisions", "2"))
                .isInstanceOf(IllegalTransactionStateException.class);
    }

    private Consumer<String, String> consumer() {
        Map<String, Object> props = KafkaTestUtils.consumerProps(broker, "outbox-test-" + next(), false);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        return new DefaultKafkaConsumerFactory<>(props, new StringDeserializer(), new StringDeserializer())
                .createConsumer();
    }
}
