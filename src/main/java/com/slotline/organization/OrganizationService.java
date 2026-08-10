package com.slotline.organization;

import com.slotline.common.NotFoundException;
import com.slotline.common.SlugTakenException;
import com.slotline.organization.dto.OrganizationRequest;
import com.slotline.organization.dto.OrganizationResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
public class OrganizationService {

    public static final String defaultSlug = "default";

    private final OrganizationRepository organizations;

    public OrganizationService(OrganizationRepository organizations) {
        this.organizations = organizations;
    }

    @Transactional
    public OrganizationResponse create(OrganizationRequest request) {
        if (organizations.existsBySlug(request.slug())) {
            throw new SlugTakenException(request.slug());
        }
        Organization organization = new Organization(request.name(), request.slug(), Instant.now());
        return OrganizationResponse.from(organizations.save(organization));
    }

    @Transactional(readOnly = true)
    public List<OrganizationResponse> findAll() {
        return organizations.findAllByOrderByCreatedAtAsc().stream()
                .map(OrganizationResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public Organization require(Long id) {
        return organizations.findById(id)
                .orElseThrow(() -> new NotFoundException("Organization " + id + " not found"));
    }

    @Transactional(readOnly = true)
    public Organization requireBySlug(String slug) {
        return organizations.findBySlug(slug)
                .orElseThrow(() -> new NotFoundException("Organization '" + slug + "' not found"));
    }
}
