package poc.rest.dto;

import java.util.ArrayList;
import java.util.List;
import poc.persistence.service.UserPage;

public class UserPageResponse {
    private int page;
    private int size;
    private long total;
    private List<UserResponse> users = new ArrayList<UserResponse>();
    public static UserPageResponse from(UserPage source) {
        UserPageResponse result = new UserPageResponse();
        result.page = source.getPage(); result.size = source.getSize(); result.total = source.getTotal();
        for (poc.persistence.entity.UserEntity user : source.getUsers()) result.users.add(UserResponse.from(user));
        return result;
    }
    public int getPage() { return page; }
    public int getSize() { return size; }
    public long getTotal() { return total; }
    public List<UserResponse> getUsers() { return users; }
}
