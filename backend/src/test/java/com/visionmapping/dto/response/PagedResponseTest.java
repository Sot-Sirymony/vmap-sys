package com.visionmapping.dto.response;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.visionmapping.exception.BusinessRuleException;
import com.visionmapping.util.Pageables;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

/** BRD 33 D-33-9, D-33-10, BR-33-4: the shared page record and the sort whitelist. */
class PagedResponseTest {

    @Test
    void aPageIsMappedAndKeepsItsNumbers() {
        PageRequest request = PageRequest.of(1, 2, Sort.by(Sort.Order.desc("createdAt"), Sort.Order.asc("id")));

        PagedResponse<String> page = PagedResponse.from(new PageImpl<>(List.of(3, 4), request, 5), number -> "#" + number);

        assertThat(page.content()).containsExactly("#3", "#4");
        assertThat(page.page()).isEqualTo(1);
        assertThat(page.size()).isEqualTo(2);
        assertThat(page.totalElements()).isEqualTo(5);
        assertThat(page.totalPages()).isEqualTo(3);
        assertThat(page.first()).isFalse();
        assertThat(page.last()).isFalse();
        assertThat(page.sort()).containsExactly(new PagedResponse.SortOrder("createdAt", "DESC"),
                new PagedResponse.SortOrder("id", "ASC"));
    }

    @Test
    void anAllowedSortGetsTheIdTieBreaker() {
        Pageable checked = Pageables.whitelist(PageRequest.of(0, 20, Sort.by(Sort.Order.desc("dueDate"))), Set.of("dueDate"));

        assertThat(checked.getPageNumber()).isZero();
        assertThat(checked.getPageSize()).isEqualTo(20);
        assertThat(checked.getSort()).containsExactly(Sort.Order.desc("dueDate"), Sort.Order.desc("id"));
    }

    @Test
    void noSortStillSortsById() {
        assertThat(Pageables.whitelist(PageRequest.of(0, 20), Set.of("dueDate")).getSort())
                .containsExactly(Sort.Order.asc("id"));
    }

    @Test
    void anIdSortIsKeptAsItIs() {
        Pageable checked = Pageables.whitelist(PageRequest.of(0, 5, Sort.by(Sort.Order.desc("id"))), Set.of("dueDate"));

        assertThat(checked.getSort()).containsExactly(Sort.Order.desc("id"));
    }

    @Test
    void anUnpagedRequestStaysUnpaged() {
        Pageable checked = Pageables.whitelist(Pageable.unpaged(Sort.by("dueDate")), Set.of("dueDate"));

        assertThat(checked.isUnpaged()).isTrue();
        assertThat(checked.getSort()).containsExactly(Sort.Order.asc("dueDate"), Sort.Order.asc("id"));
    }

    @Test
    void anUnknownSortIsRefused() {
        PageRequest request = PageRequest.of(0, 20, Sort.by("passwordHash"));

        assertThatThrownBy(() -> Pageables.whitelist(request, Set.of("dueDate", "createdAt")))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("This list cannot be sorted by passwordHash. Sort by one of: createdAt, dueDate.");
    }
}
