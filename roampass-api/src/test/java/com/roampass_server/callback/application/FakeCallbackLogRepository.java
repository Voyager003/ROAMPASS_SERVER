package com.roampass_server.callback.application;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.roampass_server.callback.domain.model.CallbackLog;
import com.roampass_server.callback.domain.model.CallbackOutcome;
import com.roampass_server.callback.domain.repository.CallbackLogRepository;

/**
 * 테스트용 인메모리 CallbackLogRepository
 */
public class FakeCallbackLogRepository implements CallbackLogRepository {

    private final List<CallbackLog> logs = new ArrayList<>();

    @Override
    public synchronized boolean insertIfAbsent(CallbackLog log) {
        if (findByEventKey(log.eventKey()).isPresent()) {
            return false;
        }
        logs.add(log);
        return true;
    }

    @Override
    public synchronized void save(CallbackLog log) {
        logs.add(log);
    }

    @Override
    public synchronized void updateOutcome(String eventKey, CallbackOutcome outcome, String note) {
        CallbackLog current = findByEventKey(eventKey).orElseThrow();
        logs.set(logs.indexOf(current), CallbackLog.restore(current.id(), current.source(), current.eventKey(),
                current.signatureOk(), current.payload(), outcome, note, current.receivedAt()));
    }

    @Override
    public synchronized Optional<CallbackLog> findByEventKey(String eventKey) {
        return logs.stream().filter(l -> eventKey != null && eventKey.equals(l.eventKey())).findFirst();
    }

    public synchronized List<CallbackLog> all() {
        return List.copyOf(logs);
    }
}
