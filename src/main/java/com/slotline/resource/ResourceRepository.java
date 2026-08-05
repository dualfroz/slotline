package com.slotline.resource;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ResourceRepository extends JpaRepository<Resource, Long> {

    List<Resource> findAllByOrderByCreatedAtDesc();

    List<Resource> findByOrganizationIdOrderByCreatedAtDesc(Long organizationId);

    List<Resource> findByGroupIdOrderByCreatedAtDesc(Long groupId);

    Optional<Resource> findByIdAndOrganizationId(Long id, Long organizationId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from Resource r where r.id = :id")
    Optional<Resource> findByIdForBooking(@Param("id") Long id);
}
