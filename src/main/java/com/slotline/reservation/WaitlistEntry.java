package com.slotline.reservation;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "waitlist_entry")
public class WaitlistEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "resource_id", nullable = false)
    private Long resourceId;

    @Column(name = "organization_id", nullable = false)
    private Long organizationId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "start_time", nullable = false)
    private Instant startTime;

    @Column(name = "end_time", nullable = false)
    private Instant endTime;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private WaitlistStatus status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "promoted_reservation_id")
    private Long promotedReservationId;

    protected WaitlistEntry() {
    }

    public WaitlistEntry(Long resourceId, Long organizationId, Long userId, Instant startTime, Instant endTime,
                         Instant createdAt) {
        this.resourceId = resourceId;
        this.organizationId = organizationId;
        this.userId = userId;
        this.startTime = startTime;
        this.endTime = endTime;
        this.status = WaitlistStatus.WAITING;
        this.createdAt = createdAt;
    }

    public void promote(Long reservationId) {
        this.status = WaitlistStatus.PROMOTED;
        this.promotedReservationId = reservationId;
    }

    public Long getId() {
        return id;
    }

    public Long getResourceId() {
        return resourceId;
    }

    public Long getOrganizationId() {
        return organizationId;
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

    public WaitlistStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Long getPromotedReservationId() {
        return promotedReservationId;
    }
}
