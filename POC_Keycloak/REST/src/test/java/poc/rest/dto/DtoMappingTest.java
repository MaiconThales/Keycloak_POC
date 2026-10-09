package poc.rest.dto;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;

import poc.persistence.entity.Product;
import poc.persistence.entity.Review;
import poc.persistence.entity.UserEntity;

class DtoMappingTest {
    @Test
    void roundTripsProductAndUserInputsAndOutputs() {
        ProductInput productInput = new ProductInput();
        productInput.setName("Notebook");
        productInput.setPrice(new BigDecimal("12.50"));
        productInput.setSku("NTB");
        assertEquals("Notebook", productInput.getName());
        assertEquals(new BigDecimal("12.50"), productInput.getPrice());
        assertEquals("NTB", productInput.getSku());

        Product product = new Product(productInput.getName(), productInput.getPrice(), productInput.getSku());
        product.setId(8L);
        ProductResponse productResponse = ProductResponse.from(product);
        assertEquals(Long.valueOf(8L), productResponse.getId());
        assertEquals("Notebook", productResponse.getName());
        assertEquals(new BigDecimal("12.50"), productResponse.getPrice());
        assertEquals("NTB", productResponse.getSku());

        UserInput userInput = new UserInput();
        userInput.setUsername("jdoe"); userInput.setEmail("j@x.test");
        userInput.setFirstName("John"); userInput.setLastName("Doe"); userInput.setPassword("secret");
        assertEquals("jdoe", userInput.getUsername());
        assertEquals("secret", userInput.getPassword());
        UserEntity user = new UserEntity("kc-1", userInput.getUsername(), userInput.getEmail(),
                userInput.getFirstName(), userInput.getLastName(), true);
        user.setId(9L);
        UserResponse response = UserResponse.from(user);
        assertEquals(Long.valueOf(9L), response.getId());
        assertEquals("jdoe", response.getUsername());
        assertEquals("j@x.test", response.getEmail());
        assertEquals(Boolean.TRUE, response.getActive());
    }

    @Test
    void mapsReviewRelationsTimestampAndNullRelationsWithoutLeakingEntities() {
        ReviewInput input = new ReviewInput();
        input.setProductId(8L); input.setRating(5); input.setComment("Excellent");
        assertEquals(Long.valueOf(8L), input.getProductId());
        assertEquals(Integer.valueOf(5), input.getRating());
        assertEquals("Excellent", input.getComment());

        Product product = new Product("Notebook", new BigDecimal("12.50"), "NTB");
        product.setId(8L);
        UserEntity author = new UserEntity("kc-1", "jdoe", "j@x.test", "John", "Doe", true);
        Review review = new Review(product, author, 5, "Excellent", LocalDateTime.of(2026, 1, 2, 3, 4, 5));
        review.setId(10L);
        ReviewResponse output = ReviewResponse.from(review);
        assertEquals(Long.valueOf(10L), output.getId());
        assertEquals(Long.valueOf(8L), output.getProductId());
        assertEquals("Notebook", output.getProductName());
        assertEquals("jdoe", output.getAuthorUsername());
        assertEquals(Integer.valueOf(5), output.getRating());
        assertEquals("2026-01-02T03:04:05", output.getCreatedAt());
        assertNull(ReviewResponse.from(null));
        assertNull(ReviewResponse.from(new Review(null, null, 1, "x", null)).getProductId());
    }
}
