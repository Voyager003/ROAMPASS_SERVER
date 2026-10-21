package com.roampass_server.mysim.api;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.roampass_server.common.domain.OrderNo;
import com.roampass_server.common.domain.PageResult;
import com.roampass_server.common.domain.UserId;
import com.roampass_server.common.domain.exception.InvalidValueException;
import com.roampass_server.mysim.application.MySimDetail;
import com.roampass_server.mysim.application.MySimQueryService;
import com.roampass_server.mysim.application.MySimSummary;

/**
 * 내 SIM 조회 API, X-User-Id 필수
 */
@RestController
@RequestMapping("/api/sims")
public class MySimController {

    static final int MAX_PAGE_SIZE = 50;

    private final MySimQueryService queryService;

    public MySimController(MySimQueryService queryService) {
        this.queryService = queryService;
    }

    @GetMapping
    public PageResult<MySimSummary> list(UserId userId,
                                         @RequestParam(defaultValue = "0") int page,
                                         @RequestParam(defaultValue = "20") int size) {
        if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
            throw new InvalidValueException("INVALID_PAGE", "page must be >= 0 and size between 1 and " + MAX_PAGE_SIZE);
        }
        return queryService.list(userId, page, size);
    }

    @GetMapping("/{orderNo}")
    public MySimDetail detail(UserId userId, @PathVariable String orderNo) {
        return queryService.detail(userId, OrderNo.of(orderNo));
    }
}
