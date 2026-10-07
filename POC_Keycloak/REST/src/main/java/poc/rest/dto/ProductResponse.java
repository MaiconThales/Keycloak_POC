package poc.rest.dto;

import java.math.BigDecimal;

import javax.xml.bind.annotation.XmlRootElement;

import poc.persistence.entity.Product;

@XmlRootElement
public class ProductResponse {
    private Long id;
    private String name;
    private BigDecimal price;
    private String sku;

    public ProductResponse() {
    }

    public static ProductResponse from(Product product) {
        ProductResponse response = new ProductResponse();
        response.id = product.getId();
        response.name = product.getName();
        response.price = product.getPrice();
        response.sku = product.getSku();
        return response;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public void setPrice(BigDecimal price) {
        this.price = price;
    }

    public String getSku() {
        return sku;
    }

    public void setSku(String sku) {
        this.sku = sku;
    }
}
