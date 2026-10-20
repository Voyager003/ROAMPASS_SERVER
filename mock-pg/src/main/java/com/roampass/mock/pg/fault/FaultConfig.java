package com.roampass.mock.pg.fault;

import java.util.concurrent.atomic.AtomicReference;
import java.util.function.DoubleSupplier;

/**
 * 현재 장애 주입 설정 보관. 실행 중 교체 가능, reset 시 시작 설정으로 복귀
 * 확률 판정용 난수는 주입(테스트에서 고정값 사용)
 */
public class FaultConfig {

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

    public FaultSettings update(FaultSettings settings) {
        current.set(settings);
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
