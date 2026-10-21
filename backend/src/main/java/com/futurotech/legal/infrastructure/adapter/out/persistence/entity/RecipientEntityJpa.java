package com.futurotech.legal.infrastructure.adapter.out.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "entities")
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

    protected RecipientEntityJpa() {
    }

    public RecipientEntityJpa(String entityId, String name, String nit, String address, String city,
                              String phone, String email, String legalRep, String entityType, String notes) {
        this.entityId = entityId;
        this.name = name;
        this.nit = nit;
        this.address = address;
        this.city = city;
        this.phone = phone;
        this.email = email;
        this.legalRep = legalRep;
        this.entityType = entityType;
        this.notes = notes;
    }

    public String getEntityId() {
        return entityId;
    }

    public String getName() {
        return name;
    }

    public String getNit() {
        return nit;
    }

    public String getAddress() {
        return address;
    }

    public String getCity() {
        return city;
    }

    public String getPhone() {
        return phone;
    }

    public String getEmail() {
        return email;
    }

    public String getLegalRep() {
        return legalRep;
    }

    public String getEntityType() {
        return entityType;
    }

    public String getNotes() {
        return notes;
    }
}
