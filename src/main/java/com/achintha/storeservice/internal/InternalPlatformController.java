package com.achintha.storeservice.internal;

import com.achintha.storeservice.holiday.HolidayDtos.InternalHoliday;
import com.achintha.storeservice.holiday.HolidayService;
import com.achintha.storeservice.settings.PlatformSettingsService;
import com.achintha.storeservice.settings.SettingsDtos.InternalSetting;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Platform settings and holidays for the other services ({@code ROLE_SERVICE} only). They cache both and drop the
 * cache on {@code SettingsChanged} / {@code HolidaysChanged}.
 */
@RestController
@RequestMapping("/internal")
@PreAuthorize("hasAuthority('ROLE_SERVICE')")
@RequiredArgsConstructor
public class InternalPlatformController {

    private final PlatformSettingsService settingsService;
    private final HolidayService holidayService;

    @GetMapping("/settings")
    public List<InternalSetting> settings() {
        return settingsService.internalList();
    }

    /** Holidays between {@code from} and {@code to} (inclusive, Colombo dates, at most 800 days). */
    @GetMapping("/holidays")
    public List<InternalHoliday> holidays(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return holidayService.range(from, to);
    }
}
