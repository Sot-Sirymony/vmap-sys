package com.visionmapping.service;

import static com.visionmapping.service.support.ServiceSupport.requireNotFutureDay;

import com.visionmapping.dto.request.AttitudeCheckInRequest;
import com.visionmapping.dto.response.AttitudeCheckInResponse;
import com.visionmapping.entity.AttitudeCheckIn;
import com.visionmapping.mapper.VisionMappingMapper;
import com.visionmapping.repository.AttitudeCheckInRepository;
import com.visionmapping.service.support.EntityLookup;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** FR-65: one attitude score per day. Saving the same day again replaces it. */
@Service
@Transactional
@RequiredArgsConstructor
public class AttitudeCheckInService {

    private final EntityLookup lookup;
    private final VisionMappingMapper mapper;
    private final AttitudeCheckInRepository attitudeCheckInRepository;
    private final Clock clock;

    @Transactional(readOnly = true)
    public List<AttitudeCheckInResponse> listRecent() {
        return attitudeCheckInRepository.findTop30ByUser_IdOrderByCheckInDateDesc(lookup.userId()).stream()
                .map(mapper::toResponse)
                .toList();
    }

    public AttitudeCheckInResponse saveForDate(LocalDate date, AttitudeCheckInRequest request) {
        requireNotFutureDay(date, clock);
        AttitudeCheckIn entity = attitudeCheckInRepository.findByUser_IdAndCheckInDate(lookup.userId(), date)
                .orElseGet(() -> AttitudeCheckIn.builder().user(lookup.currentUser()).checkInDate(date).build());
        entity.setScore(request.score());
        entity.setNote(request.note());
        return mapper.toResponse(attitudeCheckInRepository.save(entity));
    }
}
