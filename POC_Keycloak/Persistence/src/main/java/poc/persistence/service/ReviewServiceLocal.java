package poc.persistence.service;

import java.util.List;

import javax.ejb.Local;

import poc.persistence.entity.Review;

/** Local business contract for product reviews. */
@Local
public interface ReviewServiceLocal {
    List<Review> findReviews(Long productId, Integer rating);

    Review createReview(Long productId, Long authorId, Integer rating, String comment);

    Review updateReview(Long id, Long authorId, Integer rating, String comment);

    void deleteReview(Long id, Long authorId);
}
