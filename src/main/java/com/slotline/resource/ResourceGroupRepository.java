package com.slotline.resource;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ResourceGroupRepository extends JpaRepository<ResourceGroup, Long> {

    List<ResourceGroup> findByOrganizationIdOrderByCreatedAtAsc(Long organizationId);

    List<ResourceGroup> findByParentGroupId(Long parentGroupId);

    Optional<ResourceGroup> findByIdAndOrganizationId(Long id, Long organizationId);
}
