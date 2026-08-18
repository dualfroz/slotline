package com.slotline.resource;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "resource_group")
public class ResourceGroup {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "organization_id", nullable = false)
    private Long organizationId;

    @Column(name = "parent_group_id")
    private Long parentGroupId;

    @Column(nullable = false, length = 128)
    private String name;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected ResourceGroup() {
    }

    public ResourceGroup(Long organizationId, Long parentGroupId, String name, Instant createdAt) {
        this.organizationId = organizationId;
        this.parentGroupId = parentGroupId;
        this.name = name;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public Long getOrganizationId() {
        return organizationId;
    }

    public Long getParentGroupId() {
        return parentGroupId;
    }

    public String getName() {
        return name;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
