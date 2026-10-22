package com.roampass.mock.supplier.fault;

import java.util.concurrent.atomic.AtomicReference;
import java.util.function.DoubleSupplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 현재 장애 주입 설정 보관. 실행 중 교체 가능, reset 시 시작 설정으로 복귀
 * 확률 판정용 난수는 주입(테스트에서 고정값 사용)
 */
public class FaultConfig {

    private static final Logger log = LoggerFactory.getLogger(FaultConfig.class);

    private final FaultSettings initial;
    private final DoubleSupplier random;
    private final AtomicReference<FaultSettings> current;

    public FaultConfig(FaultSettings initial, DoubleSupplier random) {
        this.initial = initial;
        this.random = random;
        this.current = new AtomicReference<>(initial);
    }

    public FaultSettings current() {
        return current.get();
    }

    /** 장애 설정 변경은 시연·장애 원인 추적의 핵심이라 WARN으로 이전·이후 값을 남김 */
    public FaultSettings update(FaultSettings settings) {
        FaultSettings before = current.getAndSet(settings);
        log.warn("event=supplier_faults_changed before={} after={}", before, settings);
        return settings;
    }

    public FaultSettings reset() {
        return update(initial);
    }

    /** 확률 rate로 장애 발생 여부. 0이면 난수를 쓰지 않고 항상 false */
    public boolean roll(double rate) {
        return rate > 0 && random.getAsDouble() < rate;
    }
}
