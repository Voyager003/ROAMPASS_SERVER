package com.roampass_server.mysim.api;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.roampass_server.common.domain.OrderNo;
import com.roampass_server.common.domain.UserId;
import com.roampass_server.mysim.application.MyRefundResult;
import com.roampass_server.mysim.application.MyRefundService;

/**
 * 사용자 환불 요청 API, X-User-Id 필수
 * 202: 접수(REFUNDING), 실제 PG 취소는 환불 스케줄러가 진행하고 결과는 내 SIM 상세로 확인
 */
@RestController
public class MyRefundController {

    private final MyRefundService refundService;

    public MyRefundController(MyRefundService refundService) {
        this.refundService = refundService;
    }

    @PostMapping("/api/orders/{orderNo}/refund")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public MyRefundResult refund(UserId userId, @PathVariable String orderNo) {
        return refundService.refund(userId, OrderNo.of(orderNo));
    }
}
