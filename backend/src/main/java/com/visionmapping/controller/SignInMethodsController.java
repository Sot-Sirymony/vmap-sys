package com.visionmapping.controller;

import com.visionmapping.dto.response.OfferedSignInMethodsResponse;
import com.visionmapping.service.admin.SecurityAccessService;
import com.visionmapping.web.ApiPaths;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.GetMapping;

/** V24 SA-FR-03: which sign-in methods the sign-in pages offer. Public. Split from SecurityAccessController in V33. */
@RestController
@RequestMapping(value = ApiPaths.V1 + "/auth/sign-in-methods", produces = MediaType.APPLICATION_JSON_VALUE)
@Validated
@RequiredArgsConstructor
@Tag(name = "Sign-in methods", description = "The sign-in methods on offer, before anyone signs in")
public class SignInMethodsController {

    private final SecurityAccessService securityAccessService;

    @Operation(summary = "List the sign-in methods on offer")
    @GetMapping
    public ResponseEntity<OfferedSignInMethodsResponse> signInMethods() {
        return ResponseEntity.ok(securityAccessService.publicMethods());
    }
}
