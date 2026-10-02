package com.achintha.storeservice.store;

import com.achintha.storeservice.common.PageResponse;
import com.achintha.storeservice.common.Pagination;
import com.achintha.storeservice.review.ReviewDtos.ReviewResponse;
import com.achintha.storeservice.review.ReviewService;
import com.achintha.storeservice.store.StoreResponses.PublicStoreResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public store search and detail (D16). Only stores that are published and accepting orders exist here; responses
 * carry public fields only (no UUIDs, contact details, owner or bank data).
 */
@RestController
@RequestMapping("/api/public/stores")
@RequiredArgsConstructor
@Tag(name = "Public: stores")
public class PublicStoreController {

    static final Map<String, String> SORTABLE = Map.of("name", "name", "rating", "ratingAvg",
            "newest", "createdAt");
    static final Map<String, String> REVIEW_SORTABLE = Map.of("createdAt", "createdAt", "rating", "rating");

    private final StoreQueryService storeQueryService;
    private final ReviewService reviewService;

    @GetMapping
    @Operation(summary = "Search visible stores by name (q); sort by name, rating or newest; max page size 50")
    public PageResponse<PublicStoreResponse> search(@RequestParam(required = false) String q,
                                                    @RequestParam(defaultValue = "0") int page,
                                                    @RequestParam(defaultValue = "20") int size,
                                                    @RequestParam(defaultValue = "name,asc") String sort) {
        if (q != null && q.length() > 100) {
            q = q.substring(0, 100);
        }
        return storeQueryService.searchPublic(q, Pagination.of(page, size, sort, SORTABLE, Pagination.MAX_SIZE));
    }

    @GetMapping("/{publicId}")
    public PublicStoreResponse get(@PathVariable String publicId) {
        return storeQueryService.getPublic(publicId);
    }

    @GetMapping("/{publicId}/reviews")
    @Operation(summary = "Visible reviews of a store, with the store's reply")
    public PageResponse<ReviewResponse> reviews(@PathVariable String publicId,
                                                @RequestParam(defaultValue = "0") int page,
                                                @RequestParam(defaultValue = "20") int size,
                                                @RequestParam(defaultValue = "createdAt,desc") String sort) {
        return reviewService.publicReviews(publicId,
                Pagination.of(page, size, sort, REVIEW_SORTABLE, Pagination.MAX_SIZE));
    }
}
