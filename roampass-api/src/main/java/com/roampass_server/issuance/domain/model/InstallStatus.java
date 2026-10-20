package com.roampass_server.issuance.domain.model;

/**
 * 발급된 SIM이 사용자 기기에서 진행한 단계. 진행 순서대로 선언, 앞으로만 진행:
 * 늦게 도착한 이전 단계 신호는 무시, 신호가 빠지면 단계 건너뛰기 허용(예: ISSUED → ACTIVE)
 */
public enum InstallStatus {
    /** 발급 완료, QR·활성화 코드 표시 가능 */
    ISSUED,
    /** 사용자가 우리 화면에서 QR 열람 — 우리가 직접 기록하는 유일한 신호 */
    QR_VIEWED,
    /** 기기에 프로필 다운로드 — 공급사를 통해서만 확인 가능 */
    INSTALLED,
    /** 현지 개통, 사용 기간 시작 */
    ACTIVE,
    /** 사용 기간 종료 */
    EXPIRED;

    public boolean isBeyond(InstallStatus other) {
        return ordinal() > other.ordinal();
    }
}
