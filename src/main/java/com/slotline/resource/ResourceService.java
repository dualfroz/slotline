package com.slotline.resource;

import com.slotline.common.NotFoundException;
import com.slotline.resource.dto.ResourceRequest;
import com.slotline.resource.dto.ResourceResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
public class ResourceService {

    private final ResourceRepository resources;

    public ResourceService(ResourceRepository resources) {
        this.resources = resources;
    }

    @Transactional
    public ResourceResponse create(Long organizationId, ResourceRequest request) {
        Resource resource = new Resource(organizationId, request.name(), request.description(), Instant.now());
        applyPolicy(resource, request);
        return ResourceResponse.from(resources.save(resource));
    }

    @Transactional(readOnly = true)
    public List<ResourceResponse> findAll(Long organizationId) {
        return resources.findByOrganizationIdOrderByCreatedAtDesc(organizationId).stream()
                .map(ResourceResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public ResourceResponse findById(Long organizationId, Long id) {
        return ResourceResponse.from(require(organizationId, id));
    }

    @Transactional
    public ResourceResponse update(Long organizationId, Long id, ResourceRequest request) {
        Resource resource = require(organizationId, id);
        resource.setName(request.name());
        resource.setDescription(request.description());
        applyPolicy(resource, request);
        return ResourceResponse.from(resource);
    }

    private void applyPolicy(Resource resource, ResourceRequest request) {
        if (request.zoneId() != null && !request.zoneId().isBlank()) {
            resource.setZoneId(request.zoneId());
        }
        if (request.bufferMinutes() != null) {
            resource.setBufferMinutes(request.bufferMinutes());
        }
    }

    @Transactional
    public void delete(Long organizationId, Long id) {
        resources.delete(require(organizationId, id));
    }

    @Transactional(readOnly = true)
    public Resource require(Long organizationId, Long id) {
        return resources.findByIdAndOrganizationId(id, organizationId)
                .orElseThrow(() -> new NotFoundException("Resource " + id + " not found"));
    }
}
