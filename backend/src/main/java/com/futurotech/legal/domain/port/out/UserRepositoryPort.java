package com.futurotech.legal.domain.port.out;

import com.futurotech.legal.domain.model.User;

import java.util.List;
import java.util.Optional;

/** Persistencia de usuarios (RBAC). */
public interface UserRepositoryPort {

    Optional<User> findByUsername(String username);

    Optional<User> findById(String userId);

    List<User> findAll();

    void save(User user);

    void deleteById(String userId);
}
