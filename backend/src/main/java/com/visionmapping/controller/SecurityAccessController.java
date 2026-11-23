package com.visionmapping.controller;

import com.visionmapping.dto.request.SecurityAccessUpdateRequest;
import com.visionmapping.dto.response.SecurityAccessResponse;
import com.visionmapping.dto.response.OfferedSignInMethodsResponse;
import com.visionmapping.service.admin.SecurityAccessService;
import com.visionmapping.service.stepup.StepUpService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.lang.Nullable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** V24 (BRD 24.0.0): sign-in method settings. The admin path is checked by path rule and in the service. */
@RestController
@RequiredArgsConstructor
public class SecurityAccessController {

    private final SecurityAccessService securityAccessService;

    /** SA-FR-03: public; the sign-in pages need it before anyone signs in. */
    @GetMapping("/api/auth/sign-in-methods")
    public OfferedSignInMethodsResponse signInMethods() {
        return securityAccessService.publicMethods();
    }

    /** SA-FR-02. */
    @GetMapping("/api/admin/security-access")
    public SecurityAccessResponse view() {
        return securityAccessService.view();
    }

    /** SA-FR-05: Admin only; a reason and a fresh "Verify it's you" code. */
    @PutMapping("/api/admin/security-access")
    public SecurityAccessResponse update(@Valid @RequestBody SecurityAccessUpdateRequest request,
                                         @RequestHeader(value = StepUpService.HEADER, required = false) @Nullable String stepUp) {
        return securityAccessService.update(request, stepUp);
    }
}
