package com.futurotech.legal.infrastructure.adapter.in.web;

import com.futurotech.legal.application.service.EntityService;
import com.futurotech.legal.domain.model.RecipientEntity;
import com.futurotech.legal.infrastructure.adapter.in.web.dto.EntityDtos.EntityRequest;
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
@RequestMapping("/api/v1/entities")
public class EntityController {

    private final EntityService entityService;

    public EntityController(EntityService entityService) {
        this.entityService = entityService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('PERM_view_entities')")
    public List<RecipientEntity> list() {
        return entityService.findAll();
    }

    @GetMapping("/{entityId}")
    @PreAuthorize("hasAuthority('PERM_view_entities')")
    public ResponseEntity<RecipientEntity> get(@PathVariable String entityId) {
        return entityService.findById(entityId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    @PreAuthorize("hasAuthority('PERM_manage_entities')")
    @ResponseStatus(HttpStatus.CREATED)
    public RecipientEntity create(@Valid @RequestBody EntityRequest req) {
        RecipientEntity e = toDomain(req);
        entityService.save(e);
        return e;
    }

    @PutMapping("/{entityId}")
    @PreAuthorize("hasAuthority('PERM_manage_entities')")
    public RecipientEntity update(@PathVariable String entityId, @Valid @RequestBody EntityRequest req) {
        RecipientEntity e = toDomain(req);
        entityService.save(e);
        return e;
    }

    @DeleteMapping("/{entityId}")
    @PreAuthorize("hasAuthority('PERM_manage_entities')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable String entityId) {
        entityService.delete(entityId);
    }

    private static RecipientEntity toDomain(EntityRequest r) {
        return new RecipientEntity(r.entityId(), r.name(), r.nit(), r.address(), r.city(),
                r.phone(), r.email(), r.legalRep(), r.entityType(), r.notes());
    }
}
