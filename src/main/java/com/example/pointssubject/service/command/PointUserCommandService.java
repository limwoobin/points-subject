package com.example.pointssubject.service.command;

import com.example.pointssubject.domain.entity.PointUser;
import com.example.pointssubject.repository.PointUserRepository;
import com.example.pointssubject.service.command.dto.UpdateUserMaxBalanceCommand;
import com.example.pointssubject.service.command.dto.UpdateUserMaxBalanceResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 회원 단위 변경 (현재: 보유 한도 갱신).
 * 적립과 동일한 비관 락을 거쳐 갱신 — 직후 적립 검증이 즉시 새 한도를 본다.
 */
@Service
@RequiredArgsConstructor
public class PointUserCommandService {

    private final PointUserRepository userRepository;

    @Transactional
    public UpdateUserMaxBalanceResult updateMaxBalance(UpdateUserMaxBalanceCommand command) {
        PointUser user = userRepository.findByUserIdForUpdate(command.userId())
            .orElseGet(() -> userRepository.save(PointUser.create(command.userId())));
        user.updateMaxBalance(command.maxBalance());
        return UpdateUserMaxBalanceResult.from(user);
    }
}
