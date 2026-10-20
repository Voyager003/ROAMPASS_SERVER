package com.roampass_server.history.application;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import com.roampass_server.common.domain.OrderNo;
import com.roampass_server.history.domain.model.StatusHistory;
import com.roampass_server.history.domain.repository.StatusHistoryRepository;

/**
 * 테스트용 인메모리 StatusHistoryRepository
 */
public class FakeStatusHistoryRepository implements StatusHistoryRepository {

    private final List<StatusHistory> histories = new ArrayList<>();

    @Override
    public void append(StatusHistory history) {
        histories.add(history);
    }

    @Override
    public List<StatusHistory> findByOrderNo(OrderNo orderNo) {
        return histories.stream()
                .filter(h -> h.orderNo().equals(orderNo))
                .sorted(Comparator.comparing(StatusHistory::occurredAt)) // 안정 정렬이라 같은 시각은 저장 순서 유지
                .toList();
    }

    public List<StatusHistory> all() {
        return List.copyOf(histories);
    }
}
