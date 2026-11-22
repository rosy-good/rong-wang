package com.rongwang.common.api;

import java.util.List;

public record PageResponse<T>(List<T> items, long total) { }
