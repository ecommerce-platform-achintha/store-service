package com.achintha.storeservice.shipping;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.util.List;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/** Stores a template's rate lines as a JSON array in a text column. */
@Converter
public class ShippingRatesConverter implements AttributeConverter<List<ShippingRate>, String> {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final TypeReference<List<ShippingRate>> TYPE = new TypeReference<>() {
    };

    @Override
    public String convertToDatabaseColumn(List<ShippingRate> rates) {
        return JSON.writeValueAsString(rates == null ? List.of() : rates);
    }

    @Override
    public List<ShippingRate> convertToEntityAttribute(String json) {
        return json == null || json.isBlank() ? List.of() : List.copyOf(JSON.readValue(json, TYPE));
    }
}
