package com.roampass_server.common.domain;

import com.roampass_server.common.domain.exception.InvalidValueException;

/**
 * 상태를 바꾼 주체, 상태 변경 이력에 기록: {@code USER:{id}}, {@code ADMIN:{id}},
 * {@code SYSTEM}, {@code PG}, {@code SUPPLIER}. 사용자·운영자는 식별자 보유,
 * 나머지는 종류만으로 구분
 */
public record Actor(ActorType type, String id) {

    private static final String CODE = "INVALID_ACTOR";
    private static final String SEPARATOR = ":";

    public Actor {
        if (type == null) {
            throw new InvalidValueException(CODE, "Actor type is required");
        }
        switch (type) {
            case USER -> UserId.of(id);   // 식별자 형식 검증
            case ADMIN -> AdminId.of(id);
            default -> {
                if (id != null) {
                    throw new InvalidValueException(CODE, type + " actor has no identifier");
                }
            }
        }
    }

    public static Actor user(UserId userId) {
        return new Actor(ActorType.USER, userId.value());
    }

    public static Actor admin(AdminId adminId) {
        return new Actor(ActorType.ADMIN, adminId.value());
    }

    public static Actor system() {
        return new Actor(ActorType.SYSTEM, null);
    }

    public static Actor pg() {
        return new Actor(ActorType.PG, null);
    }

    public static Actor supplier() {
        return new Actor(ActorType.SUPPLIER, null);
    }

    /** {@link #asString()} 형태의 문자열에서 Actor 복원 */
    public static Actor parse(String raw) {
        if (raw == null || raw.isEmpty()) {
            throw new InvalidValueException(CODE, "Actor must not be empty");
        }
        int separator = raw.indexOf(SEPARATOR);
        String typeName = separator < 0 ? raw : raw.substring(0, separator);
        String id = separator < 0 ? null : raw.substring(separator + 1);
        ActorType type;
        try {
            type = ActorType.valueOf(typeName);
        } catch (IllegalArgumentException unknown) {
            throw new InvalidValueException(CODE, "Unknown actor type");
        }
        if ((type == ActorType.USER || type == ActorType.ADMIN) && id == null) {
            throw new InvalidValueException(CODE, type + " actor needs an identifier");
        }
        return new Actor(type, id);
    }

    public String asString() {
        return id == null ? type.name() : type.name() + SEPARATOR + id;
    }

    public boolean isAdmin() {
        return type == ActorType.ADMIN;
    }
}
