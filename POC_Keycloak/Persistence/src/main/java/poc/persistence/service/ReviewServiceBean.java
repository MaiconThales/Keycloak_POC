package poc.persistence.service;

import java.time.LocalDateTime;
import java.util.List;

import javax.annotation.Resource;
import javax.annotation.security.RolesAllowed;
import javax.ejb.SessionContext;
import javax.ejb.Stateless;
import javax.ejb.TransactionAttribute;
import javax.ejb.TransactionAttributeType;
import javax.persistence.EntityManager;
import javax.persistence.EntityNotFoundException;
import javax.persistence.PersistenceException;
import javax.persistence.PersistenceContext;
import javax.persistence.TypedQuery;

import poc.persistence.entity.Product;
import poc.persistence.entity.Review;
import poc.persistence.entity.UserEntity;

/**
 * CMT service for reviews.  The caller's local user id is supplied by the
 * facade after resolving the authenticated identity; the bean still enforces
 * ownership and moderation, so that the rule cannot be bypassed by another
 * caller of the local EJB view.
 */
@Stateless
@TransactionAttribute(TransactionAttributeType.REQUIRED)
public class ReviewServiceBean implements ReviewServiceLocal {
    private static final int MIN_RATING = 1;
    private static final int MAX_RATING = 5;

    @PersistenceContext(unitName = "MinhaAppPU")
    private EntityManager entityManager;

    @Resource
    private SessionContext sessionContext;

    public ReviewServiceBean() {
    }

    /* Package-private constructor keeps unit tests independent of a container. */
    ReviewServiceBean(EntityManager entityManager, SessionContext sessionContext) {
        this.entityManager = entityManager;
        this.sessionContext = sessionContext;
    }

    @Override
    @RolesAllowed({"Admin", "Sub-Admin", "User", "Admin-Read", "Sub-Admin-Read", "User-Read"})
    public List<Review> findReviews(Long productId, Integer rating) {
        validateOptionalProductId(productId);
        validateOptionalRating(rating);
        StringBuilder jpql = new StringBuilder("SELECT r FROM Review r WHERE 1 = 1");
        if (productId != null) jpql.append(" AND r.product.id = :productId");
        if (rating != null) jpql.append(" AND r.rating = :rating");
        jpql.append(" ORDER BY r.createdAt DESC, r.id DESC");
        TypedQuery<Review> query = entityManager.createQuery(jpql.toString(), Review.class);
        if (productId != null) query.setParameter("productId", productId);
        if (rating != null) query.setParameter("rating", rating);
        return query.getResultList();
    }

    @Override
    @RolesAllowed({"Admin", "Sub-Admin", "User", "Admin-Write", "Sub-Admin-Write", "User-Write"})
    public Review createReview(Long productId, Long authorId, Integer rating, String comment) {
        validateId(productId, "Product id");
        validateId(authorId, "Author id");
        validateRating(rating);
        validateComment(comment);
        Product product = findRequired(Product.class, productId, "Product");
        UserEntity author = findRequired(UserEntity.class, authorId, "Author");
        verifyCaller(authorId);
        if (alreadyReviewed(productId, authorId)) {
            throw new IllegalArgumentException("An author may review a product only once.");
        }
        Review review = new Review(product, author, rating, comment, LocalDateTime.now());
        try {
            entityManager.persist(review);
            // Force the INSERT while this transaction is still in the EJB
            // method.  Without a flush, a concurrent duplicate can fail only
            // at CMT commit, outside this translation boundary; the database
            // unique index remains the authoritative race-safe guard.
            entityManager.flush();
        } catch (PersistenceException ex) {
            // The unique index is the final race-safe guard. Expose its business
            // failure as a client validation error rather than a server error.
            throw new IllegalArgumentException("An author may review a product only once.", ex);
        }
        return review;
    }

    @Override
    @RolesAllowed({"Admin", "Sub-Admin", "User", "Admin-Update", "Sub-Admin-Update", "User-Update"})
    public Review updateReview(Long id, Long authorId, Integer rating, String comment) {
        validateId(id, "Review id");
        validateId(authorId, "Author id");
        validateRating(rating);
        validateComment(comment);
        verifyCaller(authorId);
        Review review = findRequired(Review.class, id, "Review");
        ensureOwnerOrModerator(review, authorId);
        review.setRating(rating);
        review.setComment(comment);
        return review;
    }

    @Override
    @RolesAllowed({"Admin", "Sub-Admin", "User", "Admin-Delete", "Sub-Admin-Delete", "User-Delete"})
    public void deleteReview(Long id, Long authorId) {
        validateId(id, "Review id");
        validateId(authorId, "Author id");
        verifyCaller(authorId);
        Review review = findRequired(Review.class, id, "Review");
        ensureOwnerOrModerator(review, authorId);
        entityManager.remove(review);
    }

    private void ensureOwnerOrModerator(Review review, Long authorId) {
        Long ownerId = review.getAuthor() == null ? null : review.getAuthor().getId();
        if (ownerId != null && ownerId.equals(authorId)) return;
        if (isModerator()) return;
        throw new SecurityException("Only the review author or a moderator may change this review.");
    }

    private boolean isModerator() {
        return sessionContext != null && (sessionContext.isCallerInRole("Admin")
                || sessionContext.isCallerInRole("Admin-Delete")
                || sessionContext.isCallerInRole("Admin-Update")
                || sessionContext.isCallerInRole("Sub-Admin")
                || sessionContext.isCallerInRole("Sub-Admin-Delete")
                || sessionContext.isCallerInRole("Sub-Admin-Update"));
    }

    /** Never trust the id supplied by a REST facade when a container caller exists. */
    private void verifyCaller(Long authorId) {
        if (sessionContext == null || sessionContext.getCallerPrincipal() == null) return;
        String principal = sessionContext.getCallerPrincipal().getName();
        if (principal == null || principal.trim().isEmpty()) throw new SecurityException("Unauthenticated caller.");
        TypedQuery<UserEntity> query = entityManager.createQuery(
                "SELECT u FROM UserEntity u WHERE u.username = :principal OR u.keycloakId = :principal",
                UserEntity.class);
        query.setParameter("principal", principal);
        List<UserEntity> users = query.getResultList();
        if (users == null || users.isEmpty() || !authorId.equals(users.get(0).getId())) {
            throw new SecurityException("Authenticated principal does not match the author.");
        }
    }

    private boolean alreadyReviewed(Long productId, Long authorId) {
        TypedQuery<Review> query = entityManager.createQuery(
                "SELECT r FROM Review r WHERE r.product.id = :productId AND r.author.id = :authorId",
                Review.class);
        // A null query is tolerated only for lightweight non-container unit
        // doubles; a real JPA provider always returns a query here.
        if (query == null) return false;
        query.setParameter("productId", productId);
        query.setParameter("authorId", authorId);
        List<Review> reviews = query.getResultList();
        return reviews != null && !reviews.isEmpty();
    }

    private <T> T findRequired(Class<T> type, Long id, String label) {
        T value = entityManager.find(type, id);
        if (value == null) throw new EntityNotFoundException(label + " not found: " + id);
        return value;
    }

    private void validateId(Long id, String label) {
        if (id == null) throw new IllegalArgumentException(label + " is required.");
    }

    private void validateOptionalProductId(Long id) {
        if (id != null && id < 1) throw new IllegalArgumentException("Product id must be positive.");
    }

    private void validateOptionalRating(Integer rating) {
        if (rating != null) validateRating(rating);
    }

    private void validateRating(Integer rating) {
        if (rating == null || rating < MIN_RATING || rating > MAX_RATING)
            throw new IllegalArgumentException("Rating must be between 1 and 5.");
    }

    private void validateComment(String comment) {
        if (comment == null || comment.trim().isEmpty())
            throw new IllegalArgumentException("Comment is required.");
    }
}
