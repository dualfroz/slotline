package com.slotline.availability;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface BlackoutDateRepository extends JpaRepository<BlackoutDate, Long> {

    List<BlackoutDate> findByResourceIdOrderByStartDateAsc(Long resourceId);

    @Query("""
            select b from BlackoutDate b
            where b.resourceId = :resourceId
              and b.startDate <= :until
              and b.endDate >= :from
            """)
    List<BlackoutDate> findOverlapping(@Param("resourceId") Long resourceId,
                                       @Param("from") LocalDate from,
                                       @Param("until") LocalDate until);
}
