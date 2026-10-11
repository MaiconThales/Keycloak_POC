package poc.rest.dto;

import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

/** Client supplied fields for a review.  The authenticated user is not a payload field. */
@XmlRootElement
public class ReviewInput {
    @XmlElement(name = "product_id")
    private Long productId;

    @NotNull
    @Size(min = 1, max = 10000)
    private String comment;

    public Long getProductId() { return productId; }
    public void setProductId(Long productId) { this.productId = productId; }
    public String getComment() { return comment; }
    public void setComment(String comment) { this.comment = comment; }
}
