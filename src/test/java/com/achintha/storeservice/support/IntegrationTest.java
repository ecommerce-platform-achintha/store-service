package com.achintha.storeservice.support;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.achintha.storeservice.bank.Bank;
import com.achintha.storeservice.bank.BankRepository;
import com.achintha.storeservice.courier.Courier;
import com.achintha.storeservice.courier.CourierRepository;
import com.achintha.storeservice.security.AssistantPermission;
import com.achintha.storeservice.security.Role;
import com.achintha.storeservice.security.SecurityStateCache;
import com.achintha.storeservice.security.UserStatus;
import com.achintha.storeservice.settings.PlatformSettingsService;
import com.achintha.storeservice.store.Store;
import com.achintha.storeservice.store.StoreRepository;
import com.jayway.jsonpath.JsonPath;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Base class of the integration tests: the full application against a real PostgreSQL (Testcontainers), an embedded
 * Kafka broker and a WireMock user-service (JWKS, service token, security state), all shared by the whole test run so
 * the Spring context is cached. Schedulers are off; tests call the outbox relay directly. The clock is a
 * {@link MutableClock}. Tokens are minted with {@link TestKeys}, exactly as user-service would.
 */
@SpringBootTest(properties = {
        "spring.cloud.config.enabled=false",
        "eureka.client.enabled=false",
        "app.scheduling.enabled=false",
        "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}",
        "security.internal.client-secret=" + IntegrationTest.SERVICE_CLIENT_SECRET,
        "app.crypto.account-number-key=" + IntegrationTest.ACCOUNT_KEY,
        "logging.level.org.apache.kafka=WARN",
        "logging.level.kafka=WARN",
        "logging.level.state.change.logger=WARN"
})
@AutoConfigureMockMvc
@EmbeddedKafka(topics = {"user-events", "order-events", "store-events"}, partitions = 1)
@Import(IntegrationTest.ClockConfig.class)
public abstract class IntegrationTest {

    public static final String SERVICE_CLIENT_SECRET = "test-store-service-secret-123456";
    /** Base64 of 32 bytes, test only. */
    public static final String ACCOUNT_KEY = "dGVzdC1vbmx5LWFjY291bnQta2V5LTMyLWJ5dGVzISE=";

    private static final AtomicLong SEQUENCE = new AtomicLong(ThreadLocalRandom.current().nextLong(1_000_000L));
    private static final PostgreSQLContainer POSTGRES = SharedPostgres.CONTAINER;
    private static final JwtEncoder ENCODER = NimbusJwtEncoder
            .withKeyPair((RSAPublicKey) TestKeys.KEY_PAIR.getPublic(),
                    (RSAPrivateKey) TestKeys.KEY_PAIR.getPrivate())
            .algorithm(SignatureAlgorithm.RS256)
            .jwkPostProcessor(jwk -> jwk.keyID(SharedWireMock.KEY_ID))
            .build();

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("security.jwt.jwks-uri", SharedWireMock::jwksUri);
        registry.add("spring.cloud.discovery.client.simple.instances.user-service[0].uri",
                SharedWireMock.USER_SERVICE::baseUrl);
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class ClockConfig {

        @Bean
        @Primary
        MutableClock mutableClock() {
            return new MutableClock();
        }
    }

    @Autowired
    protected MockMvc mockMvc;
    @Autowired
    protected TransactionTemplate transactionTemplate;
    @Autowired
    protected MutableClock clock;
    @Autowired
    protected SecurityStateCache securityStateCache;
    @Autowired
    protected StoreRepository storeRepository;
    @Autowired
    protected BankRepository bankRepository;
    @Autowired
    protected CourierRepository courierRepository;
    @Autowired
    protected PlatformSettingsService settingsService;

    @AfterEach
    void resetClock() {
        clock.reset();
        // The settings snapshot is timed with this clock
        settingsService.invalidate();
    }

    // ------------------------------------------------------------------------------------------------- users

    protected static long next() {
        return SEQUENCE.incrementAndGet();
    }

    /** A user known to the security-state cache (as if user-events had been consumed) with the given status. */
    protected TestUser user(Role role, UserStatus status, UUID storeId, List<String> perms) {
        TestUser user = new TestUser(UUID.randomUUID(), "USR-2610-" + suffix(), role, storeId, perms, 1);
        securityStateCache.apply(user.id(), user.tokenVersion(), status);
        return user;
    }

    protected TestUser customer() {
        return user(Role.ROLE_CUSTOMER, UserStatus.ACTIVE, null, null);
    }

    protected TestUser merchant() {
        return merchant(UserStatus.ACTIVE);
    }

    protected TestUser merchant(UserStatus status) {
        return user(Role.ROLE_MERCHANT, status, UUID.randomUUID(), null);
    }

    protected TestUser assistant(TestUser merchant, AssistantPermission... permissions) {
        return user(Role.ROLE_ASSISTANT, UserStatus.ACTIVE, merchant.storeId(),
                Arrays.stream(permissions).map(Enum::name).toList());
    }

    protected TestUser admin() {
        return user(Role.ROLE_ADMIN, UserStatus.ACTIVE, null, null);
    }

    protected TestUser superAdmin() {
        return user(Role.ROLE_SUPER_ADMIN, UserStatus.ACTIVE, null, null);
    }

    /** A signed access token with exactly the claims user-service issues (section 3.3). */
    protected String tokenFor(TestUser user) {
        Instant now = clock.instant();
        JwtClaimsSet.Builder claims = JwtClaimsSet.builder()
                .issuer("user-service")
                .audience(List.of("marketplace"))
                .subject(user.id().toString())
                .issuedAt(now)
                .expiresAt(now.plusSeconds(600))
                .id(UUID.randomUUID().toString())
                .claim("pid", user.publicId())
                .claim("role", user.role().name())
                .claim("status", "ACTIVE")
                .claim("tv", user.tokenVersion());
        if (user.storeId() != null) {
            claims.claim("storeId", user.storeId().toString());
        }
        if (user.perms() != null) {
            claims.claim("perms", user.perms());
        }
        return encode(claims.build());
    }

    protected String serviceToken() {
        Instant now = clock.instant();
        return encode(JwtClaimsSet.builder()
                .issuer("user-service")
                .audience(List.of("marketplace"))
                .subject("order-service")
                .issuedAt(now)
                .expiresAt(now.plusSeconds(300))
                .id(UUID.randomUUID().toString())
                .claim("role", Role.ROLE_SERVICE.name())
                .claim("svc", "order-service")
                .build());
    }

    protected static String encode(JwtClaimsSet claims) {
        JwsHeader header = JwsHeader.with(SignatureAlgorithm.RS256).keyId(SharedWireMock.KEY_ID).type("JWT").build();
        return ENCODER.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }

    // ---------------------------------------------------------------------------------------------- fixtures

    protected static String suffix() {
        String alphabet = "0123456789ABCDEFGHJKMNPQRSTVWXYZ";
        long n = next();
        StringBuilder s = new StringBuilder();
        for (int i = 0; i < 6; i++) {
            s.append(alphabet.charAt((int) (n % 32)));
            n /= 32;
        }
        return s.toString();
    }

    protected static String storeProfileJson(String name) {
        return """
                {"name":"%s","description":"Hand-made <b>goods</b>","contactPhone":"+94771234567",
                 "contactEmail":"Shop@Example.test","addressLine":"12 Main Street","city":"Kandy",
                 "district":"Kandy","logoKey":"logos/store.png"}
                """.formatted(name);
    }

    /** Creates the merchant's store through the API, with a complete profile. */
    protected Store createStore(TestUser merchant) throws Exception {
        mockMvc.perform(json(withBearer(put("/api/merchant/store"), tokenFor(merchant)),
                        storeProfileJson("Store " + next())))
                .andExpect(status().isOk());
        return storeRepository.findById(merchant.storeId()).orElseThrow();
    }

    protected Bank bank() {
        return transactionTemplate.execute(tx -> {
            Bank bank = new Bank();
            bank.setCode("B" + suffix());
            bank.setName("Test Bank " + next());
            bank.setActive(true);
            bank.setCreatedAt(Instant.now());
            bank.setUpdatedAt(Instant.now());
            return bankRepository.save(bank);
        });
    }

    protected Courier courier() {
        return transactionTemplate.execute(tx -> {
            Courier courier = new Courier();
            courier.setCode("C" + suffix());
            courier.setName("Test Courier " + next());
            courier.setTrackingUrlTemplate("https://track.example.test/?id={trackingNumber}");
            courier.setTrackingNumberRegex("^[A-Z0-9]{8,12}$");
            courier.setCodSupported(true);
            courier.setActive(true);
            courier.setCreatedAt(Instant.now());
            courier.setUpdatedAt(Instant.now());
            return courierRepository.save(courier);
        });
    }

    // -------------------------------------------------------------------------------------------------- HTTP

    protected static MockHttpServletRequestBuilder withBearer(MockHttpServletRequestBuilder request, String token) {
        return token == null ? request : request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
    }

    protected static MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder request, String body) {
        return request.contentType(MediaType.APPLICATION_JSON).content(body);
    }

    protected static String read(String json, String path) {
        Object value = JsonPath.read(json, path);
        return value == null ? null : value.toString();
    }
}
