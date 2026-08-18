package com.slotline.availability;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.DayOfWeek;
import java.util.List;

public interface OperatingHoursRepository extends JpaRepository<OperatingHours, Long> {

    List<OperatingHours> findByResourceId(Long resourceId);

    List<OperatingHours> findByResourceIdAndDayOfWeek(Long resourceId, DayOfWeek dayOfWeek);

    void deleteByResourceId(Long resourceId);
}
