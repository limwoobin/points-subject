package com.example.pointssubject.controller;

import com.example.pointssubject.controller.dto.CancelEarnRequest;
import com.example.pointssubject.controller.dto.CancelEarnResponse;
import com.example.pointssubject.controller.dto.CancelUsePointRequest;
import com.example.pointssubject.controller.dto.CancelUsePointResponse;
import com.example.pointssubject.controller.dto.EarnPointRequest;
import com.example.pointssubject.controller.dto.EarnPointResponse;
import com.example.pointssubject.controller.dto.UsePointRequest;
import com.example.pointssubject.controller.dto.UsePointResponse;
import com.example.pointssubject.service.command.PointEarnCommandService;
import com.example.pointssubject.service.command.PointUseCommandService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/points")
@RequiredArgsConstructor
@Validated
public class PointCommandController {

    private final PointEarnCommandService earnService;
    private final PointUseCommandService useService;

    @PostMapping("/earn")
    public ResponseEntity<EarnPointResponse> earn(@RequestBody @Valid EarnPointRequest request) {
        var result = earnService.earn(request.toCommand());
        return ResponseEntity.status(HttpStatus.CREATED).body(EarnPointResponse.from(result));
    }

    @PostMapping("/earn/{earnId}/cancel")
    public CancelEarnResponse cancelEarn(@PathVariable @Positive Long earnId,
                                         @RequestBody @Valid CancelEarnRequest request) {
        var result = earnService.cancelEarn(request.toCommand(earnId));
        return CancelEarnResponse.from(result);
    }

    @PostMapping("/use")
    public ResponseEntity<UsePointResponse> use(@RequestBody @Valid UsePointRequest request) {
        var result = useService.use(request.toCommand());
        return ResponseEntity.status(HttpStatus.CREATED).body(UsePointResponse.from(result));
    }

    @PostMapping("/cancel")
    public CancelUsePointResponse cancelUse(@RequestBody @Valid CancelUsePointRequest request) {
        var result = useService.cancelUse(request.toCommand());
        return CancelUsePointResponse.from(result);
    }
}
