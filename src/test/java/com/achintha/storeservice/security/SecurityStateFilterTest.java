package com.achintha.storeservice.security;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.achintha.storeservice.events.UserEvent;
import com.achintha.storeservice.events.UserEventHandler;
import com.achintha.storeservice.support.IntegrationTest;
import com.achintha.storeservice.support.SharedWireMock;
import com.achintha.storeservice.support.TestUser;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;

/**
 * Token revocation (section 3.3): tokens older than the user's token version are refused; the version comes from the
 * user-events-fed cache or, on a miss, from user-service with a service token.
 */
class SecurityStateFilterTest extends IntegrationTest {

    private static final WireMockServer USER_SERVICE = SharedWireMock.USER_SERVICE;

    @Autowired
    private UserEventHandler userEventHandler;
    @Autowired
    private ServiceTokenProvider serviceTokenProvider;

    @BeforeEach
    void stubServiceToken() {
        serviceTokenProvider.invalidate();
        USER_SERVICE.stubFor(WireMock.post(urlEqualTo("/internal/auth/service-token"))
                .willReturn(okJson("""
                        {"accessToken":"svc-token-1","tokenType":"Bearer","expiresIn":300,
                         "expiresAt":"%s"}""".formatted(Instant.now().plusSeconds(300)))));
    }

    @AfterEach
    void resetStubs() {
        USER_SERVICE.resetAll();
        SharedWireMock.stubJwks();
    }

    @Test
    void tokenOlderThanTheCachedVersionIsRevoked() throws Exception {
        TestUser merchant = merchant();
        createStore(merchant);
        String oldToken = tokenFor(merchant);

        // user-service bumped the token version (e.g. password change) and told everyone
        userEventHandler.handle(new UserEvent(UUID.randomUUID(), "UserSecurityChanged", Instant.now(),
                merchant.id(), merchant.publicId(), "ROLE_MERCHANT", "ACTIVE", null, merchant.storeId(), 2L,
                "PASSWORD_CHANGED"));

        mockMvc.perform(withBearer(get("/api/merchant/store"), oldToken))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("TOKEN_REVOKED"))
                .andExpect(header().string("WWW-Authenticate", "Bearer error=\"invalid_token\""));
        mockMvc.perform(withBearer(get("/api/merchant/store"), tokenFor(merchant.withTokenVersion(2))))
                .andExpect(status().isOk());
    }

    @Test
    void cacheMissAsksUserServiceWithACachedServiceToken() throws Exception {
        UUID userId = UUID.randomUUID();
        TestUser customer = new TestUser(userId, "USR-2610-" + suffix(), Role.ROLE_CUSTOMER, null, null, 3);
        USER_SERVICE.stubFor(WireMock.get(urlEqualTo("/internal/users/" + userId + "/security-state"))
                .willReturn(okJson("""
                        {"userId":"%s","publicId":"%s","role":"ROLE_CUSTOMER","status":"ACTIVE","tv":3}
                        """.formatted(userId, customer.publicId()))));

        String token = tokenFor(customer);
        mockMvc.perform(withBearer(get("/api/public/stores"), token)).andExpect(status().isOk());
        mockMvc.perform(withBearer(get("/api/public/stores"), token)).andExpect(status().isOk());

        // One lookup (then cached), one service-token request with this service's client credentials
        USER_SERVICE.verify(1, getRequestedFor(urlEqualTo("/internal/users/" + userId + "/security-state"))
                .withHeader("Authorization", equalTo("Bearer svc-token-1")));
        USER_SERVICE.verify(1, postRequestedFor(urlEqualTo("/internal/auth/service-token"))
                .withRequestBody(equalToJson("""
                        {"clientId":"store-service","clientSecret":"%s"}""".formatted(SERVICE_CLIENT_SECRET))));
        assertThat(securityStateCache.cached(userId).orElseThrow().tokenVersion()).isEqualTo(3);

        // An older token of the same user is refused without asking again
        mockMvc.perform(withBearer(get("/api/public/stores"), tokenFor(customer.withTokenVersion(2))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void unknownUserIsRevoked() throws Exception {
        UUID userId = UUID.randomUUID();
        USER_SERVICE.stubFor(WireMock.get(urlEqualTo("/internal/users/" + userId + "/security-state"))
                .willReturn(aResponse().withStatus(404)));
        TestUser ghost = new TestUser(userId, "USR-2610-" + suffix(), Role.ROLE_CUSTOMER, null, null, 1);

        mockMvc.perform(withBearer(get("/api/public/stores"), tokenFor(ghost)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("TOKEN_REVOKED"));
    }

    @Test
    void tokenNewerThanTheCacheTriggersARefresh() throws Exception {
        TestUser merchant = merchant(); // cached with tv 1
        USER_SERVICE.stubFor(WireMock.get(urlEqualTo("/internal/users/" + merchant.id() + "/security-state"))
                .willReturn(okJson("""
                        {"userId":"%s","role":"ROLE_MERCHANT","status":"ACTIVE","tv":2}""".formatted(merchant.id()))));

        mockMvc.perform(withBearer(get("/api/merchant/owner/metrics"), tokenFor(merchant.withTokenVersion(2))))
                .andExpect(status().isOk());
        assertThat(securityStateCache.cached(merchant.id()).orElseThrow().tokenVersion()).isEqualTo(2);
    }

    @Test
    void invalidTokensAreRejected() throws Exception {
        TestUser merchant = merchant();
        // Wrong audience
        String wrongAudience = encode(JwtClaimsSet.builder()
                .issuer("user-service").audience(List.of("other")).subject(merchant.id().toString())
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(60))
                .claim("role", "ROLE_MERCHANT").claim("tv", 1).build());
        mockMvc.perform(withBearer(get("/api/merchant/owner/metrics"), wrongAudience))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
        // Expired beyond the 30 s skew
        String expired = encode(JwtClaimsSet.builder()
                .issuer("user-service").audience(List.of("marketplace")).subject(merchant.id().toString())
                .issuedAt(Instant.now().minusSeconds(700)).expiresAt(Instant.now().minusSeconds(31))
                .claim("role", "ROLE_MERCHANT").claim("tv", 1).build());
        mockMvc.perform(withBearer(get("/api/merchant/owner/metrics"), expired))
                .andExpect(status().isUnauthorized());
        // Garbage
        mockMvc.perform(withBearer(get("/api/merchant/owner/metrics"), "not.a.jwt"))
                .andExpect(status().isUnauthorized());
        // No tv claim
        String noVersion = encode(JwtClaimsSet.builder()
                .issuer("user-service").audience(List.of("marketplace")).subject(merchant.id().toString())
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(60))
                .claim("role", "ROLE_MERCHANT").build());
        mockMvc.perform(withBearer(get("/api/merchant/owner/metrics"), noVersion))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("TOKEN_REVOKED"));
        USER_SERVICE.verify(0, getRequestedFor(WireMock.urlMatching("/internal/users/.*")));
    }
}
