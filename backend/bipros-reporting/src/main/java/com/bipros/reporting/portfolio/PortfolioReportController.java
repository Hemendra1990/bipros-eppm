package com.bipros.reporting.portfolio;

import com.bipros.common.dto.ApiResponse;
import com.bipros.reporting.portfolio.dto.PortfolioEvmRow;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/v1/portfolio")
@PreAuthorize("hasAnyRole('ADMIN', 'PROJECT_MANAGER', 'VIEWER')")
@RequiredArgsConstructor
public class PortfolioReportController {

  private final PortfolioReportService portfolioReportService;

  @GetMapping("/evm-rollup")
  public ApiResponse<List<PortfolioEvmRow>> getEvmRollup() {
    return ApiResponse.ok(portfolioReportService.getEvmRollup());
  }
}
