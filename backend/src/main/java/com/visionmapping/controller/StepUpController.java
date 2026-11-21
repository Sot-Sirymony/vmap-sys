package com.visionmapping.controller;

import com.visionmapping.dto.request.StepUpRequest;
import com.visionmapping.dto.request.StepUpVerifyRequest;
import com.visionmapping.dto.response.StepUpChallengeResponse;
import com.visionmapping.dto.response.StepUpVerifyResponse;
import com.visionmapping.service.stepup.StepUpService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** V20 (BRD 20.0.0): "Verify it's you" codes for the signed-in person. */
@RestController
@RequestMapping("/api/account/step-up")
@RequiredArgsConstructor
@Validated
public class StepUpController {

    private final StepUpService service;

    /** SV-FR-10: send a code for one action (in the person's language). */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public StepUpChallengeResponse request(@Valid @RequestBody StepUpRequest request,
                                           @RequestParam(defaultValue = "en") @Pattern(regexp = "en|km") String language) {
        return service.request(request, language);
    }

    /** SV-FR-13: check the code the person typed. */
    @PostMapping("/{challengeId}/verify")
    public StepUpVerifyResponse verify(@PathVariable Long challengeId, @Valid @RequestBody StepUpVerifyRequest request) {
        return service.verify(challengeId, request.code());
    }
}
