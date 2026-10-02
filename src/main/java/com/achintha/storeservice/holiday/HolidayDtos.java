package com.achintha.storeservice.holiday;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.time.LocalDate;

public final class HolidayDtos {

    private HolidayDtos() {
    }

    public record HolidayRequest(
            @NotNull LocalDate date,
            @NotBlank @Size(max = 100) String name,
            @NotNull HolidayType type) {
    }

    public record HolidayResponse(String publicId, LocalDate date, String name, HolidayType type,
                                  Instant createdAt, Instant updatedAt) {

        static HolidayResponse from(Holiday holiday) {
            return new HolidayResponse(holiday.getPublicId(), holiday.getDate(), holiday.getName(),
                    holiday.getType(), holiday.getCreatedAt(), holiday.getUpdatedAt());
        }
    }

    /** Internal view for order-service's deadline calculator. */
    public record InternalHoliday(LocalDate date, String name, HolidayType type) {
    }
}
