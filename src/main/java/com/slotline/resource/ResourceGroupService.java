package com.slotline.resource;

import com.slotline.common.NotFoundException;
import com.slotline.resource.dto.ResourceGroupRequest;
import com.slotline.resource.dto.ResourceGroupResponse;
import com.slotline.resource.dto.ResourceResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
public class ResourceGroupService {

    private final ResourceGroupRepository groups;
    private final ResourceService resources;
    private final ResourceRepository resourceRepository;

    public ResourceGroupService(ResourceGroupRepository groups, ResourceService resources,
                                ResourceRepository resourceRepository) {
        this.groups = groups;
        this.resources = resources;
        this.resourceRepository = resourceRepository;
    }

    @Transactional
    public ResourceGroupResponse create(Long organizationId, ResourceGroupRequest request) {
        if (request.parentGroupId() != null) {
            require(organizationId, request.parentGroupId());
        }
        ResourceGroup group = new ResourceGroup(organizationId, request.parentGroupId(), request.name(), Instant.now());
        return ResourceGroupResponse.from(groups.save(group));
    }

    @Transactional(readOnly = true)
    public List<ResourceGroupResponse> findAll(Long organizationId) {
        return groups.findByOrganizationIdOrderByCreatedAtAsc(organizationId).stream()
                .map(ResourceGroupResponse::from)
                .toList();
    }

    @Transactional
    public ResourceResponse assignResource(Long organizationId, Long groupId, Long resourceId) {
        ResourceGroup group = require(organizationId, groupId);
        Resource resource = resources.require(organizationId, resourceId);
        resource.setGroupId(group.getId());
        return ResourceResponse.from(resource);
    }

    @Transactional(readOnly = true)
    public List<ResourceResponse> resourcesInGroup(Long organizationId, Long groupId) {
        ResourceGroup group = require(organizationId, groupId);
        return resourceRepository.findByGroupIdOrderByCreatedAtDesc(group.getId()).stream()
                .map(ResourceResponse::from)
                .toList();
    }

    private ResourceGroup require(Long organizationId, Long groupId) {
        return groups.findByIdAndOrganizationId(groupId, organizationId)
                .orElseThrow(() -> new NotFoundException("Resource group " + groupId + " not found"));
    }
}
