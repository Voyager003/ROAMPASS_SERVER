package com.roampass.mock.pg.api;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.roampass.mock.pg.fault.FaultConfig;
import com.roampass.mock.pg.fault.FaultSettings;

/**
 * 시연·테스트용 장애 주입 설정 조회·교체·초기화
 */
@RestController
@RequestMapping("/mock/pg/config")
public class FaultConfigController {

    private final FaultConfig faultConfig;

    public FaultConfigController(FaultConfig faultConfig) {
        this.faultConfig = faultConfig;
    }

    @GetMapping
    public FaultSettings get() {
        return faultConfig.current();
    }

    @PutMapping
    public FaultSettings replace(@RequestBody FaultSettingsRequest request) {
        return faultConfig.update(request.toSettings());
    }

    @PostMapping("/reset")
    public FaultSettings reset() {
        return faultConfig.reset();
    }
}
