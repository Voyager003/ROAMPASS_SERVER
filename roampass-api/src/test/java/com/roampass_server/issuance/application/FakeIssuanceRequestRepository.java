package com.roampass_server.issuance.application;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import com.roampass_server.common.domain.OrderNo;
import com.roampass_server.issuance.domain.model.IssuanceRequest;
import com.roampass_server.issuance.domain.model.IssuanceStatus;
import com.roampass_server.issuance.domain.repository.IssuanceRequestRepository;

/**
 * 테스트용 인메모리 IssuanceRequestRepository. 저장 시 id 부여
 */
public class FakeIssuanceRequestRepository implements IssuanceRequestRepository {

    private final List<IssuanceRequest> requests = new ArrayList<>();
    private final AtomicLong sequence = new AtomicLong();

    @Override
    public synchronized IssuanceRequest save(IssuanceRequest request) {
        IssuanceRequest saved = IssuanceRequest.restore(sequence.incrementAndGet(), request.orderNo(),
                request.attempt(), request.supplierOrderNo(), request.status(), request.failureReason(),
                request.requestedAt(), request.completedAt());
        requests.add(saved);
        return saved;
    }

    @Override
    public synchronized Optional<IssuanceRequest> findLatestByOrderNo(OrderNo orderNo) {
        return requests.stream().filter(r -> r.orderNo().equals(orderNo))
                .max(Comparator.comparingInt(IssuanceRequest::attempt));
    }

    @Override
    public synchronized int countByOrderNo(OrderNo orderNo) {
        return (int) requests.stream().filter(r -> r.orderNo().equals(orderNo)).count();
    }

    @Override
    public synchronized List<IssuanceRequest> findByOrderNo(OrderNo orderNo) {
        return requests.stream().filter(r -> r.orderNo().equals(orderNo))
                .sorted(Comparator.comparingInt(IssuanceRequest::attempt)).toList();
    }

    @Override
    public synchronized List<IssuanceRequest> findPending(int limit) {
        return requests.stream().filter(r -> r.status() == IssuanceStatus.PENDING)
                .sorted(Comparator.comparing(IssuanceRequest::requestedAt)).limit(limit).toList();
    }

    @Override
    public synchronized List<IssuanceRequest> findOpenRequestedBefore(Instant requestedBefore, int limit) {
        return requests.stream()
                .filter(r -> r.status().isOpen() && !r.requestedAt().isAfter(requestedBefore))
                .sorted(Comparator.comparing(IssuanceRequest::requestedAt)).limit(limit).toList();
    }

    @Override
    public synchronized boolean saveIfStatus(IssuanceRequest request, IssuanceStatus expected) {
        for (int i = 0; i < requests.size(); i++) {
            IssuanceRequest current = requests.get(i);
            if (current.id().equals(request.id())) {
                if (current.status() != expected) {
                    return false;
                }
                requests.set(i, request);
                return true;
            }
        }
        return false;
    }

    /** 테스트에서 저장된 요청을 직접 바꿔 동시 변경을 흉내 낼 때 사용 */
    public synchronized void replace(IssuanceRequest request) {
        requests.replaceAll(r -> r.id().equals(request.id()) ? request : r);
    }
}
