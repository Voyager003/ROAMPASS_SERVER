package com.roampass.mock.supplier.callback;

/**
 * 발송할 콜백 + 서명 유효 여부(장애 주입 "서명 오류"면 false → 틀린 비밀키로 서명)
 */
public record CallbackDelivery(CallbackEvent event, boolean signatureValid) {
}
