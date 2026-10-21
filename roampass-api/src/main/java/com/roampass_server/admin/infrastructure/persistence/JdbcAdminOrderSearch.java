package com.roampass_server.admin.infrastructure.persistence;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import com.roampass_server.admin.application.AdminOrderSearch;
import com.roampass_server.admin.application.AdminOrderSearchCondition;
import com.roampass_server.common.domain.OrderNo;
import com.roampass_server.common.domain.PageResult;
import com.roampass_server.issuance.domain.model.InstallStatus;

/**
 * 어드민 주문 검색의 SQL 구현. 주어진 조건만 WHERE에 붙임(비어 있는 조건은 쿼리에서 빠짐)
 * <ul>
 *   <li>결제 상태: 주문의 가장 최근 결제 시도(requested_at, id 내림차순 첫 행)의 상태</li>
 *   <li>설치 상태: sim_profile.install_status. 만료는 저장하지 않고 조회 시점에 판정하므로
 *       EXPIRED = ACTIVE이면서 개통 + 24시간 × 사용 일수가 지난 것, ACTIVE = 아직 안 지난 것</li>
 * </ul>
 */
public class JdbcAdminOrderSearch implements AdminOrderSearch {

    private static final String USAGE_END = "sp.activated_at + sp.usage_days * interval '24 hours'";

    private final NamedParameterJdbcTemplate jdbc;

    public JdbcAdminOrderSearch(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public PageResult<OrderNo> search(AdminOrderSearchCondition c, Instant now, int page, int size) {
        MapSqlParameterSource params = new MapSqlParameterSource();
        List<String> where = new ArrayList<>();
        if (c.status() != null) {
            where.add("o.status = :status");
            params.addValue("status", c.status().name());
        }
        if (c.simType() != null) {
            where.add("o.sim_type = :simType");
            params.addValue("simType", c.simType().name());
        }
        if (c.createdFrom() != null) {
            where.add("o.created_at >= :createdFrom");
            params.addValue("createdFrom", Timestamp.from(c.createdFrom()));
        }
        if (c.createdTo() != null) {
            where.add("o.created_at < :createdTo");
            params.addValue("createdTo", Timestamp.from(c.createdTo()));
        }
        if (c.paymentStatus() != null) {
            where.add("""
                    (select p.status from payment p where p.order_no = o.order_no
                      order by p.requested_at desc, p.id desc limit 1) = :paymentStatus""");
            params.addValue("paymentStatus", c.paymentStatus().name());
        }
        if (c.installStatus() != null) {
            where.add("exists (select 1 from sim_profile sp where sp.order_no = o.order_no and "
                    + installPredicate(c.installStatus()) + ")");
            params.addValue("installStatus", c.installStatus().name());
            params.addValue("now", Timestamp.from(now));
        }
        String filter = where.isEmpty() ? "" : " where " + String.join(" and ", where);

        Long total = jdbc.queryForObject("select count(*) from orders o" + filter, params, Long.class);
        params.addValue("limit", size);
        params.addValue("offset", (long) page * size);
        List<OrderNo> content = jdbc.query(
                "select o.order_no from orders o" + filter + " order by o.created_at desc, o.id desc limit :limit offset :offset",
                params, (rs, rowNum) -> OrderNo.of(rs.getString("order_no")));
        return new PageResult<>(content, page, size, total == null ? 0 : total);
    }

    private static String installPredicate(InstallStatus status) {
        return switch (status) {
            case EXPIRED -> "sp.install_status = 'ACTIVE' and " + USAGE_END + " <= :now";
            case ACTIVE -> "sp.install_status = :installStatus and " + USAGE_END + " > :now";
            default -> "sp.install_status = :installStatus";
        };
    }
}
