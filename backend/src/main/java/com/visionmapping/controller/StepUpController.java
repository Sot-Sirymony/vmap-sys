package com.visionmapping.controller;

import com.visionmapping.dto.request.StepUpRequest;
import com.visionmapping.dto.request.StepUpVerifyRequest;
import com.visionmapping.dto.response.StepUpChallengeResponse;
import com.visionmapping.dto.response.StepUpVerifyResponse;
import com.visionmapping.service.stepup.StepUpService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.visionmapping.web.ApiPaths;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.constraints.Positive;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

/** V20 (BRD 20.0.0): "Verify it's you" codes for the signed-in person. */
@RestController
@RequestMapping(value = ApiPaths.V1 + "/account/step-up", produces = MediaType.APPLICATION_JSON_VALUE)
@Validated
@RequiredArgsConstructor
@Tag(name = "Verify it is you", description = "Codes that confirm a sensitive action")
public class StepUpController {

    private final StepUpService service;

    /** SV-FR-10: send a code for one action (in the person's language). */
    @Operation(summary = "Send a 'Verify it is you' code")
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<StepUpChallengeResponse> request(@Valid @RequestBody StepUpRequest request,
                                           @RequestParam(defaultValue = "en") @Pattern(regexp = "en|km") String language) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.request(request, language));
    }

    /** SV-FR-13: check the code the person typed. */
    @Operation(summary = "Check a 'Verify it is you' code")
    @PostMapping(value = "/{challengeId}/verify", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<StepUpVerifyResponse> verify(@PathVariable @Positive Long challengeId, @Valid @RequestBody StepUpVerifyRequest request) {
        return ResponseEntity.ok(service.verify(challengeId, request.code()));
    }
}
