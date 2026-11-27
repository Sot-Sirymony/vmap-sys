package com.visionmapping.controller;

import com.visionmapping.service.twofactor.TwoFactorService;
import jakarta.validation.constraints.Positive;
import com.visionmapping.web.ApiPaths;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;

/** V21 (TF-FR-23): the devices that skip the second step. Split from TwoFactorAccountController in V33. */
@RestController
@RequestMapping(value = ApiPaths.V1 + "/account/trusted-devices", produces = MediaType.APPLICATION_JSON_VALUE)
@Validated
@RequiredArgsConstructor
@Tag(name = "Trusted devices", description = "Devices that skip the second sign-in step")
public class TrustedDeviceController {

    private final TwoFactorService twoFactorService;

    @Operation(summary = "Forget one trusted device")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> removeDevice(@PathVariable @Positive Long id) {
        twoFactorService.removeDevice(id);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Forget every trusted device")
    @DeleteMapping
    public ResponseEntity<Void> removeAllDevices() {
        twoFactorService.removeAllDevices();
        return ResponseEntity.noContent().build();
    }
}
