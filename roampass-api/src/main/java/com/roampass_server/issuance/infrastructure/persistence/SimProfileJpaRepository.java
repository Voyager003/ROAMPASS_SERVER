package com.roampass_server.issuance.infrastructure.persistence;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.roampass_server.issuance.domain.model.InstallStatus;

interface SimProfileJpaRepository extends JpaRepository<SimProfileJpaEntity, Long> {

    Optional<SimProfileJpaEntity> findByOrderNo(String orderNo);

    /**
     * 폴링 대상: 개통 전·미삭제이고 여행 시작일 창(시작일 N일 전 ~ 시작일 + 사용 일수) 안
     * 행마다 사용 일수가 달라 날짜 + 정수 연산(PostgreSQL)을 쓰는 네이티브 쿼리
     */
    @Query(value = """
            select p.* from sim_profile p join orders o on o.order_no = p.order_no
             where p.install_status in ('ISSUED', 'QR_VIEWED', 'INSTALLED')
               and p.deleted_at is null
               and o.travel_start_date <= :windowEnd
               and o.travel_start_date + p.usage_days >= :today
             order by p.issued_at
             limit :limit
            """, nativeQuery = true)
    List<SimProfileJpaEntity> findTrackable(@Param("today") LocalDate today, @Param("windowEnd") LocalDate windowEnd,
                                            @Param("limit") int limit);

    /**
     * 저장된 설치 상태가 expected일 때만 변경 반영(조건부 UPDATE), 영향 행 수 반환
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update SimProfileJpaEntity p
               set p.installStatus = :status, p.qrViewedAt = :qrViewedAt, p.installedAt = :installedAt,
                   p.activatedAt = :activatedAt, p.deletedAt = :deletedAt
             where p.id = :id and p.installStatus = :expected
            """)
    int updateIfStatus(@Param("id") Long id, @Param("expected") InstallStatus expected,
                       @Param("status") InstallStatus status, @Param("qrViewedAt") Instant qrViewedAt,
                       @Param("installedAt") Instant installedAt, @Param("activatedAt") Instant activatedAt,
                       @Param("deletedAt") Instant deletedAt);
}
