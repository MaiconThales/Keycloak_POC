package poc.persistence.service;

import java.util.Collections;
import java.util.List;

import poc.persistence.entity.UserEntity;

/** Immutable page contract shared by the user EJB and its future REST facade. */
public final class UserPage {
    private final int page;
    private final int size;
    private final long total;
    private final List<UserEntity> users;

    public UserPage(int page, int size, long total, List<UserEntity> users) {
        this.page = page;
        this.size = size;
        this.total = total;
        this.users = users == null ? Collections.<UserEntity>emptyList() : users;
    }
    public int getPage() { return page; }
    public int getSize() { return size; }
    public long getTotal() { return total; }
    public List<UserEntity> getUsers() { return users; }
}
