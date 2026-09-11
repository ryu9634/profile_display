package com.portfolio.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 오류 응답 형식. 화면은 message 를 그대로 사용자에게 보여줍니다.
 */
@Data
@AllArgsConstructor
public class ErrorResponse {
    private int status;
    private String message;
    private long timestamp;

    public ErrorResponse(int status, String message) {
        this.status = status;
        this.message = message;
        this.timestamp = System.currentTimeMillis();
    }
}
