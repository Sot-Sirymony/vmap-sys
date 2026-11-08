package com.visionmapping.service;

import com.visionmapping.entity.Dream;
import com.visionmapping.entity.Goal;
import com.visionmapping.entity.Obstacle;
import com.visionmapping.entity.Partner;
import com.visionmapping.entity.ProgressLog;
import com.visionmapping.entity.Review;
import com.visionmapping.entity.TaskItem;
import com.visionmapping.entity.VisionArea;
import com.visionmapping.entity.VisionStep;
import com.visionmapping.util.Copies;
import java.util.List;

/** Every unarchived record of the user, already narrowed to one area when the caller asked for it. */
record DashboardData(
        List<VisionArea> areas,
        List<Dream> dreams,
        List<Goal> goals,
        List<VisionStep> steps,
        List<TaskItem> tasks,
        List<Obstacle> obstacles,
        List<Partner> partners,
        List<Review> reviews,
        List<ProgressLog> progressLogs) {
    public DashboardData {
        areas = Copies.list(areas);
        dreams = Copies.list(dreams);
        goals = Copies.list(goals);
        steps = Copies.list(steps);
        tasks = Copies.list(tasks);
        obstacles = Copies.list(obstacles);
        partners = Copies.list(partners);
        reviews = Copies.list(reviews);
        progressLogs = Copies.list(progressLogs);
    }

}
