package com.roampass.mock.supplier.issuance;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.roampass.mock.supplier.callback.CallbackDelivery;
import com.roampass.mock.supplier.callback.CallbackEvent;
import com.roampass.mock.supplier.callback.CallbackEventType;
import com.roampass.mock.supplier.callback.CallbackPublisher;
import com.roampass.mock.supplier.fault.FaultConfig;
import com.roampass.mock.supplier.fault.FaultSettings;

/**
 * 모의 공급사의 발급 접수·콜백·프로필 조회. 상태는 메모리 보관
 * 접수는 항상 성공(202), 발급 실패는 콜백으로만 통지(실제 공급사처럼 비동기 결과)
 * 멱등 키 선점은 putIfAbsent 한 번으로 판정
 */
@Service
public class MockSupplierService {

    private final Map<String, SupplierOrder> byIdempotencyKey = new ConcurrentHashMap<>();
    private final Map<String, SupplierOrder> byIccid = new ConcurrentHashMap<>();
    private final Map<String, SupplierOrder> bySupplierOrderNo = new ConcurrentHashMap<>();
    private final CallbackPublisher callbackPublisher;
    private final FaultConfig faultConfig;
    private final IccidGenerator iccidGenerator;

    public MockSupplierService(CallbackPublisher callbackPublisher, FaultConfig faultConfig,
                               IccidGenerator iccidGenerator) {
        this.callbackPublisher = callbackPublisher;
        this.faultConfig = faultConfig;
        this.iccidGenerator = iccidGenerator;
    }

    public IssueResult issue(IssueCommand command) {
        FaultSettings faults = faultConfig.current();
        boolean fail = faultConfig.roll(faults.issueFailureRate());
        SupplierOrder created = fail
                ? new SupplierOrder(newId("SUP-"), command.orderNo(), null, null)
                : new SupplierOrder(newId("SUP-"), command.orderNo(), iccidGenerator.next(),
                        "LPA:1$smdp.example$" + newId("").substring(0, 12).toUpperCase());
        SupplierOrder existing = byIdempotencyKey.putIfAbsent(command.idempotencyKey(), created);
        if (existing != null) {
            return new IssueResult(existing.supplierOrderNo(), existing.orderNo(), "ACCEPTED");
        }
        bySupplierOrderNo.put(created.supplierOrderNo(), created);
        if (created.issued()) {
            byIccid.put(created.iccid(), created);
        }
        publishCallbacks(created, faults);
        return new IssueResult(created.supplierOrderNo(), created.orderNo(), "ACCEPTED");
    }

    public Optional<ProfileView> findProfile(String iccid) {
        return Optional.ofNullable(byIccid.get(iccid)).map(SupplierOrder::view);
    }

    /** 테스트·시연 보조: 접수 번호로 발급된 ICCID 조회 */
    public Optional<String> findIccidBySupplierOrderNo(String supplierOrderNo) {
        return Optional.ofNullable(bySupplierOrderNo.get(supplierOrderNo)).map(SupplierOrder::iccid);
    }

    /** 시연용: 설치·개통·삭제를 흉내 내는 상태 변경 */
    public ProfileView changeState(String iccid, ProfileState state, Instant now) {
        SupplierOrder order = byIccid.get(iccid);
        if (order == null) {
            throw new MockSupplierException(HttpStatus.NOT_FOUND, "PROFILE_NOT_FOUND", "Unknown ICCID");
        }
        return order.changeState(state, now);
    }

    private void publishCallbacks(SupplierOrder order, FaultSettings faults) {
        if (faults.callbackDrop()) {
            return;
        }
        CallbackEvent event = order.issued()
                ? new CallbackEvent(newId("sevt_"), CallbackEventType.ISSUED, order.supplierOrderNo(), order.orderNo(),
                        order.iccid(), order.activationCode(), null, Instant.now())
                : new CallbackEvent(newId("sevt_"), CallbackEventType.ISSUE_FAILED, order.supplierOrderNo(),
                        order.orderNo(), null, null, "INJECTED_ISSUE_FAILURE", Instant.now());
        CallbackDelivery delivery = new CallbackDelivery(event, !faults.invalidSignature());
        for (int i = 0; i <= faults.callbackDuplicates(); i++) {
            callbackPublisher.publish(delivery); // 중복분도 같은 eventId
        }
    }

    private static String newId(String prefix) {
        return prefix + UUID.randomUUID().toString().replace("-", "");
    }
}
