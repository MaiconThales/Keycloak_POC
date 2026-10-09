package poc.persistence.service;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import javax.annotation.security.RolesAllowed;
import javax.ejb.Stateless;
import javax.ejb.TransactionAttribute;
import javax.ejb.TransactionAttributeType;
import javax.ejb.TransactionManagement;
import javax.ejb.TransactionManagementType;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.persistence.PersistenceException;
import javax.persistence.TypedQuery;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import poc.persistence.entity.Product;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ProductServiceBeanTest {
    private EntityManager entityManager;
    private ProductServiceBean bean;

    @BeforeEach
    void setUp() throws ReflectiveOperationException {
        entityManager = mock(EntityManager.class);
        bean = new ProductServiceBean();
        Field field = ProductServiceBean.class.getDeclaredField("entityManager");
        field.setAccessible(true);
        field.set(bean, entityManager);
    }

    @Test
    void listsProductsUsingTypedOrderedQuery() {
        TypedQuery<Product> query = mockProductQuery();
        List<Product> products = Arrays.asList(
                new Product("Notebook", new BigDecimal("4500.00"), "NTB"),
                new Product("Mouse", new BigDecimal("50.00"), "MSE"));
        when(query.getResultList()).thenReturn(products);

        assertSame(products, bean.findAll());
        verify(entityManager).createQuery("SELECT p FROM Product p ORDER BY p.id", Product.class);
        verify(query).getResultList();
        verifyNoMoreInteractions(entityManager);
    }

    @Test
    void returnsEmptyListWhenNoProductsExist() {
        TypedQuery<Product> query = mockProductQuery();
        when(query.getResultList()).thenReturn(Collections.emptyList());

        assertTrue(bean.findAll().isEmpty());
    }

    @Test
    void propagatesQueryFailure() {
        PersistenceException failure = new PersistenceException("Database unavailable");
        when(entityManager.createQuery(anyString(), eq(Product.class))).thenThrow(failure);

        assertSame(failure, assertThrows(PersistenceException.class, bean::findAll));
    }

    @Test
    void createsAndReturnsThePersistedProductWithoutLosingPricePrecision() {
        BigDecimal price = new BigDecimal("4500.01");

        Product product = bean.create("Notebook", price, "NTB");

        assertEquals("Notebook", product.getName());
        assertEquals(price, product.getPrice());
        assertEquals("NTB", product.getSku());
        verify(entityManager).persist(same(product));
        verifyNoMoreInteractions(entityManager);
    }

    @Test
    void returnsIdentifierAssignedDuringPersistence() {
        doAnswer(invocation -> {
            Product product = invocation.getArgument(0);
            product.setId(101L);
            return null;
        }).when(entityManager).persist(any(Product.class));

        assertEquals(Long.valueOf(101), bean.create("Notebook", new BigDecimal("4500.00"), "NTB").getId());
    }

    @Test
    void propagatesPersistenceFailureForContainerRollback() {
        PersistenceException failure = new PersistenceException("Duplicate SKU");
        doThrow(failure).when(entityManager).persist(any(Product.class));

        assertSame(failure, assertThrows(PersistenceException.class,
                () -> bean.create("Notebook", new BigDecimal("4500.00"), "NTB")));
    }

    @Test
    void declaresContainerTransactionPersistenceAndActionAuthorization() throws Exception {
        assertNotNull(ProductServiceBean.class.getAnnotation(Stateless.class));
        assertEquals(TransactionAttributeType.REQUIRED,
                ProductServiceBean.class.getAnnotation(TransactionAttribute.class).value());
        TransactionManagement management = ProductServiceBean.class.getAnnotation(TransactionManagement.class);
        assertTrue(management == null || management.value() == TransactionManagementType.CONTAINER);
        assertEquals("MinhaAppPU", ProductServiceBean.class.getDeclaredField("entityManager")
                .getAnnotation(PersistenceContext.class).unitName());
        assertArrayEquals(new String[]{"Admin", "Sub-Admin", "Admin-Write", "Sub-Admin-Write"}, ProductServiceBean.class
                .getMethod("create", String.class, BigDecimal.class, String.class)
                .getAnnotation(RolesAllowed.class).value());
        assertArrayEquals(new String[]{"Admin", "Sub-Admin", "User", "Admin-Read", "Sub-Admin-Read", "User-Read"},
                ProductServiceBean.class.getMethod("findAll").getAnnotation(RolesAllowed.class).value());
    }

    @SuppressWarnings("unchecked")
    private TypedQuery<Product> mockProductQuery() {
        TypedQuery<Product> query = mock(TypedQuery.class);
        when(entityManager.createQuery("SELECT p FROM Product p ORDER BY p.id", Product.class))
                .thenReturn(query);
        return query;
    }
}
