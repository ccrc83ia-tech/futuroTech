package com.futurotech.legal.infrastructure.adapter.out.persistence;

import com.futurotech.legal.domain.model.Role;
import com.futurotech.legal.domain.model.User;
import com.futurotech.legal.domain.port.out.UserRepositoryPort;
import com.futurotech.legal.infrastructure.adapter.out.persistence.entity.UserEntity;
import com.futurotech.legal.infrastructure.adapter.out.persistence.repository.UserJpaRepository;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Adaptador de persistencia de usuarios: mapea UserEntity <-> User. */
@Component
public class UserRepositoryAdapter implements UserRepositoryPort {

    private final UserJpaRepository jpa;

    public UserRepositoryAdapter(UserJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<User> findByUsername(String username) {
        return jpa.findByUsername(username).map(this::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<User> findById(String userId) {
        return jpa.findById(userId).map(this::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public List<User> findAll() {
        return jpa.findAll().stream().map(this::toDomain).toList();
    }

    @Override
    @Transactional
    public void save(User u) {
        jpa.save(new UserEntity(
                u.userId(), u.username(), u.fullName(), u.email(),
                u.role().value(), u.active(), u.passwordHash(), u.customPermissions()));
    }

    @Override
    @Transactional
    public void deleteById(String userId) {
        jpa.deleteById(userId);
    }

    private User toDomain(UserEntity e) {
        return new User(
                e.getUserId(), e.getUsername(), e.getFullName(), e.getEmail(),
                Role.fromValue(e.getRole()), e.isActive(), e.getPasswordHash(), e.getPermissions());
    }
}
