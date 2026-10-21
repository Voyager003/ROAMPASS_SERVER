package com.roampass_server.common.domain;

import java.util.List;
import java.util.function.Function;

/**
 * 페이지 조회 결과. page는 0부터
 */
public record PageResult<T>(List<T> content, int page, int size, long totalElements) {

    public <R> PageResult<R> map(Function<T, R> mapper) {
        return new PageResult<>(content.stream().map(mapper).toList(), page, size, totalElements);
    }
}
