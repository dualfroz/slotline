package com.slotline.reservation;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface WaitlistRepository extends JpaRepository<WaitlistEntry, Long> {

    List<WaitlistEntry> findByUserIdOrderByCreatedAtDesc(Long userId);

    @Query("""
            select w from WaitlistEntry w
            where w.resourceId = :resourceId
              and w.status = com.slotline.reservation.WaitlistStatus.WAITING
              and w.startTime < :end
              and w.endTime > :start
            order by w.createdAt asc
            """)
    List<WaitlistEntry> findWaitingOverlaps(@Param("resourceId") Long resourceId,
                                            @Param("start") Instant start,
                                            @Param("end") Instant end);
}
