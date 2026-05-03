package com.example.pointssubject.service.command;

import com.example.pointssubject.domain.entity.PointActionLog;
import com.example.pointssubject.repository.PointActionLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PointActionLogger {

    private final PointActionLogRepository repository;

    public void earn(Long userId, Long pointEarnId, Long amount) {
        repository.save(PointActionLog.earn(userId, pointEarnId, amount));
    }

    public void earnCancel(Long userId, Long pointEarnId, Long amount) {
        repository.save(PointActionLog.earnCancel(userId, pointEarnId, amount));
    }

    public void use(Long userId, Long pointUseId, Long amount, String orderNumber) {
        repository.save(PointActionLog.use(userId, pointUseId, amount, orderNumber));
    }

    public void useCancel(Long userId,
                          Long pointUseId,
                          Long amount,
                          String orderNumber,
                          String orderRefundId) {
        repository.save(PointActionLog.useCancel(userId, pointUseId, amount, orderNumber, orderRefundId));
    }
}
