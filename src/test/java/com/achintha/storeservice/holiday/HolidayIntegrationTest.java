package com.achintha.storeservice.holiday;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.achintha.storeservice.outbox.OutboxRepository;
import com.achintha.storeservice.support.IntegrationTest;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** Holiday calendar CRUD (super admin), HolidaysChanged events and the internal range query. */
class HolidayIntegrationTest extends IntegrationTest {

    @Autowired
    private OutboxRepository outboxRepository;

    @Test
    void superAdminManagesHolidaysAndOtherServicesReadARange() throws Exception {
        String token = tokenFor(superAdmin());
        LocalDate day = LocalDate.of(2400, 1, 1).plusDays(next() % 3000);
        long eventsBefore = outboxRepository.findAll().stream()
                .filter(m -> m.getEventType().equals("HolidaysChanged")).count();

        String id = read(mockMvc.perform(json(withBearer(post("/api/super-admin/holidays"), token),
                        "{\"date\":\"%s\",\"name\":\"Vesak Full Moon Poya\",\"type\":\"POYA\"}".formatted(day)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.type").value("POYA"))
                .andReturn().getResponse().getContentAsString(), "$.publicId");
        assertThat(id).startsWith("HOL-");

        mockMvc.perform(json(withBearer(post("/api/super-admin/holidays"), token),
                        "{\"date\":\"%s\",\"name\":\"Duplicate\",\"type\":\"PUBLIC\"}".formatted(day)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("HOLIDAY_DATE_TAKEN"));

        mockMvc.perform(json(withBearer(put("/api/super-admin/holidays/" + id), token),
                        "{\"date\":\"%s\",\"name\":\"Vesak\",\"type\":\"PUBLIC\"}".formatted(day.plusDays(1))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.date").value(day.plusDays(1).toString()));

        mockMvc.perform(withBearer(get("/internal/holidays").param("from", day.toString())
                        .param("to", day.plusDays(5).toString()), serviceToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].name").value("Vesak"));
        mockMvc.perform(withBearer(get("/internal/holidays").param("from", day.toString())
                        .param("to", day.minusDays(1).toString()), serviceToken()))
                .andExpect(status().isBadRequest());

        mockMvc.perform(withBearer(delete("/api/super-admin/holidays/" + id), token))
                .andExpect(status().isNoContent());
        mockMvc.perform(withBearer(get("/api/super-admin/holidays").param("from", day.toString())
                        .param("to", day.plusDays(5).toString()), token))
                .andExpect(jsonPath("$.totalElements").value(0));

        // created, moved (old date removed + new date), deleted
        assertThat(outboxRepository.findAll().stream().filter(m -> m.getEventType().equals("HolidaysChanged"))
                .count()).isEqualTo(eventsBefore + 4);
    }
}
