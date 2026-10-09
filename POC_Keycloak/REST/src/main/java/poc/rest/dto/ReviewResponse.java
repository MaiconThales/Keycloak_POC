package poc.rest.dto;

import java.time.LocalDateTime;
import poc.persistence.entity.Review;

/** Stable public representation; JPA relationships never leak through REST. */
public class ReviewResponse {
    private Long id;
    private Long productId;
    private String productName;
    private String authorUsername;
    private Integer rating;
    private String comment;
    private String createdAt;

    public static ReviewResponse from(Review review) {
        if (review == null) return null;
        ReviewResponse result = new ReviewResponse();
        result.id = review.getId();
        result.productId = review.getProduct() == null ? null : review.getProduct().getId();
        result.productName = review.getProduct() == null ? null : review.getProduct().getName();
        result.authorUsername = review.getAuthor() == null ? null : review.getAuthor().getUsername();
        result.rating = review.getRating();
        result.comment = review.getComment();
        LocalDateTime timestamp = review.getCreatedAt();
        result.createdAt = timestamp == null ? null : timestamp.toString();
        return result;
    }
    public Long getId() { return id; }
    public Long getProductId() { return productId; }
    public String getProductName() { return productName; }
    public String getAuthorUsername() { return authorUsername; }
    public Integer getRating() { return rating; }
    public String getComment() { return comment; }
    public String getCreatedAt() { return createdAt; }
}
