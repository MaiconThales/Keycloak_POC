package poc.persistence.entity;

import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ReviewTest {
    @Test
    void supportsJpaNoArgumentConstructionAndAccessors() {
        Product product = new Product();
        UserEntity author = new UserEntity();
        LocalDateTime createdAt = LocalDateTime.of(2026, 10, 8, 12, 30);
        Review review = new Review();

        review.setId(11L);
        review.setProduct(product);
        review.setAuthor(author);
        review.setRating(5);
        review.setComment("Excellent product");
        review.setCreatedAt(createdAt);

        assertEquals(Long.valueOf(11L), review.getId());
        assertEquals(product, review.getProduct());
        assertEquals(author, review.getAuthor());
        assertEquals(Integer.valueOf(5), review.getRating());
        assertEquals("Excellent product", review.getComment());
        assertEquals(createdAt, review.getCreatedAt());
    }

    @Test
    void constructsReviewWithoutAssigningAnIdentifier() {
        Product product = new Product();
        UserEntity author = new UserEntity();
        LocalDateTime createdAt = LocalDateTime.of(2026, 10, 8, 12, 30);

        Review review = new Review(product, author, 4, "Good", createdAt);

        assertNull(review.getId());
        assertEquals(product, review.getProduct());
        assertEquals(author, review.getAuthor());
        assertEquals(Integer.valueOf(4), review.getRating());
        assertEquals("Good", review.getComment());
        assertEquals(createdAt, review.getCreatedAt());
    }
}
