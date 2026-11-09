package com.visionmapping.service;

import com.visionmapping.dto.response.WorkStyleBalanceResponse;
import com.visionmapping.dto.response.WorkStyleBalanceResponse.BalancePartner;
import com.visionmapping.entity.AppUser;
import com.visionmapping.entity.Partner;
import com.visionmapping.entity.enums.PartnerStatus;
import com.visionmapping.entity.enums.WorkStyleArchetype;
import com.visionmapping.repository.PartnerRepository;
import com.visionmapping.service.support.EntityLookup;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** V15 Phase 5: the Work Style helpers that need no AI (AH-NFR-03). */
@Service
@RequiredArgsConstructor
public class WorkStyleSupportService {

    /** FR-49.4, the same table as the Steps page's Ideal Partner Profile: who complements each style's likely blind spot. */
    public static final Map<WorkStyleArchetype, List<WorkStyleArchetype>> COMPLEMENTARY = new EnumMap<>(WorkStyleArchetype.class);
    private static final Set<PartnerStatus> ACTIVE = Set.of(PartnerStatus.TO_CONTACT, PartnerStatus.CONTACTED, PartnerStatus.AGREED,
            PartnerStatus.ACTIVE, PartnerStatus.WAITING);

    static {
        COMPLEMENTARY.put(WorkStyleArchetype.DRIVER, List.of(WorkStyleArchetype.PLANNER));
        COMPLEMENTARY.put(WorkStyleArchetype.CONNECTOR, List.of(WorkStyleArchetype.STEADIER, WorkStyleArchetype.PLANNER));
        COMPLEMENTARY.put(WorkStyleArchetype.STEADIER, List.of(WorkStyleArchetype.DRIVER));
        COMPLEMENTARY.put(WorkStyleArchetype.PLANNER, List.of(WorkStyleArchetype.CONNECTOR));
    }

    private final EntityLookup lookup;
    private final PartnerRepository partnerRepository;

    /** AH-FR-73: partners by style, the complementary styles, and the ones no partner has yet. */
    @Transactional(readOnly = true)
    public WorkStyleBalanceResponse partnerBalance() {
        AppUser user = lookup.currentUser();
        List<Partner> active = partnerRepository.findByUser_IdAndArchivedFalse(user.getId()).stream()
                .filter(partner -> partner.getStatus() == null || ACTIVE.contains(partner.getStatus()))
                .sorted(Comparator.comparing(Partner::getName, String.CASE_INSENSITIVE_ORDER))
                .toList();
        Map<String, Long> counts = new LinkedHashMap<>();
        for (WorkStyleArchetype style : WorkStyleArchetype.values()) {
            counts.put(style.name(), active.stream().filter(partner -> partner.getWorkStyleType() == style).count());
        }
        long noStyle = active.stream().filter(partner -> partner.getWorkStyleType() == null).count();
        WorkStyleArchetype dominant = user.getWorkStyleDominant();
        List<WorkStyleArchetype> complementary = dominant == null ? List.of() : COMPLEMENTARY.get(dominant);
        List<String> missing = complementary.stream().filter(style -> counts.get(style.name()) == 0).map(Enum::name).toList();
        List<BalancePartner> partners = active.stream()
                .map(partner -> new BalancePartner(partner.getId(), partner.getName(), partner.getRole(),
                        partner.getSupportType() == null ? null : partner.getSupportType().name(),
                        partner.getWorkStyleType() == null ? null : partner.getWorkStyleType().name(),
                        partner.getPrimaryMotivator() == null ? null : partner.getPrimaryMotivator().name()))
                .toList();
        return new WorkStyleBalanceResponse(dominant == null ? null : dominant.name(), complementary.stream().map(Enum::name).toList(),
                counts, noStyle, missing, partners);
    }
}
