package com.hansungteam.ersync.organization.infrastructure;

import com.hansungteam.ersync.organization.domain.OrganizationType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * 병원 또는 구급대 조직의 영속 모델입니다.
 */
@Entity
@Table(name = "organizations")
public class OrganizationEntity {

    @Id
    @Column(nullable = false, length = 36)
    private String id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private OrganizationType type;

    @Column(nullable = false, length = 120, unique = true)
    private String name;

    @Column(nullable = false)
    private boolean active;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    protected OrganizationEntity() {
    }

    public OrganizationEntity(OrganizationType type, String name, Instant now) {
        this.id = UUID.randomUUID().toString();
        this.type = type;
        this.name = name;
        this.active = true;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public String id() {
        return id;
    }

    public OrganizationType type() {
        return type;
    }

    public String name() {
        return name;
    }

    public boolean active() {
        return active;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant updatedAt() {
        return updatedAt;
    }
}
