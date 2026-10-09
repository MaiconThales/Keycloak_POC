package poc.rest.resource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.mockito.Mockito.*;
import java.security.Principal;
import java.time.LocalDateTime;
import java.util.Arrays;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.SecurityContext;
import javax.annotation.security.RolesAllowed;
import org.junit.jupiter.api.Test;
import poc.persistence.entity.Product;
import poc.persistence.entity.Review;
import poc.persistence.entity.UserEntity;
import poc.persistence.service.ReviewServiceLocal;
import poc.persistence.service.UserServiceLocal;
import poc.rest.dto.ReviewInput;
import poc.rest.dto.ReviewResponse;

class ReviewResourceTest {
    @Test void exposesContractRolesForReviewOperations() throws Exception {
        assertArrayEquals(new String[] {"User", "User-Write"}, ReviewResource.class
                .getMethod("create", ReviewInput.class).getAnnotation(RolesAllowed.class).value());
        assertArrayEquals(new String[] {"Admin", "Sub-Admin", "User", "Admin-Update", "Sub-Admin-Update", "User-Update"},
                ReviewResource.class.getMethod("update", Long.class, ReviewInput.class)
                        .getAnnotation(RolesAllowed.class).value());
        assertArrayEquals(new String[] {"Admin", "Sub-Admin", "User", "Admin-Delete", "Sub-Admin-Delete", "User-Delete"},
                ReviewResource.class.getMethod("delete", Long.class).getAnnotation(RolesAllowed.class).value());
    }

    @Test void listsAndMapsReviewWithoutLeakingEntity() {
        ReviewServiceLocal reviews = mock(ReviewServiceLocal.class);
        UserServiceLocal users = mock(UserServiceLocal.class);
        when(reviews.findReviews(3L, 5)).thenReturn(Arrays.asList(review()));
        ReviewResponse result = new ReviewResource(reviews, users, context("john")).find(3L, 5).get(0);
        assertEquals("Phone", result.getProductName());
        assertEquals("john", result.getAuthorUsername());
    }

    @Test void createsUsingAuthenticatedPrincipalNotPayloadIdentity() {
        ReviewServiceLocal reviews = mock(ReviewServiceLocal.class); UserServiceLocal users = mock(UserServiceLocal.class);
        when(users.findUserIdByPrincipal("john")).thenReturn(9L);
        when(reviews.createReview(3L, 9L, 4, "good")).thenReturn(review());
        Response response = new ReviewResource(reviews, users, context("john")).create(input(3L, 4, " good "));
        assertEquals(201, response.getStatus()); verify(reviews).createReview(3L, 9L, 4, "good");
    }

    @Test void updatesAndDeletesThroughService() {
        ReviewServiceLocal reviews = mock(ReviewServiceLocal.class); UserServiceLocal users = mock(UserServiceLocal.class);
        when(users.findUserIdByPrincipal("john")).thenReturn(9L); when(reviews.updateReview(2L, 9L, 5, "new")).thenReturn(review());
        ReviewResource resource = new ReviewResource(reviews, users, context("john"));
        assertEquals(200, resource.update(2L, input(3L, 5, "new")).getStatus());
        assertEquals(204, resource.delete(2L).getStatus()); verify(reviews).deleteReview(2L, 9L);
    }

    @Test void rejectsInvalidInputAndUnknownPrincipalBeforeEjbCall() {
        ReviewServiceLocal reviews = mock(ReviewServiceLocal.class); UserServiceLocal users = mock(UserServiceLocal.class);
        when(users.findUserIdByPrincipal("unknown")).thenReturn(null);
        ReviewResource resource = new ReviewResource(reviews, users, context("unknown"));
        assertEquals(400, resource.create(input(null, 6, " ")).getStatus());
        assertEquals(403, resource.create(input(3L, 3, "ok")).getStatus());
        assertEquals(400, resource.update(null, input(3L, 3, "ok")).getStatus());
        assertEquals(400, resource.delete(null).getStatus()); verifyNoInteractions(reviews);
    }

    private ReviewInput input(Long product, Integer rating, String comment) {
        ReviewInput input = new ReviewInput(); input.setProductId(product); input.setRating(rating); input.setComment(comment); return input;
    }
    private Review review() {
        Product product = new Product("Phone", null, "P"); product.setId(3L);
        UserEntity user = new UserEntity("kc", "john", "j@x", "John", "Doe", true); user.setId(9L);
        return new Review(product, user, 5, "great", LocalDateTime.of(2026, 1, 1, 1, 1));
    }
    private SecurityContext context(String name) {
        SecurityContext context = mock(SecurityContext.class);
        when(context.getUserPrincipal()).thenReturn(new Principal() { public String getName() { return name; } });
        return context;
    }
}
