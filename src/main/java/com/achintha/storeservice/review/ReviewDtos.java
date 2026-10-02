package com.achintha.storeservice.review;

import com.achintha.storeservice.common.Validation;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;

public final class ReviewDtos {

    private ReviewDtos() {
    }

    /**
     * @param orderPublicId  the customer's completed order with this store
     * @param attachmentKeys keys of images already uploaded to the (mock) image storage
     */
    public record CreateReviewRequest(
            @NotBlank @Pattern(regexp = Validation.PUBLIC_ID_REGEX) String orderPublicId,
            @NotNull @Min(1) @Max(5) Integer rating,
            @Size(max = Validation.TEXT_MAX) String text,
            @Size(max = 5) List<@NotBlank @Pattern(regexp = Validation.STORAGE_KEY_REGEX) String> attachmentKeys) {
    }

    public record ReplyRequest(@NotBlank @Size(max = Validation.TEXT_MAX) String text) {
    }

    public record ReplyResponse(String text, Instant createdAt) {
    }

    /** Public view: no customer or order data. */
    public record ReviewResponse(
            String publicId,
            int rating,
            String text,
            List<String> attachmentUrls,
            Instant createdAt,
            ReplyResponse reply) {
    }

    /** Merchant and admin view: also the order and the moderation state. */
    public record ManagedReviewResponse(
            String publicId,
            String orderPublicId,
            int rating,
            String text,
            List<String> attachmentUrls,
            boolean hidden,
            String hiddenReason,
            Instant hiddenAt,
            Instant createdAt,
            ReplyResponse reply) {
    }
}
