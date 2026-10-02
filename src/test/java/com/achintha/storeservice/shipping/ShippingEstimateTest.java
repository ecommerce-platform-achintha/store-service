package com.achintha.storeservice.shipping;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.achintha.storeservice.courier.Courier;
import com.achintha.storeservice.store.Store;
import com.achintha.storeservice.support.IntegrationTest;
import com.achintha.storeservice.support.TestUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Store couriers, shipping templates and the internal shipping estimate for order-service. */
class ShippingEstimateTest extends IntegrationTest {

    private Store store;
    private String token;
    private Courier flat;
    private Courier district;
    private Courier weight;

    @BeforeEach
    void setUp() throws Exception {
        TestUser merchant = merchant();
        store = createStore(merchant);
        token = tokenFor(merchant);
        flat = courier();
        district = courier();
        weight = courier();
        mockMvc.perform(json(withBearer(put("/api/merchant/owner/couriers"), token),
                        "{\"courierCodes\":[\"%s\",\"%s\",\"%s\"]}".formatted(flat.getCode(), district.getCode(),
                                weight.getCode())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3));
        template("{\"courierCode\":\"%s\",\"name\":\"Island-wide\",\"ruleType\":\"FLAT\",\"baseAmount\":350}"
                .formatted(flat.getCode()));
        template(("{\"courierCode\":\"%s\",\"name\":\"By district\",\"ruleType\":\"PER_DISTRICT\","
                + "\"rates\":[{\"district\":\"Colombo\",\"amount\":300},{\"district\":\"Jaffna\",\"amount\":650}]}")
                .formatted(district.getCode()));
        template(("{\"courierCode\":\"%s\",\"name\":\"By weight\",\"ruleType\":\"WEIGHT_BAND\",\"baseAmount\":2000,"
                + "\"rates\":[{\"maxWeightKg\":5,\"amount\":900},{\"maxWeightKg\":1,\"amount\":400}]}")
                .formatted(weight.getCode()));
    }

    private void template(String body) throws Exception {
        mockMvc.perform(json(withBearer(post("/api/merchant/owner/shipping-templates"), token), body))
                .andExpect(status().isCreated());
    }

    private String estimate() {
        return "/internal/stores/" + store.getId() + "/shipping-estimate";
    }

    @Test
    void estimatesFollowTheTemplateRules() throws Exception {
        String service = serviceToken();
        mockMvc.perform(withBearer(get(estimate()).param("courier", flat.getCode()), service))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.amount").value(350))
                .andExpect(jsonPath("$.estimate").value(true))
                .andExpect(jsonPath("$.currency").value("LKR"));
        mockMvc.perform(withBearer(get(estimate()).param("courier", district.getCode()).param("district", "jaffna"),
                        service))
                .andExpect(jsonPath("$.amount").value(650));
        // District without a rate and no base amount: no estimate
        mockMvc.perform(withBearer(get(estimate()).param("courier", district.getCode()).param("district", "Galle"),
                        service))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("SHIPPING_ESTIMATE_UNAVAILABLE"));
        // Bands are sorted: 0.8 kg falls in "up to 1 kg", 3 kg in "up to 5 kg", heavier uses the base amount
        mockMvc.perform(withBearer(get(estimate()).param("courier", weight.getCode()).param("weight", "0.8"),
                        service))
                .andExpect(jsonPath("$.amount").value(400));
        mockMvc.perform(withBearer(get(estimate()).param("courier", weight.getCode()).param("weight", "3"), service))
                .andExpect(jsonPath("$.amount").value(900));
        mockMvc.perform(withBearer(get(estimate()).param("courier", weight.getCode()).param("weight", "12"), service))
                .andExpect(jsonPath("$.amount").value(2000));
    }

    @Test
    void couriersMustBeEnabledAndFromTheMasterList() throws Exception {
        Courier other = courier();
        mockMvc.perform(withBearer(get(estimate()).param("courier", other.getCode()), serviceToken()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COURIER_NOT_ENABLED"));
        mockMvc.perform(json(withBearer(put("/api/merchant/owner/couriers"), token), "{\"courierCodes\":[\"NOPE\"]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COURIER_NOT_AVAILABLE"));
        // Internal courier list for order-service carries the tracking template and regex
        mockMvc.perform(withBearer(get("/internal/stores/" + store.getId() + "/couriers"), serviceToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].trackingUrlTemplate").value("https://track.example.test/?id={trackingNumber}"))
                .andExpect(jsonPath("$[0].trackingNumberRegex").value("^[A-Z0-9]{8,12}$"));
    }

    @Test
    void invalidTemplatesAreRefused() throws Exception {
        mockMvc.perform(json(withBearer(post("/api/merchant/owner/shipping-templates"), token),
                        "{\"courierCode\":\"%s\",\"name\":\"Again\",\"ruleType\":\"FLAT\",\"baseAmount\":1}"
                                .formatted(flat.getCode())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SHIPPING_TEMPLATE_EXISTS"));
        Courier fresh = courier();
        mockMvc.perform(json(withBearer(put("/api/merchant/owner/couriers"), token),
                "{\"courierCodes\":[\"%s\",\"%s\"]}".formatted(flat.getCode(), fresh.getCode())));
        mockMvc.perform(json(withBearer(post("/api/merchant/owner/shipping-templates"), token),
                        "{\"courierCode\":\"%s\",\"name\":\"Bad\",\"ruleType\":\"FLAT\"}".formatted(fresh.getCode())))
                .andExpect(status().isBadRequest());
        mockMvc.perform(json(withBearer(post("/api/merchant/owner/shipping-templates"), token),
                        ("{\"courierCode\":\"%s\",\"name\":\"Bad\",\"ruleType\":\"PER_DISTRICT\","
                                + "\"rates\":[{\"district\":\"Kandy\",\"amount\":1},{\"district\":\"KANDY\",\"amount\":2}]}")
                                .formatted(fresh.getCode())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("district KANDY appears twice"));
        mockMvc.perform(json(withBearer(post("/api/merchant/owner/shipping-templates"), token),
                        ("{\"courierCode\":\"%s\",\"name\":\"Bad\",\"ruleType\":\"WEIGHT_BAND\","
                                + "\"rates\":[{\"maxWeightKg\":1,\"amount\":-5}]}").formatted(fresh.getCode())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }
}
