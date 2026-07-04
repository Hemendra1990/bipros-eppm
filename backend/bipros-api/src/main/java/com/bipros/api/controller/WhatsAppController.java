package com.bipros.api.controller;

import com.bipros.api.service.WhatsAppService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/public/whatsapp")
@RequiredArgsConstructor
public class WhatsAppController {

    private final WhatsAppService service;

    @GetMapping("/gis")
    public ResponseEntity<Void> redirectToGis(
            @RequestParam("projectId") String projectId,
            @RequestParam("token") String token) {
        return service.redirectToGis(projectId, token);
    }
}
