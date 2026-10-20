package com.roampass_server.plan.infrastructure.persistence;

import com.roampass_server.common.domain.Currency;
import com.roampass_server.common.domain.Money;
import com.roampass_server.common.domain.SimType;
import com.roampass_server.plan.domain.model.Plan;
import com.roampass_server.plan.domain.model.PlanSpec;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * plan 테이블 매핑. 요금제는 마이그레이션 시드로만 들어가므로 읽기 전용(도메인 → 엔티티 변환 없음)
 */
@Entity
@Table(name = "plan")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PlanJpaEntity {

    @Id
    private Long id;

    @Column(name = "country_code", nullable = false)
    private String countryCode;

    @Column(nullable = false)
    private int days;

    @Column(name = "data_mb", nullable = false)
    private int dataMb;

    @Enumerated(EnumType.STRING)
    @Column(name = "sim_type", nullable = false)
    private SimType simType;

    @Column(name = "price_amount", nullable = false)
    private long priceAmount;

    @Enumerated(EnumType.STRING)
    @Column(name = "price_currency", nullable = false)
    private Currency priceCurrency;

    public Plan toDomain() {
        return Plan.of(id, PlanSpec.of(countryCode, days, dataMb), simType, Money.of(priceAmount, priceCurrency));
    }
}
