package com.futurotech.legal.infrastructure.adapter.out.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "entities")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class RecipientEntityJpa {

    @Id
    @Column(name = "entity_id")
    private String entityId;

    private String name;
    private String nit;
    private String address;
    private String city;
    private String phone;
    private String email;

    @Column(name = "legal_rep")
    private String legalRep;

    @Column(name = "entity_type")
    private String entityType;

    private String notes;
}
