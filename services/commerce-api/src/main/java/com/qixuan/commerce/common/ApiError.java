package com.qixuan.commerce.common;

import java.time.Instant;

public record ApiError(
        String code,
        String message,
        String path,
        Instant timeStamp)

{
}
