package com.visionmapping.controller;

import com.visionmapping.dto.request.ObstacleHelpRequest;
import com.visionmapping.dto.response.ObstacleHelpResponses.BlockedTask;
import com.visionmapping.dto.response.ObstacleHelpResponses.ObstacleHelpAnswer;
import com.visionmapping.dto.response.ObstacleHelpResponses.PartnerMatches;
import com.visionmapping.dto.response.ObstacleHelpResponses.RankedObstacle;
import com.visionmapping.entity.enums.ObstacleType;
import com.visionmapping.service.ObstacleSupportService;
import com.visionmapping.service.ai.ObstacleHelpService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * V15 Phase 2: the obstacle helpers (AH-FR-31 to AH-FR-41). The AI helpers go
 * through one endpoint with a mode; partner matching, Blocked tasks with no
 * obstacle and the which-first order need no AI (AH-NFR-03).
 */
@Validated
@RestController
@RequiredArgsConstructor
public class ObstacleHelpController {

    private final ObstacleHelpService helpService;
    private final ObstacleSupportService supportService;

    /** AH-FR-31 to AH-FR-33, AH-FR-37, AH-FR-41: one AI helper. Nothing is saved. */
    @PostMapping("/api/ai/obstacles/help")
    public ObstacleHelpAnswer help(@Valid @RequestBody ObstacleHelpRequest request,
                                   @RequestParam(defaultValue = "en") @Pattern(regexp = "en|km") String language) {
        return helpService.help(request, language);
    }

    /** AH-FR-35: the participant's own partners who fit the obstacle type. No AI; nothing is sent to Claude. */
    @GetMapping("/api/obstacles/partner-matches")
    public PartnerMatches partnerMatches(@RequestParam ObstacleType obstacleType) {
        return supportService.partnerMatches(obstacleType);
    }

    /** AH-FR-41: open obstacles in the rule order. No AI. */
    @GetMapping("/api/obstacles/priority")
    public List<RankedObstacle> priority() {
        return supportService.priority();
    }

    /** AH-FR-39: Blocked tasks with a blocker reason and no obstacle. No AI. */
    @GetMapping("/api/tasks/blocked-without-obstacle")
    public List<BlockedTask> blockedWithoutObstacle() {
        return supportService.blockedWithoutObstacle();
    }
}
