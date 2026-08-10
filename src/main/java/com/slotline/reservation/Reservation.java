package com.slotline.reservation;

import com.slotline.resource.Resource;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;

@Entity
@Table(name = "reservation")
public class Reservation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "resource_id", nullable = false)
    private Resource resource;

    @Column(name = "organization_id", nullable = false)
    private Long organizationId;

    @Column(name = "series_id", length = 36)
    private String seriesId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "start_time", nullable = false)
    private Instant startTime;

    @Column(name = "end_time", nullable = false)
    private Instant endTime;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private ReservationStatus status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Version
    private Long version;

    protected Reservation() {
    }

    public Reservation(Resource resource, Long userId, Instant startTime, Instant endTime, Instant createdAt) {
        this.resource = resource;
        this.organizationId = resource.getOrganizationId();
        this.userId = userId;
        this.startTime = startTime;
        this.endTime = endTime;
        this.status = ReservationStatus.ACTIVE;
        this.createdAt = createdAt;
    }

    public void cancel() {
        this.status = ReservationStatus.CANCELLED;
    }

    public void assignSeries(String seriesId) {
        this.seriesId = seriesId;
    }

    public Long getId() {
        return id;
    }

    public Resource getResource() {
        return resource;
    }

    public Long getOrganizationId() {
        return organizationId;
    }

    public String getSeriesId() {
        return seriesId;
    }

    public Long getUserId() {
        return userId;
    }

    public Instant getStartTime() {
        return startTime;
    }

    public Instant getEndTime() {
        return endTime;
    }

    public ReservationStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Long getVersion() {
        return version;
    }
}
