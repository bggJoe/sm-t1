package com.example.backend.registration.web;

import com.example.backend.registration.application.RegistrationApplicationService;
import com.example.backend.registration.application.SubmitApplicationCommand;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * POST /registrations — PUBLIC endpoint, no auth (see SLICE-001 scope, INV-001~004 enforced by service).
 */
@RestController
@RequestMapping("/registrations")
public class RegistrationController {

    private final RegistrationApplicationService service;

    public RegistrationController(RegistrationApplicationService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<Void> submit(@RequestBody RegistrationRequest request) {
        service.submitApplication(new SubmitApplicationCommand(request.email(), request.name()));
        return ResponseEntity.status(201).build();
    }
}
