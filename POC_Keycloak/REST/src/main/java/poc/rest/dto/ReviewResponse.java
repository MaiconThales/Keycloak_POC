package poc.rest.dto;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

import poc.persistence.entity.Review;

/** Stable API representation of a review; never accepts an identity from a client. */
@XmlRootElement
public class ReviewResponse {
    private Long id;
    @XmlElement(name = "product_id")
    private Long productId;
    @XmlElement(name = "user_keycloak_id")
    private String userKeycloakId;
    private String comment;

    public static ReviewResponse from(Review review) {
        ReviewResponse response = new ReviewResponse();
        response.id = review.getId();
        response.productId = review.getProduct() == null ? null : review.getProduct().getId();
        response.userKeycloakId = review.getUserKeycloakId();
        response.comment = review.getComment();
        return response;
    }
    public Long getId() { return id; }
    public Long getProductId() { return productId; }
    public String getUserKeycloakId() { return userKeycloakId; }
    public String getComment() { return comment; }
}
