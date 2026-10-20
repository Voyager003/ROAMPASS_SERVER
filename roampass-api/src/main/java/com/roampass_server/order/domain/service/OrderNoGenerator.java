package com.roampass_server.order.domain.service;

import java.time.Instant;

import com.roampass_server.order.domain.model.OrderNo;

/**
 * Port for issuing new order numbers. Tests can supply fixed numbers.
 */
public interface OrderNoGenerator {

    OrderNo next(Instant now);
}
