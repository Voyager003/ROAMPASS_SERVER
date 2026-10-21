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

    /**
     * 유심 개통(부록 A `POST /usim/activate`). 공항에서 받은 카드의 ICCID는 공급사가 이미 알고 있다고 보고,
     * 처음 요청이면 프로필을 ENABLED로 등록. 같은 ICCID 재요청은 처음 개통 시각 그대로(멱등)
     */
    public ProfileView activateUsim(String iccid, Instant now) {
        SupplierOrder order = byIccid.computeIfAbsent(iccid, id -> new SupplierOrder(newId("SUP-"), null, id, null));
        return applyStateChange(order, ProfileState.ENABLED, now);
    }

    /** 시연용: 설치·개통·삭제를 흉내 내는 상태 변경 */
    public ProfileView changeState(String iccid, ProfileState state, Instant now) {
        SupplierOrder order = byIccid.get(iccid);
        if (order == null) {
            throw new MockSupplierException(HttpStatus.NOT_FOUND, "PROFILE_NOT_FOUND", "Unknown ICCID");
        }
        return applyStateChange(order, state, now);
    }

    private ProfileView applyStateChange(SupplierOrder order, ProfileState state, Instant now) {
        SupplierOrder.StateChange change = order.changeState(state, now);
        if (change.newlyActivated()) {
            publishActivated(order, change.view());
        }
        return change.view();
    }

    /** 개통 이벤트 콜백(과제 2.3-④). 지연·중복·서명 오류는 발급 콜백과 같은 설정, 누락은 전체 누락 또는 개통만 누락 */
    private void publishActivated(SupplierOrder order, ProfileView view) {
        FaultSettings faults = faultConfig.current();
        if (faults.callbackDrop() || faults.activationCallbackDrop()) {
            return;
        }
        CallbackEvent event = new CallbackEvent(newId("sevt_"), CallbackEventType.ACTIVATED, order.supplierOrderNo(),
                order.orderNo(), view.iccid(), null, null, view.activatedAt(), Instant.now());
        deliver(new CallbackDelivery(event, !faults.invalidSignature()), faults);
    }

    private void publishCallbacks(SupplierOrder order, FaultSettings faults) {
        if (faults.callbackDrop()) {
            return;
        }
        CallbackEvent event = order.issued()
                ? new CallbackEvent(newId("sevt_"), CallbackEventType.ISSUED, order.supplierOrderNo(), order.orderNo(),
                        order.iccid(), order.activationCode(), null, null, Instant.now())
                : new CallbackEvent(newId("sevt_"), CallbackEventType.ISSUE_FAILED, order.supplierOrderNo(),
                        order.orderNo(), null, null, "INJECTED_ISSUE_FAILURE", null, Instant.now());
        deliver(new CallbackDelivery(event, !faults.invalidSignature()), faults);
    }

    private void deliver(CallbackDelivery delivery, FaultSettings faults) {
        for (int i = 0; i <= faults.callbackDuplicates(); i++) {
            callbackPublisher.publish(delivery); // 중복분도 같은 eventId
        }
    }

    private static String newId(String prefix) {
        return prefix + UUID.randomUUID().toString().replace("-", "");
    }
}
