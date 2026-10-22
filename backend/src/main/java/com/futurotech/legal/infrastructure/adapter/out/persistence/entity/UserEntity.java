package com.futurotech.legal.infrastructure.adapter.out.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
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
}
