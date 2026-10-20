package com.roampass_server.history.domain.model;

import com.roampass_server.common.domain.InvalidValueException;
import com.roampass_server.common.domain.OrderNo;

/**
 * Which aggregate a history entry is about: its kind plus its key
 * (order number, payment idempotency key, refund id, ...). The key is a plain string because the history
 * domain does not import the other domains' types.
 */
public record HistoryTarget(TargetType type, String key) {

    public HistoryTarget {
        if (type == null || key == null || key.isBlank()) {
            throw new InvalidValueException("INVALID_HISTORY", "History target needs a type and a key");
        }
    }

    public static HistoryTarget of(TargetType type, String key) {
        return new HistoryTarget(type, key);
    }

    public static HistoryTarget order(OrderNo orderNo) {
        return new HistoryTarget(TargetType.ORDER, orderNo.value());
    }
}
