package com.slotline.reservation;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface ReservationRepository extends JpaRepository<Reservation, Long> {

    List<Reservation> findByUserIdOrderByStartTimeDesc(Long userId);

    List<Reservation> findByResourceIdAndStatusOrderByStartTimeAsc(Long resourceId, ReservationStatus status);

    List<Reservation> findByResourceIdOrderByStartTimeAsc(Long resourceId);

    List<Reservation> findByUserIdAndStatusOrderByStartTimeAsc(Long userId, ReservationStatus status);

    List<Reservation> findBySeriesIdOrderByStartTimeAsc(String seriesId);

    @Query("""
            select r from Reservation r
            where r.resource.id = :resourceId
              and r.status = com.slotline.reservation.ReservationStatus.ACTIVE
              and r.startTime < :end
              and r.endTime > :start
            """)
    List<Reservation> findActiveOverlaps(@Param("resourceId") Long resourceId,
                                         @Param("start") Instant start,
                                         @Param("end") Instant end);

    @Query("""
            select r from Reservation r
            where r.resource.id = :resourceId
              and r.status = com.slotline.reservation.ReservationStatus.ACTIVE
              and r.startTime < :until
              and r.endTime > :from
            """)
    List<Reservation> findActiveWithinWindow(@Param("resourceId") Long resourceId,
                                             @Param("from") Instant from,
                                             @Param("until") Instant until);

    @Query("""
            select r from Reservation r
            where r.resource.id = :resourceId
              and r.status = com.slotline.reservation.ReservationStatus.ACTIVE
              and r.startTime < :until
              and r.endTime > :from
            order by r.startTime asc
            """)
    List<Reservation> findActiveForResourceBetween(@Param("resourceId") Long resourceId,
                                                    @Param("from") Instant from,
                                                    @Param("until") Instant until);
}
