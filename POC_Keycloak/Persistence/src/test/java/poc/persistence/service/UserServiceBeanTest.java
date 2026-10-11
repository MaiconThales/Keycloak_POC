package poc.persistence.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.lang.reflect.Field;
import java.util.Collections;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class UserServiceBeanTest {
    private UserDirectoryLocal directory; private UserServiceBean bean;
    @BeforeEach void setUp() throws Exception {
        directory = mock(UserDirectoryLocal.class); bean = new UserServiceBean();
        Field f = UserServiceBean.class.getDeclaredField("directory"); f.setAccessible(true); f.set(bean, directory);
    }
    @Test void delegatesAllCrudWithTrimmedValues() {
        UserProfile p = new UserProfile("1", "alice", "a@x");
        when(directory.findAll()).thenReturn(Collections.singletonList(p)); when(directory.create("alice", "a@x")).thenReturn(p);
        when(directory.update("1", "alice", "a@x")).thenReturn(p);
        assertSame(p, bean.findAll().get(0)); assertSame(p, bean.create(" alice ", " a@x "));
        assertSame(p, bean.update(" 1 ", " alice ", " a@x ")); bean.delete(" 1 ");
        verify(directory).delete("1");
    }
    @Test void validatesEveryRequiredFieldBeforeDirectoryCall() {
        assertThrows(IllegalArgumentException.class, () -> bean.create(null, "x"));
        assertThrows(IllegalArgumentException.class, () -> bean.create("x", " "));
        assertThrows(IllegalArgumentException.class, () -> bean.update(" ", "x", "x"));
        assertThrows(IllegalArgumentException.class, () -> bean.update("x", null, "x"));
        assertThrows(IllegalArgumentException.class, () -> bean.update("x", "x", " "));
        assertThrows(IllegalArgumentException.class, () -> bean.delete(" "));
        verifyNoInteractions(directory);
    }
}
