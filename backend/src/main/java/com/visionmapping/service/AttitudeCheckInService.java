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
import com.visionmapping.dto.response.PagedResponse;
import com.visionmapping.util.Pageables;
import java.util.Set;
import org.springframework.data.domain.Pageable;

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

    /** V33 (D-33-10): the properties check-ins can be sorted by. */
    static final Set<String> CHECK_IN_SORTS = Set.of("checkInDate", "score");

    /** V33 (D-33-9): one page of the user's check-ins; the first page of 30 is what the journal showed before. */
    @Transactional(readOnly = true)
    public PagedResponse<AttitudeCheckInResponse> pageCheckIns(Pageable pageable) {
        return PagedResponse.from(attitudeCheckInRepository.findByUser_Id(lookup.userId(),
                Pageables.whitelist(pageable, CHECK_IN_SORTS)), mapper::toResponse);
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
