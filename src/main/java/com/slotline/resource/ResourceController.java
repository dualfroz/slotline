package com.slotline.resource;

import com.slotline.resource.dto.ResourceRequest;
import com.slotline.resource.dto.ResourceResponse;
import com.slotline.security.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/resources")
@Tag(name = "Resources")
public class ResourceController {

    private final ResourceService resourceService;

    public ResourceController(ResourceService resourceService) {
        this.resourceService = resourceService;
    }

    @GetMapping
    @Operation(summary = "List bookable resources in the caller's organization")
    public List<ResourceResponse> list(@AuthenticationPrincipal AuthenticatedUser actor) {
        return resourceService.findAll(actor.organizationId());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a single resource")
    public ResourceResponse get(@AuthenticationPrincipal AuthenticatedUser actor, @PathVariable Long id) {
        return resourceService.findById(actor.organizationId(), id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a resource (ADMIN only)")
    public ResourceResponse create(@AuthenticationPrincipal AuthenticatedUser actor,
                                   @Valid @RequestBody ResourceRequest request) {
        return resourceService.create(actor.organizationId(), request);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update a resource (ADMIN only)")
    public ResourceResponse update(@AuthenticationPrincipal AuthenticatedUser actor, @PathVariable Long id,
                                   @Valid @RequestBody ResourceRequest request) {
        return resourceService.update(actor.organizationId(), id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete a resource (ADMIN only)")
    public void delete(@AuthenticationPrincipal AuthenticatedUser actor, @PathVariable Long id) {
        resourceService.delete(actor.organizationId(), id);
    }
}
