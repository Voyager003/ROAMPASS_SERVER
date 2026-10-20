package com.roampass_server.payment.domain.model;

import java.util.regex.Pattern;

import com.roampass_server.common.domain.InvalidValueException;

/**
 * Transaction id assigned by the PG on approval. Needed for cancels and status lookups.
 * The format is the PG's, so only "non-blank, no whitespace, reasonable length" is enforced.
 */
public record PgTid(String value) {

    private static final Pattern NO_WHITESPACE = Pattern.compile("\\S{1,100}");

    public PgTid {
        if (value == null || !NO_WHITESPACE.matcher(value).matches()) {
            throw new InvalidValueException("INVALID_PG_TID", "PG transaction id must be 1-100 non-whitespace characters");
        }
    }

    public static PgTid of(String value) {
        return new PgTid(value);
    }
}
