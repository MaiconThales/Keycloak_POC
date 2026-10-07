package poc.persistence.entity;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ProductTest {
    @Test
    void supportsJpaNoArgumentConstructionAndAccessors() {
        Product product = new Product();
        product.setId(101L);
        product.setName("Notebook");
        product.setPrice(new BigDecimal("4500.01"));
        product.setSku("NTB");

        assertEquals(Long.valueOf(101), product.getId());
        assertEquals("Notebook", product.getName());
        assertEquals(new BigDecimal("4500.01"), product.getPrice());
        assertEquals("NTB", product.getSku());
    }

    @Test
    void constructsNewProductWithoutAssigningAnIdentifier() {
        Product product = new Product("Mouse", new BigDecimal("50.00"), "MSE");

        assertNull(product.getId());
        assertEquals("Mouse", product.getName());
        assertEquals(new BigDecimal("50.00"), product.getPrice());
        assertEquals("MSE", product.getSku());
    }
}
