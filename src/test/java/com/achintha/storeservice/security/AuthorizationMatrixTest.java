package com.achintha.storeservice.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;

import com.achintha.storeservice.bank.Bank;
import com.achintha.storeservice.store.Store;
import com.achintha.storeservice.support.IntegrationTest;
import com.achintha.storeservice.support.TestUser;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.HttpMethod;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Role-by-endpoint authorization matrix (section 11): every endpoint is called by every role and anonymously.
 * "Allowed" calls target harmless or non-existent resources, so 404 (and the odd 400 from business rules) proves the
 * call got past authorization; 401/403 prove it did not. Assistants pass {@code /api/merchant/**} routes only with
 * the route's permission and never pass owner-only routes.
 */
class AuthorizationMatrixTest extends IntegrationTest {

    enum Who { ANONYMOUS, CUSTOMER, MERCHANT, ASSISTANT, ASSISTANT_NO_PERMS, ADMIN, SUPER_ADMIN, SERVICE }

    private static final String MISSING = "XXX-2610-ZZZZZZ";

    private static Map<Who, String> tokens;
    private static Store store;
    private static Bank bank;

    @BeforeEach
    void fixtures() throws Exception {
        if (tokens != null) {
            return;
        }
        TestUser merchant = merchant();
        store = createStore(merchant);
        bank = bank();
        tokens = new EnumMap<>(Who.class);
        tokens.put(Who.CUSTOMER, tokenFor(customer()));
        tokens.put(Who.MERCHANT, tokenFor(merchant));
        tokens.put(Who.ASSISTANT, tokenFor(assistant(merchant, AssistantPermission.values())));
        tokens.put(Who.ASSISTANT_NO_PERMS, tokenFor(assistant(merchant, AssistantPermission.ORDER_VIEW)));
        tokens.put(Who.ADMIN, tokenFor(admin()));
        tokens.put(Who.SUPER_ADMIN, tokenFor(superAdmin()));
        tokens.put(Who.SERVICE, serviceToken());
    }

    record Endpoint(HttpMethod method, String path, Supplier<String> body, Map<Who, Integer> allowed) {

        @Override
        public String toString() {
            return method + " " + path;
        }
    }

    static Stream<Arguments> matrix() {
        List<Arguments> arguments = new ArrayList<>();
        for (Endpoint endpoint : endpoints()) {
            for (Who who : Who.values()) {
                int expected = endpoint.allowed().getOrDefault(who, who == Who.ANONYMOUS ? 401 : 403);
                arguments.add(Arguments.of(endpoint, who, expected));
            }
        }
        return arguments.stream();
    }

    @ParameterizedTest(name = "{0} as {1} -> {2}")
    @MethodSource("matrix")
    void endpointAnswersAsExpected(Endpoint endpoint, Who who, int expected) throws Exception {
        String path = endpoint.path()
                .replace("{store}", store.getPublicId())
                .replace("{storeId}", store.getId().toString())
                .replace("{bank}", bank.getCode());
        MockHttpServletRequestBuilder request = request(endpoint.method(), path);
        if (endpoint.body() != null) {
            request = json(request, endpoint.body().get().replace("{storeName}", store.getName())
                    .replace("{bank}", bank.getCode()));
        }
        MockHttpServletResponse response = mockMvc.perform(withBearer(request, tokens.get(who)))
                .andReturn().getResponse();
        Assertions.assertThat(response.getStatus())
                .as("%s as %s: %s", endpoint, who, response.getContentAsString())
                .isEqualTo(expected);
    }

    // ------------------------------------------------------------------------------------------------ endpoints

    private static List<Endpoint> endpoints() {
        Map<Who, Integer> everyone = only(200, Who.values());
        Map<Who, Integer> owner = only(200, Who.MERCHANT);
        Map<Who, Integer> admins = only(200, Who.ADMIN, Who.SUPER_ADMIN);
        Map<Who, Integer> superAdmin = only(200, Who.SUPER_ADMIN);
        Map<Who, Integer> service = only(200, Who.SERVICE);
        Supplier<String> reason = () -> "{\"reason\":\"matrix test\"}";

        return List.of(
                // Public
                new Endpoint(HttpMethod.GET, "/api/public/stores", null, everyone),
                new Endpoint(HttpMethod.GET, "/api/public/stores/{store}", null, everyone),
                new Endpoint(HttpMethod.GET, "/api/public/stores/" + MISSING, null, only(404, Who.values())),
                new Endpoint(HttpMethod.GET, "/api/public/stores/{store}/reviews", null, everyone),

                // Merchant: store profile (owner only, no assistant permission covers it)
                new Endpoint(HttpMethod.GET, "/api/merchant/store", null, owner),
                new Endpoint(HttpMethod.PUT, "/api/merchant/store",
                        () -> "{\"name\":\"{storeName}\",\"contactPhone\":\"+94771234567\","
                                + "\"contactEmail\":\"a@b.test\",\"addressLine\":\"1 Road\",\"district\":\"Kandy\"}",
                        owner),
                new Endpoint(HttpMethod.PUT, "/api/merchant/store/cod", () -> "{\"enabled\":false}", owner),

                // Merchant owner only: assistants refused even with every permission
                new Endpoint(HttpMethod.GET, "/api/merchant/owner/bank-accounts", null, owner),
                new Endpoint(HttpMethod.GET, "/api/merchant/owner/bank-accounts/banks", null, owner),
                new Endpoint(HttpMethod.GET, "/api/merchant/owner/bank-accounts/" + MISSING, null,
                        only(404, Who.MERCHANT)),
                new Endpoint(HttpMethod.POST, "/api/merchant/owner/bank-accounts",
                        () -> "{\"bankCode\":\"{bank}\",\"branch\":\"Kandy\",\"accountName\":\"Matrix\","
                                + "\"accountNumber\":\"1234567890\"}", only(201, Who.MERCHANT)),
                new Endpoint(HttpMethod.PUT, "/api/merchant/owner/bank-accounts/" + MISSING,
                        () -> "{\"bankCode\":\"{bank}\",\"branch\":\"Kandy\",\"accountName\":\"Matrix\","
                                + "\"active\":true}", only(404, Who.MERCHANT)),
                new Endpoint(HttpMethod.DELETE, "/api/merchant/owner/bank-accounts/" + MISSING, null,
                        only(404, Who.MERCHANT)),
                new Endpoint(HttpMethod.GET, "/api/merchant/owner/couriers", null, owner),
                new Endpoint(HttpMethod.GET, "/api/merchant/owner/couriers/available", null, owner),
                new Endpoint(HttpMethod.PUT, "/api/merchant/owner/couriers", () -> "{\"courierCodes\":[]}", owner),
                new Endpoint(HttpMethod.GET, "/api/merchant/owner/shipping-templates", null, owner),
                new Endpoint(HttpMethod.GET, "/api/merchant/owner/shipping-templates/" + MISSING, null,
                        only(404, Who.MERCHANT)),
                new Endpoint(HttpMethod.POST, "/api/merchant/owner/shipping-templates",
                        () -> "{\"courierCode\":\"NOT_ENABLED\",\"name\":\"Flat\",\"ruleType\":\"FLAT\","
                                + "\"baseAmount\":350}", only(400, Who.MERCHANT)),
                new Endpoint(HttpMethod.PUT, "/api/merchant/owner/shipping-templates/" + MISSING,
                        () -> "{\"courierCode\":\"NOT_ENABLED\",\"name\":\"Flat\",\"ruleType\":\"FLAT\","
                                + "\"baseAmount\":350}", only(404, Who.MERCHANT)),
                new Endpoint(HttpMethod.DELETE, "/api/merchant/owner/shipping-templates/" + MISSING, null,
                        only(404, Who.MERCHANT)),
                new Endpoint(HttpMethod.GET, "/api/merchant/owner/metrics", null, owner),

                // Merchant or assistant with REVIEW_REPLY
                new Endpoint(HttpMethod.GET, "/api/merchant/reviews", null, only(200, Who.MERCHANT, Who.ASSISTANT)),
                new Endpoint(HttpMethod.POST, "/api/merchant/reviews/" + MISSING + "/reply",
                        () -> "{\"text\":\"Thanks!\"}", only(404, Who.MERCHANT, Who.ASSISTANT)),

                // Customer
                new Endpoint(HttpMethod.POST, "/api/customer/stores/{store}/reviews",
                        () -> "{\"orderPublicId\":\"ORD-2610-ZZZZZZ\",\"rating\":5}", only(404, Who.CUSTOMER)),

                // Admin (and super admin)
                new Endpoint(HttpMethod.GET, "/api/admin/stores", null, admins),
                new Endpoint(HttpMethod.GET, "/api/admin/stores/{store}", null, admins),
                new Endpoint(HttpMethod.GET, "/api/admin/stores/{store}/score", null, admins),
                new Endpoint(HttpMethod.GET, "/api/admin/stores/{store}/score-events", null, admins),
                new Endpoint(HttpMethod.GET, "/api/admin/stores/{store}/reviews", null, admins),
                new Endpoint(HttpMethod.POST, "/api/admin/stores/reviews/" + MISSING + "/hide", reason,
                        only(404, Who.ADMIN, Who.SUPER_ADMIN)),
                new Endpoint(HttpMethod.POST, "/api/admin/stores/reviews/" + MISSING + "/unhide", reason,
                        only(404, Who.ADMIN, Who.SUPER_ADMIN)),
                new Endpoint(HttpMethod.GET, "/api/admin/merchants/at-risk", null, admins),
                new Endpoint(HttpMethod.GET, "/api/admin/banks", null, admins),
                new Endpoint(HttpMethod.GET, "/api/admin/banks/{bank}", null, admins),
                new Endpoint(HttpMethod.GET, "/api/admin/couriers", null, admins),

                // Super admin only (master data writes under /api/admin, settings, holidays, credits)
                new Endpoint(HttpMethod.POST, "/api/admin/banks",
                        () -> "{\"code\":\"M" + suffix() + "\",\"name\":\"Matrix Bank\"}", only(201, Who.SUPER_ADMIN)),
                new Endpoint(HttpMethod.PUT, "/api/admin/banks/NOPE",
                        () -> "{\"name\":\"Matrix Bank\",\"active\":true}", only(404, Who.SUPER_ADMIN)),
                new Endpoint(HttpMethod.DELETE, "/api/admin/banks/NOPE", null, only(404, Who.SUPER_ADMIN)),
                new Endpoint(HttpMethod.POST, "/api/admin/couriers",
                        () -> "{\"code\":\"M" + suffix() + "\",\"name\":\"Matrix Courier\","
                                + "\"trackingUrlTemplate\":\"https://t.example.test/{trackingNumber}\","
                                + "\"trackingNumberRegex\":\"^[0-9]{8}$\",\"codSupported\":false}",
                        only(201, Who.SUPER_ADMIN)),
                new Endpoint(HttpMethod.PUT, "/api/admin/couriers/NOPE",
                        () -> "{\"name\":\"Matrix\",\"trackingUrlTemplate\":\"https://t.example.test/{trackingNumber}\","
                                + "\"trackingNumberRegex\":\"^[0-9]{8}$\",\"codSupported\":false,\"active\":true}",
                        only(404, Who.SUPER_ADMIN)),
                new Endpoint(HttpMethod.DELETE, "/api/admin/couriers/NOPE", null, only(404, Who.SUPER_ADMIN)),
                new Endpoint(HttpMethod.GET, "/api/super-admin/settings", null, superAdmin),
                new Endpoint(HttpMethod.PUT, "/api/super-admin/settings/orders.max-quote-revisions",
                        () -> "{\"value\":\"2\"}", superAdmin),
                new Endpoint(HttpMethod.GET, "/api/super-admin/settings/orders.max-quote-revisions/history", null,
                        superAdmin),
                new Endpoint(HttpMethod.GET, "/api/super-admin/holidays", null, superAdmin),
                new Endpoint(HttpMethod.POST, "/api/super-admin/holidays",
                        () -> "{\"date\":\"" + LocalDate.of(2300, 1, 1).plusDays(next() % 30000)
                                + "\",\"name\":\"Matrix Day\",\"type\":\"PUBLIC\"}", only(201, Who.SUPER_ADMIN)),
                new Endpoint(HttpMethod.PUT, "/api/super-admin/holidays/" + MISSING,
                        () -> "{\"date\":\"2300-01-01\",\"name\":\"Matrix Day\",\"type\":\"POYA\"}",
                        only(404, Who.SUPER_ADMIN)),
                new Endpoint(HttpMethod.DELETE, "/api/super-admin/holidays/" + MISSING, null,
                        only(404, Who.SUPER_ADMIN)),
                new Endpoint(HttpMethod.POST, "/api/super-admin/merchants/{store}/credits",
                        () -> "{\"amount\":100.00,\"reason\":\"matrix\"}", only(201, Who.SUPER_ADMIN)),
                new Endpoint(HttpMethod.GET, "/api/super-admin/merchants/{store}/ledger", null, superAdmin),

                // Internal (service tokens only)
                new Endpoint(HttpMethod.GET, "/internal/stores/{storeId}", null, service),
                new Endpoint(HttpMethod.GET, "/internal/stores/by-public-id/{store}", null, service),
                new Endpoint(HttpMethod.GET, "/internal/stores/{storeId}/bank-accounts", null, service),
                new Endpoint(HttpMethod.GET, "/internal/stores/{storeId}/couriers", null, service),
                new Endpoint(HttpMethod.GET, "/internal/stores/{storeId}/shipping-estimate?courier=NOPE", null,
                        only(400, Who.SERVICE)),
                new Endpoint(HttpMethod.GET, "/internal/stores/00000000-0000-0000-0000-000000000000", null,
                        only(404, Who.SERVICE)),
                new Endpoint(HttpMethod.GET, "/internal/settings", null, service),
                new Endpoint(HttpMethod.GET, "/internal/holidays?from=2026-01-01&to=2026-12-31", null, service),

                // Deny by default: unknown paths and non-GET public calls
                new Endpoint(HttpMethod.POST, "/api/public/stores", () -> "{}", Map.of()),
                new Endpoint(HttpMethod.GET, "/api/unknown", null, Map.of()));
    }

    private static Map<Who, Integer> only(int status, Who... who) {
        return Arrays.stream(who).collect(Collectors.toMap(w -> w, w -> status));
    }
}
