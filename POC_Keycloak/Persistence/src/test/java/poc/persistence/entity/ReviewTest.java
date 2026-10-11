package poc.persistence.entity;

import java.lang.reflect.Field;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.Table;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.persistence.EntityManager;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/** Unit coverage for the Review JPA model without starting a persistence unit. */
class ReviewTest {
    private EntityManager entityManager;

    @BeforeEach
    void setUp() {
        entityManager = mock(EntityManager.class);
    }

    @Test
    void supportsJpaConstructionAndAllAccessors() {
        Product product = new Product("Notebook", null, "NTB");
        Review review = new Review();

        review.setId(7L);
        review.setProduct(product);
        review.setUserKeycloakId("user-7");
        review.setComment("Excellent product");

        assertEquals(Long.valueOf(7), review.getId());
        assertSame(product, review.getProduct());
        assertEquals("user-7", review.getUserKeycloakId());
        assertEquals("Excellent product", review.getComment());
    }

    @Test
    void constructorAssociatesReviewWithItsProductAndLeavesIdUnset() {
        Product product = new Product("Mouse", null, "MSE");

        Review review = new Review(product, "keycloak-user", "Useful");

        assertNull(review.getId());
        assertSame(product, review.getProduct());
        assertEquals("keycloak-user", review.getUserKeycloakId());
        assertEquals("Useful", review.getComment());
    }

    @Test
    void persistsReviewAndKeepsTheProductRelationship() {
        Product product = new Product("Keyboard", null, "KBD");
        Review review = new Review(product, "user-1", "Good");
        doAnswer(invocation -> {
            invocation.<Review>getArgument(0).setId(11L);
            return null;
        }).when(entityManager).persist(review);

        entityManager.persist(review);

        verify(entityManager).persist(review);
        assertEquals(Long.valueOf(11), review.getId());
        assertSame(product, review.getProduct());
    }

    @Test
    void declaresTheExpectedJPARelationshipAndColumnConstraints() throws Exception {
        assertNotNull(Review.class.getAnnotation(Entity.class));
        assertEquals("reviews", Review.class.getAnnotation(Table.class).name());

        Field productField = Review.class.getDeclaredField("product");
        ManyToOne manyToOne = productField.getAnnotation(ManyToOne.class);
        JoinColumn joinColumn = productField.getAnnotation(JoinColumn.class);
        assertNotNull(manyToOne);
        assertTrue(!manyToOne.optional());
        assertEquals("product_id", joinColumn.name());
        assertTrue(!joinColumn.nullable());

        Column userColumn = Review.class.getDeclaredField("userKeycloakId").getAnnotation(Column.class);
        Column commentColumn = Review.class.getDeclaredField("comment").getAnnotation(Column.class);
        assertEquals("user_keycloak_id", userColumn.name());
        assertEquals(255, userColumn.length());
        assertTrue(!userColumn.nullable());
        assertEquals("comment", commentColumn.name());
        assertEquals("TEXT", commentColumn.columnDefinition());
        assertTrue(!commentColumn.nullable());
    }
}
