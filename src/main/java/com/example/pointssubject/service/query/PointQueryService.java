package com.example.pointssubject.service.query;

import com.example.pointssubject.domain.entity.PointActionLog;
import com.example.pointssubject.exception.PointErrorCode;
import com.example.pointssubject.exception.PointException;
import com.example.pointssubject.repository.PointActionLogRepository;
import com.example.pointssubject.repository.PointEarnRepository;
import com.example.pointssubject.service.query.dto.BalanceView;
import com.example.pointssubject.service.query.dto.PointHistoryView;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PointQueryService {

    private final PointEarnRepository earnRepository;
    private final PointActionLogRepository actionLogRepository;

    private static final int MAX_PAGE_SIZE = 100;

    @Transactional(readOnly = true)
    public BalanceView getBalance(Long userId) {
        long balance = earnRepository.sumActiveBalance(userId, LocalDateTime.now());
        return new BalanceView(userId, balance);
    }

    @Transactional(readOnly = true)
    public PointHistoryView getHistory(Long userId, int page, int size) {
        if (page < 0) {
            throw new PointException(PointErrorCode.VALIDATION_FAILED, "page=" + page);
        }

        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new PointException(PointErrorCode.VALIDATION_FAILED, "size=" + size + ", allowed=1.." + MAX_PAGE_SIZE);
        }

        Pageable pageable = PageRequest.of(page, size);
        Page<PointActionLog> result = actionLogRepository.findByUserIdOrderByCreatedAtDescIdDesc(userId, pageable);
        return PointHistoryView.from(userId, result);
    }
}
