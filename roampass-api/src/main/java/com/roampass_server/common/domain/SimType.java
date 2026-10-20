package com.roampass_server.common.domain;

/**
 * 서비스가 판매하는 SIM 두 종류. 요금제·주문·발급이 모두 사용하므로 공유 커널에 위치
 * 종류에 따라 결제 이후 경로가 갈림(이심은 공급사 발급, 유심은 공항 수령 후 ICCID 등록)
 */
public enum SimType {
    /** QR·활성화 코드로 휴대폰에 내려받는 SIM */
    ESIM,
    /** 공항에서 받는 실물 카드 */
    USIM
}
