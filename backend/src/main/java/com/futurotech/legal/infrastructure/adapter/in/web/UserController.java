package com.futurotech.legal.infrastructure.adapter.in.web;

import com.futurotech.legal.application.service.UserService;
import com.futurotech.legal.domain.model.Role;
import com.futurotech.legal.domain.model.User;
import com.futurotech.legal.infrastructure.adapter.in.web.dto.UserDtos.CreateUserRequest;
import com.futurotech.legal.infrastructure.adapter.in.web.dto.UserDtos.UpdateUserRequest;
import com.futurotech.legal.infrastructure.adapter.in.web.dto.UserDtos.UserResponse;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users")
@PreAuthorize("hasAuthority('PERM_manage_users')")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping
    public List<UserResponse> list() {
        return userService.findAll().stream().map(UserController::toResponse).toList();
    }

    @GetMapping("/{userId}")
    public ResponseEntity<UserResponse> get(@PathVariable String userId) {
        return userService.findById(userId)
                .map(UserController::toResponse)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponse create(@Valid @RequestBody CreateUserRequest req) {
        User created = userService.create(req.username(), req.fullName(), req.email(),
                Role.fromValue(req.role()), req.password(), req.permissions());
        return toResponse(created);
    }

    @PutMapping("/{userId}")
    public UserResponse update(@PathVariable String userId, @Valid @RequestBody UpdateUserRequest req) {
        User updated = userService.update(userId, req.fullName(), req.email(),
                Role.fromValue(req.role()), req.active(), req.password(), req.permissions());
        return toResponse(updated);
    }

    @DeleteMapping("/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable String userId) {
        userService.delete(userId);
    }

    private static UserResponse toResponse(User u) {
        List<String> perms = u.customPermissions() != null
                ? u.customPermissions()
                : u.role().defaultPermissions().stream().map(p -> p.value()).toList();
        return new UserResponse(u.userId(), u.username(), u.fullName(), u.email(),
                u.role().value(), u.active(), perms);
    }
}
