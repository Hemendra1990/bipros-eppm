package com.bipros.portfolio.presentation;

import com.bipros.common.dto.ApiResponse;
import com.bipros.portfolio.application.dto.CreatePortfolioRequest;
import com.bipros.portfolio.application.dto.PortfolioProjectResponse;
import com.bipros.portfolio.application.dto.PortfolioResponse;
import com.bipros.portfolio.application.service.PortfolioService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/v1/portfolios")
@RequiredArgsConstructor
public class PortfolioController {

  private final PortfolioService portfolioService;

  @PostMapping
  public ResponseEntity<ApiResponse<PortfolioResponse>> createPortfolio(
      @Valid @RequestBody CreatePortfolioRequest request) {
    PortfolioResponse response = portfolioService.createPortfolio(request);
    return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(response));
  }

  @GetMapping
  public ResponseEntity<ApiResponse<List<PortfolioResponse>>> listPortfolios() {
    List<PortfolioResponse> response = portfolioService.listPortfolios();
    return ResponseEntity.ok(ApiResponse.ok(response));
  }

  @GetMapping("/{id}")
  public ResponseEntity<ApiResponse<PortfolioResponse>> getPortfolio(@PathVariable UUID id) {
    PortfolioResponse response = portfolioService.getPortfolio(id);
    return ResponseEntity.ok(ApiResponse.ok(response));
  }

  @DeleteMapping("/{id}")
  public ResponseEntity<Void> deletePortfolio(@PathVariable UUID id) {
    portfolioService.deletePortfolio(id);
    return ResponseEntity.noContent().build();
  }

  @PostMapping("/{id}/projects")
  public ResponseEntity<ApiResponse<PortfolioProjectResponse>> addProject(
      @PathVariable UUID id, @RequestParam UUID projectId) {
    PortfolioProjectResponse response = portfolioService.addProject(id, projectId);
    return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(response));
  }

  @DeleteMapping("/{id}/projects/{projectId}")
  public ResponseEntity<Void> removeProject(@PathVariable UUID id, @PathVariable UUID projectId) {
    portfolioService.removeProject(id, projectId);
    return ResponseEntity.noContent().build();
  }

  @GetMapping("/{id}/projects")
  public ResponseEntity<ApiResponse<List<PortfolioProjectResponse>>> getPortfolioProjects(
      @PathVariable UUID id) {
    List<PortfolioProjectResponse> response = portfolioService.getPortfolioProjects(id);
    return ResponseEntity.ok(ApiResponse.ok(response));
  }
}
