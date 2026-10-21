package com.roampass_server.mysim.application;

import java.time.Instant;
import java.util.List;

/**
 * 발급 대기 정보(이심 결제 후 SIM을 받기 전). 클라이언트는 {@code pollIntervalSeconds}마다 상세를 다시 조회
 *
 * @param stage               QUEUED: 공급사 요청 전(스케줄러 대기), REQUESTED: 공급사 처리 중
 * @param requestedAt         공급사에 요청한 시각, QUEUED면 null
 * @param resultDeadline      이 시각까지 결과가 없으면 서버가 발급 실패로 처리하고 자동 환불, QUEUED면 null
 * @param overdue             결과 대기 한도를 넘김(곧 실패 처리·자동 환불 예정)
 * @param pollIntervalSeconds 권장 재조회 간격
 * @param actions             지금 사용자가 할 수 있는 일
 */
public record IssuanceWaiting(Stage stage, Instant requestedAt, Instant resultDeadline, boolean overdue,
                              int pollIntervalSeconds, List<Action> actions) {

    public enum Stage {
        QUEUED,
        REQUESTED
    }

    public enum Action {
        /** 화면에서 계속 기다리기 */
        WAIT,
        /** 화면을 닫아도 서버가 발급을 계속 진행, 나중에 내 SIM에서 확인 */
        CHECK_LATER,
        /** 환불 요청, 공급사 요청 전에만 */
        REQUEST_REFUND
    }
}
