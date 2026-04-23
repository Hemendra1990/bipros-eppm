package com.bipros.resource.presentation.controller;

import com.bipros.common.dto.ApiResponse;
import com.bipros.resource.application.dto.UnitRateMasterRow;
import com.bipros.resource.application.service.UnitRateMasterService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/v1/unit-rate-master")
@RequiredArgsConstructor
@Slf4j
public class UnitRateMasterController {

  private final UnitRateMasterService service;

  @GetMapping
  public ResponseEntity<ApiResponse<List<UnitRateMasterRow>>> list(
      @RequestParam(required = false) String category) {
    log.info("GET /v1/unit-rate-master category={}", category);
    return ResponseEntity.ok(ApiResponse.ok(service.list(category)));
  }
}
