package com.bipros.admin.presentation.controller;

import com.bipros.admin.application.dto.OrganisationDto;
import com.bipros.admin.application.service.OrganisationService;
import com.bipros.admin.domain.model.OrganisationType;
import com.bipros.common.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/v1/organisations")
@PreAuthorize("isAuthenticated()")
@RequiredArgsConstructor
public class OrganisationController {

    private final OrganisationService organisationService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<OrganisationDto>>> list(
            @RequestParam(value = "type", required = false) OrganisationType type) {
        List<OrganisationDto> result = (type != null)
                ? organisationService.listByType(type)
                : organisationService.listAll();
        return ResponseEntity.ok(ApiResponse.ok(result));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<OrganisationDto>> get(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(organisationService.get(id)));
    }
}
