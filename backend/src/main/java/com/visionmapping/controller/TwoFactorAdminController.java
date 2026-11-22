package com.visionmapping.controller;

import com.visionmapping.dto.request.TwoFactorResetRequest;
import com.visionmapping.dto.response.TwoFactorReportRow;
import com.visionmapping.service.stepup.StepUpService;
import com.visionmapping.service.twofactor.TwoFactorAdminService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.lang.Nullable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** V21 (TF-FR-28, TF-FR-36): admin only, by path rule and in the service. */
@RestController
@RequiredArgsConstructor
public class TwoFactorAdminController {

    private final TwoFactorAdminService twoFactorAdminService;

    @GetMapping("/api/admin/two-factor")
    public List<TwoFactorReportRow> report() {
        return twoFactorAdminService.report();
    }

    @PostMapping("/api/admin/users/{id}/two-factor/reset")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void reset(@PathVariable Long id, @Valid @RequestBody TwoFactorResetRequest request,
                      @RequestHeader(value = StepUpService.HEADER, required = false) @Nullable String stepUp) {
        twoFactorAdminService.reset(id, request.reason(), stepUp);
    }
}
