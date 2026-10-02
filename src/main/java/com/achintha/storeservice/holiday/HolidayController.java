package com.achintha.storeservice.holiday;

import com.achintha.storeservice.common.PageResponse;
import com.achintha.storeservice.common.Pagination;
import com.achintha.storeservice.config.OpenApiConfig;
import com.achintha.storeservice.holiday.HolidayDtos.HolidayRequest;
import com.achintha.storeservice.holiday.HolidayDtos.HolidayResponse;
import com.achintha.storeservice.security.Actor;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Holiday calendar (public and Poya days, Colombo dates). Super admin only. */
@RestController
@RequestMapping("/api/super-admin/holidays")
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
@PreAuthorize("hasAuthority('ROLE_SUPER_ADMIN')")
@RequiredArgsConstructor
@Tag(name = "Super admin: holidays")
public class HolidayController {

    private static final Map<String, String> SORTABLE = Map.of("date", "date", "name", "name");

    private final HolidayService holidayService;

    @GetMapping
    @Operation(summary = "Holidays between from and to (default: the whole calendar)")
    public PageResponse<HolidayResponse> list(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            @RequestParam(defaultValue = "date,asc") String sort) {
        return holidayService.list(from != null ? from : LocalDate.of(1900, 1, 1),
                to != null ? to : LocalDate.of(9999, 12, 31),
                Pagination.of(page, size, sort, SORTABLE, Pagination.MAX_ADMIN_SIZE));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Add a holiday (one per date)")
    public HolidayResponse create(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody HolidayRequest request) {
        return holidayService.create(Actor.from(jwt), request);
    }

    @PutMapping("/{publicId}")
    @Operation(summary = "Change a holiday")
    public HolidayResponse update(@AuthenticationPrincipal Jwt jwt, @PathVariable String publicId,
                                  @Valid @RequestBody HolidayRequest request) {
        return holidayService.update(Actor.from(jwt), publicId, request);
    }

    @DeleteMapping("/{publicId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Remove a holiday")
    public void delete(@AuthenticationPrincipal Jwt jwt, @PathVariable String publicId) {
        holidayService.delete(Actor.from(jwt), publicId);
    }
}
