package com.slotline.organization;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "organization")
public class Organization {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 128)
    private String name;

    @Column(nullable = false, unique = true, length = 64)
    private String slug;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected Organization() {
    }

    public Organization(String name, String slug, Instant createdAt) {
        this.name = name;
        this.slug = slug;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getSlug() {
        return slug;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
