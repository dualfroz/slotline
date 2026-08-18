package com.slotline.availability;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDate;

@Entity
@Table(name = "blackout_date")
public class BlackoutDate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "resource_id", nullable = false)
    private Long resourceId;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Column(length = 256)
    private String reason;

    protected BlackoutDate() {
    }

    public BlackoutDate(Long resourceId, LocalDate startDate, LocalDate endDate, String reason) {
        this.resourceId = resourceId;
        this.startDate = startDate;
        this.endDate = endDate;
        this.reason = reason;
    }

    public Long getId() {
        return id;
    }

    public Long getResourceId() {
        return resourceId;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public String getReason() {
        return reason;
    }
}
