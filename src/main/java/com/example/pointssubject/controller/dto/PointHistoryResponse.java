package com.example.pointssubject.controller.dto;

import com.example.pointssubject.domain.enums.PointActionType;
import com.example.pointssubject.service.query.dto.PointHistoryView;
import java.time.LocalDateTime;
import java.util.List;

public record PointHistoryResponse(
    Long userId,
    int page,
    int size,
    long totalElements,
    int totalPages,
    boolean hasNext,
    List<Item> items
) {

    public static PointHistoryResponse from(PointHistoryView view) {
        List<Item> items = view.items().stream()
            .map(Item::from)
            .toList();
        return new PointHistoryResponse(
            view.userId(),
            view.page(),
            view.size(),
            view.totalElements(),
            view.totalPages(),
            view.hasNext(),
            items
        );
    }

    public record Item(
        PointActionType type,
        Long id,
        Long amount,
        String orderNumber,
        LocalDateTime occurredAt
    ) {

        public static Item from(PointHistoryView.Item v) {
            return new Item(v.type(), v.id(), v.amount(), v.orderNumber(), v.occurredAt());
        }
    }
}
