package com.visionmapping.util;

import com.visionmapping.exception.BusinessRuleException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;

/**
 * V33 (D-33-10, BR-33-4): checks the sort of a paged request before it reaches
 * a query. Each service names the properties its list can be sorted by.
 */
public final class Pageables {

    /** The last sort order on every page, so that pages never repeat or skip rows. */
    static final String TIE_BREAKER = "id";

    private Pageables() {
    }

    /**
     * V33 (D-33-9): one page of a list already built in memory, for lists that
     * are filtered in Java (unplaced obstacles, saved report search). The list's
     * own order is kept; the page's sort is only reported.
     */
    public static <T> Page<T> slice(List<T> items, Pageable pageable) {
        if (pageable.isUnpaged()) {
            return new PageImpl<>(items, pageable, items.size());
        }
        int from = (int) Math.min(pageable.getOffset(), items.size());
        int to = (int) Math.min(from + (long) pageable.getPageSize(), items.size());
        return new PageImpl<>(items.subList(from, to), pageable, items.size());
    }

    /**
     * The same page, sorted only by allowed properties, with {@code id}
     * added last when it is not already there.
     *
     * @throws BusinessRuleException (answered as 400) for a property outside {@code allowed}
     */
    public static Pageable whitelist(Pageable pageable, Set<String> allowed) {
        List<Sort.Order> orders = new ArrayList<>();
        Set<String> refused = new TreeSet<>();
        for (Sort.Order order : pageable.getSort()) {
            if (allowed.contains(order.getProperty()) || TIE_BREAKER.equals(order.getProperty())) {
                orders.add(order);
            } else {
                refused.add(order.getProperty());
            }
        }
        if (!refused.isEmpty()) {
            throw new BusinessRuleException("This list cannot be sorted by " + String.join(", ", refused)
                    + ". Sort by one of: " + String.join(", ", new TreeSet<>(allowed)) + ".");
        }
        if (orders.stream().noneMatch(order -> TIE_BREAKER.equals(order.getProperty()))) {
            Sort.Direction direction = orders.isEmpty() ? Sort.Direction.ASC : orders.get(orders.size() - 1).getDirection();
            orders.add(new Sort.Order(direction, TIE_BREAKER));
        }
        if (pageable.isUnpaged()) {
            return Pageable.unpaged(Sort.by(orders));
        }
        return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), Sort.by(orders));
    }
}
