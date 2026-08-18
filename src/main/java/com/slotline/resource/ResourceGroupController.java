package com.slotline.resource;

import com.slotline.resource.dto.ResourceGroupRequest;
import com.slotline.resource.dto.ResourceGroupResponse;
import com.slotline.resource.dto.ResourceResponse;
import com.slotline.security.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/resource-groups")
@Tag(name = "Resource groups")
public class ResourceGroupController {

    private final ResourceGroupService groupService;

    public ResourceGroupController(ResourceGroupService groupService) {
        this.groupService = groupService;
    }

    @GetMapping
    @Operation(summary = "List resource groups in the caller's organization")
    public List<ResourceGroupResponse> list(@AuthenticationPrincipal AuthenticatedUser actor) {
        return groupService.findAll(actor.organizationId());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a resource group, optionally nested (ADMIN only)")
    public ResourceGroupResponse create(@AuthenticationPrincipal AuthenticatedUser actor,
                                        @Valid @RequestBody ResourceGroupRequest request) {
        return groupService.create(actor.organizationId(), request);
    }

    @PostMapping("/{groupId}/resources/{resourceId}")
    @Operation(summary = "Assign a resource to a group (ADMIN only)")
    public ResourceResponse assign(@AuthenticationPrincipal AuthenticatedUser actor, @PathVariable Long groupId,
                                   @PathVariable Long resourceId) {
        return groupService.assignResource(actor.organizationId(), groupId, resourceId);
    }

    @GetMapping("/{groupId}/resources")
    @Operation(summary = "List resources directly assigned to a group")
    public List<ResourceResponse> resources(@AuthenticationPrincipal AuthenticatedUser actor,
                                            @PathVariable Long groupId) {
        return groupService.resourcesInGroup(actor.organizationId(), groupId);
    }
}
