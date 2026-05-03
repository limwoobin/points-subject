package com.example.pointssubject.service.command;

import com.example.pointssubject.domain.entity.PointUser;
import com.example.pointssubject.repository.PointUserRepository;
import com.example.pointssubject.service.command.dto.UpdateUserMaxBalanceCommand;
import com.example.pointssubject.service.command.dto.UpdateUserMaxBalanceResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
