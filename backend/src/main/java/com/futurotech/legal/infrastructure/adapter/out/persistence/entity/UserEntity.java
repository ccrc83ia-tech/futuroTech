package com.futurotech.legal.infrastructure.adapter.out.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.List;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "users")
public class UserEntity {

    @Id
    @Column(name = "user_id")
    private String userId;

    private String username;

    @Column(name = "full_name")
    private String fullName;

    private String email;

    private String role;

    private boolean active;

    @Column(name = "password_hash")
    private String passwordHash;

    @JdbcTypeCode(SqlTypes.JSON)
    private List<String> permissions;

    protected UserEntity() {
    }

    public UserEntity(String userId, String username, String fullName, String email,
                      String role, boolean active, String passwordHash, List<String> permissions) {
        this.userId = userId;
        this.username = username;
        this.fullName = fullName;
        this.email = email;
        this.role = role;
        this.active = active;
        this.passwordHash = passwordHash;
        this.permissions = permissions;
    }

    public String getUserId() {
        return userId;
    }

    public String getUsername() {
        return username;
    }

    public String getFullName() {
        return fullName;
    }

    public String getEmail() {
        return email;
    }

    public String getRole() {
        return role;
    }

    public boolean isActive() {
        return active;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public List<String> getPermissions() {
        return permissions;
    }
}
