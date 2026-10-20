package com.roampass_server.common.domain;

/**
 * Subject of a state change, recorded in status history: {@code USER:{id}}, {@code ADMIN:{id}},
 * {@code SYSTEM}, {@code PG} or {@code SUPPLIER}. Users and operators carry their identifier;
 * the others are identified by type alone.
 */
public record Actor(ActorType type, String id) {

    private static final String CODE = "INVALID_ACTOR";
    private static final String SEPARATOR = ":";

    public Actor {
        if (type == null) {
            throw new InvalidValueException(CODE, "Actor type is required");
        }
        switch (type) {
            case USER -> UserId.of(id);   // validates the identifier format
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

    /** Restores an actor from its {@link #asString()} form. */
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
