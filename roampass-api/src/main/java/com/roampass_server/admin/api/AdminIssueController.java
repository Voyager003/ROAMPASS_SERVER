package com.roampass_server.admin.api;

import java.time.Clock;
import java.time.Instant;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.roampass_server.admin.application.AdminActionResult;
import com.roampass_server.admin.application.AdminActionService;
import com.roampass_server.admin.application.AdminIssueQueryService;
import com.roampass_server.admin.application.AdminIssues;
import com.roampass_server.common.domain.AdminId;
import com.roampass_server.common.domain.OrderNo;

/**
 * 어드민 이상 건 조회·운영자 조치 API. X-Admin-Id 필수
 */
@RestController
public class AdminIssueController {

    private final AdminIssueQueryService issueQueryService;
    private final AdminActionService actionService;
    private final Clock clock;

    public AdminIssueController(AdminIssueQueryService issueQueryService, AdminActionService actionService,
                                Clock clock) {
        this.issueQueryService = issueQueryService;
        this.actionService = actionService;
        this.clock = clock;
    }

    @GetMapping("/admin/issues")
    public AdminResponse<AdminIssues> issues() {
        Instant asOf = Instant.now(clock);
        return new AdminResponse<>(asOf, issueQueryService.find());
    }

    /** 수동 환불(전액). 실제 PG 취소는 환불 스케줄러가 진행 */
    @PostMapping("/admin/orders/{orderNo}/refund")
    public AdminResponse<AdminActionResult> refund(AdminId adminId, @PathVariable String orderNo,
                                                   @RequestBody AdminActionRequest request) {
        AdminActionResult result = actionService.refund(adminId, OrderNo.of(orderNo), request.reason());
        return new AdminResponse<>(Instant.now(clock), result);
    }

    @PostMapping("/admin/orders/{orderNo}/reissue")
    public AdminResponse<AdminActionResult> reissue(AdminId adminId, @PathVariable String orderNo,
                                                    @RequestBody AdminActionRequest request) {
        AdminActionResult result = actionService.reissue(adminId, OrderNo.of(orderNo), request.reason());
        return new AdminResponse<>(Instant.now(clock), result);
    }
}
