package com.interview.user.domain;

public interface UserAccountRepository {

    UserAccount findById(String userId);

    void save(UserAccount account);

    boolean existsById(String userId);
}
