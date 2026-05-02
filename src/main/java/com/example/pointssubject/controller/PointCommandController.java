package com.example.pointssubject.controller;

import com.example.pointssubject.controller.dto.CancelEarnResponse;
import com.example.pointssubject.controller.dto.EarnPointRequest;
import com.example.pointssubject.controller.dto.EarnPointResponse;
import com.example.pointssubject.service.command.PointEarnCommandService;
import com.example.pointssubject.service.command.dto.CancelEarnCommand;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/points")
@RequiredArgsConstructor
public class PointCommandController {

    private final PointEarnCommandService earnService;

    @PostMapping("/earn")
    public ResponseEntity<EarnPointResponse> earn(@RequestBody @Valid EarnPointRequest request) {
        var result = earnService.earn(request.toCommand());
        return ResponseEntity.status(HttpStatus.CREATED).body(EarnPointResponse.from(result));
    }

    @PostMapping("/earn/{earnId}/cancel")
    public CancelEarnResponse cancelEarn(@PathVariable Long earnId) {
        var result = earnService.cancelEarn(new CancelEarnCommand(earnId));
        return CancelEarnResponse.from(result);
    }
}
