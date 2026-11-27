package com.visionmapping.dto.response;

import com.visionmapping.util.Copies;
import java.util.List;
import java.util.function.Function;
import org.springframework.data.domain.Page;

/**
 * V33 (D-33-9): one page of a list, in a JSON shape VMS owns. Every paged
 * endpoint answers with this record, never with a raw {@code Page}, whose JSON
 * shape is not stable across Spring versions.
 *
 * @param content       the items on this page
 * @param page          the page number, from 0
 * @param size          the page size asked for
 * @param totalElements the number of items on all pages
 * @param totalPages    the number of pages
 * @param first         whether this is the first page
 * @param last          whether this is the last page
 * @param sort          the sort orders used, in order
 */
public record PagedResponse<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean first,
        boolean last,
        List<SortOrder> sort) {

    public PagedResponse {
        content = Copies.list(content);
        sort = Copies.list(sort);
    }

    /** Maps each item with {@code mapper} and keeps the page numbers and sort orders. */
    public static <E, T> PagedResponse<T> from(Page<E> page, Function<? super E, ? extends T> mapper) {
        List<T> content = page.getContent().stream().<T>map(mapper).toList();
        List<SortOrder> sort = page.getSort().stream()
                .map(order -> new SortOrder(order.getProperty(), order.getDirection().name()))
                .toList();
        return new PagedResponse<>(content, page.getNumber(), page.getSize(), page.getTotalElements(),
                page.getTotalPages(), page.isFirst(), page.isLast(), sort);
    }

    /** One sort order: a property and {@code ASC} or {@code DESC}. */
    public record SortOrder(String property, String direction) {
    }
}
