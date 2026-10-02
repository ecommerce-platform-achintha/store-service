package com.achintha.storeservice.settings;

import com.achintha.storeservice.common.PageResponse;
import com.achintha.storeservice.common.Pagination;
import com.achintha.storeservice.config.OpenApiConfig;
import com.achintha.storeservice.security.Actor;
import com.achintha.storeservice.settings.SettingsDtos.SettingHistoryResponse;
import com.achintha.storeservice.settings.SettingsDtos.SettingResponse;
import com.achintha.storeservice.settings.SettingsDtos.UpdateSettingRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Platform settings (section 7). Super admin only; every change is validated, audited and kept in the history. */
@RestController
@RequestMapping("/api/super-admin/settings")
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
@PreAuthorize("hasAuthority('ROLE_SUPER_ADMIN')")
@RequiredArgsConstructor
@Tag(name = "Super admin: settings")
public class SettingsController {

    private static final Map<String, String> HISTORY_SORTABLE = Map.of("changedAt", "changedAt");

    private final PlatformSettingsService settingsService;

    @GetMapping
    @Operation(summary = "All settings with type, default and allowed range")
    public List<SettingResponse> list() {
        return settingsService.list();
    }

    @PutMapping("/{key}")
    @Operation(summary = "Update one setting (value checked against its type and range; emits SettingsChanged)")
    public SettingResponse update(@AuthenticationPrincipal Jwt jwt, @PathVariable String key,
                                  @Valid @RequestBody UpdateSettingRequest request) {
        return settingsService.update(Actor.from(jwt), key, request.value(), request.reason());
    }

    @GetMapping("/{key}/history")
    @Operation(summary = "Change history of one setting, newest first")
    public PageResponse<SettingHistoryResponse> history(@PathVariable String key,
                                                        @RequestParam(defaultValue = "0") int page,
                                                        @RequestParam(defaultValue = "20") int size,
                                                        @RequestParam(defaultValue = "changedAt,desc") String sort) {
        return settingsService.history(key,
                Pagination.of(page, size, sort, HISTORY_SORTABLE, Pagination.MAX_ADMIN_SIZE));
    }
}
