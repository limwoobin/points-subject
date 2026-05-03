package com.example.pointssubject.service.query.dto;

import com.example.pointssubject.domain.entity.PointActionLog;
import com.example.pointssubject.domain.enums.PointActionType;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.domain.Page;

public record PointHistoryView(
    Long userId,
    int page,
    int size,
    long totalElements,
    int totalPages,
    boolean hasNext,
    List<Item> items
) {

    public static PointHistoryView from(Long userId, Page<PointActionLog> page) {
        List<Item> items = page.getContent().stream()
            .map(Item::from)
            .toList();
        return new PointHistoryView(
            userId,
            page.getNumber(),
            page.getSize(),
            page.getTotalElements(),
            page.getTotalPages(),
            page.hasNext(),
            items
        );
    }

    /** id = 도메인 row id (EARN 계열은 point_earn.id, USE 계열은 point_use.id). */
    public record Item(
        PointActionType type,
        Long id,
        Long amount,
        String orderNumber,
        LocalDateTime occurredAt
    ) {

        public static Item from(PointActionLog log) {
            Long id = switch (log.getActionType()) {
                case EARN, EARN_CANCEL -> log.getPointEarnId();
                case USE, USE_CANCEL -> log.getPointUseId();
            };
            return new Item(
                log.getActionType(),
                id,
                log.getAmount(),
                log.getOrderNumber(),
                log.getCreatedAt()
            );
        }
    }
}
