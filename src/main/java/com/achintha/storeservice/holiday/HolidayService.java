package com.achintha.storeservice.holiday;

import com.achintha.storeservice.audit.AuditService;
import com.achintha.storeservice.common.PageResponse;
import com.achintha.storeservice.common.PublicIdGenerator;
import com.achintha.storeservice.common.TextSanitizer;
import com.achintha.storeservice.exception.ApiException;
import com.achintha.storeservice.exception.ErrorCode;
import com.achintha.storeservice.exception.NotFoundException;
import com.achintha.storeservice.holiday.HolidayDtos.HolidayRequest;
import com.achintha.storeservice.holiday.HolidayDtos.HolidayResponse;
import com.achintha.storeservice.holiday.HolidayDtos.InternalHoliday;
import com.achintha.storeservice.outbox.StoreEventPublisher;
import com.achintha.storeservice.security.Actor;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Holiday calendar (super admin). Every change is audited and emits {@code HolidaysChanged}. */
@Service
@RequiredArgsConstructor
public class HolidayService {

    /** Longest range served by one internal call (keeps the response small). */
    static final int MAX_RANGE_DAYS = 800;

    private final HolidayRepository repository;
    private final PublicIdGenerator publicIds;
    private final AuditService auditService;
    private final StoreEventPublisher events;
    private final Clock clock;

    @Transactional(readOnly = true)
    public PageResponse<HolidayResponse> list(LocalDate from, LocalDate to, Pageable pageable) {
        return PageResponse.from(repository.findAllByDateBetween(from, to, pageable), HolidayResponse::from);
    }

    @Transactional(readOnly = true)
    public List<InternalHoliday> range(LocalDate from, LocalDate to) {
        if (to.isBefore(from)) {
            throw ApiException.badRequest(ErrorCode.BAD_REQUEST, "to must not be before from");
        }
        if (from.plusDays(MAX_RANGE_DAYS).isBefore(to)) {
            throw ApiException.badRequest(ErrorCode.BAD_REQUEST, "the range may span at most " + MAX_RANGE_DAYS
                    + " days");
        }
        return repository.findAllByDateBetweenOrderByDateAsc(from, to).stream()
                .map(h -> new InternalHoliday(h.getDate(), h.getName(), h.getType()))
                .toList();
    }

    @Transactional
    public HolidayResponse create(Actor actor, HolidayRequest request) {
        if (repository.existsByDate(request.date())) {
            throw dateTaken();
        }
        Instant now = clock.instant();
        Holiday holiday = new Holiday();
        holiday.setId(UUID.randomUUID());
        holiday.setPublicId(publicIds.generateUnique(PublicIdGenerator.HOLIDAY_PREFIX, repository::existsByPublicId));
        holiday.setCreatedAt(now);
        apply(holiday, request, now);
        holiday = save(holiday);
        auditService.record(actor, "HOLIDAY_CREATED", AuditService.TARGET_HOLIDAY, holiday.getPublicId(), null,
                snapshot(holiday), null);
        events.holidaysChanged(holiday.getDate(), "CREATED");
        return HolidayResponse.from(holiday);
    }

    @Transactional
    public HolidayResponse update(Actor actor, String publicId, HolidayRequest request) {
        Holiday holiday = require(publicId);
        Map<String, Object> before = snapshot(holiday);
        LocalDate previousDate = holiday.getDate();
        if (!previousDate.equals(request.date()) && repository.existsByDate(request.date())) {
            throw dateTaken();
        }
        apply(holiday, request, clock.instant());
        holiday = save(holiday);
        auditService.record(actor, "HOLIDAY_UPDATED", AuditService.TARGET_HOLIDAY, holiday.getPublicId(), before,
                snapshot(holiday), null);
        if (!previousDate.equals(holiday.getDate())) {
            events.holidaysChanged(previousDate, "REMOVED");
        }
        events.holidaysChanged(holiday.getDate(), "UPDATED");
        return HolidayResponse.from(holiday);
    }

    @Transactional
    public void delete(Actor actor, String publicId) {
        Holiday holiday = require(publicId);
        repository.delete(holiday);
        auditService.record(actor, "HOLIDAY_DELETED", AuditService.TARGET_HOLIDAY, holiday.getPublicId(),
                snapshot(holiday), null, null);
        events.holidaysChanged(holiday.getDate(), "REMOVED");
    }

    private Holiday require(String publicId) {
        return repository.findByPublicId(publicId).orElseThrow(() -> new NotFoundException("Holiday not found"));
    }

    private Holiday save(Holiday holiday) {
        try {
            return repository.saveAndFlush(holiday);
        } catch (DataIntegrityViolationException e) {
            throw dateTaken();
        }
    }

    private static void apply(Holiday holiday, HolidayRequest request, Instant now) {
        String name = TextSanitizer.cleanLine(request.name());
        if (name == null) {
            throw ApiException.badRequest(ErrorCode.VALIDATION_FAILED, "name must not be blank");
        }
        holiday.setDate(request.date());
        holiday.setName(name);
        holiday.setType(request.type());
        holiday.setUpdatedAt(now);
    }

    private static ApiException dateTaken() {
        return ApiException.conflict(ErrorCode.HOLIDAY_DATE_TAKEN, "There is already a holiday on this date");
    }

    private static Map<String, Object> snapshot(Holiday holiday) {
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("date", holiday.getDate().toString());
        state.put("name", holiday.getName());
        state.put("type", holiday.getType());
        return state;
    }
}
