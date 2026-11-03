package com.portfolio.config;

import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import com.portfolio.dto.ErrorResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * 모든 예외를 {@link ErrorResponse} 형태의 JSON 으로 통일해 응답합니다.
 *
 * 관리자 화면은 여기서 내려준 message 를 그대로 사용자에게 보여주므로,
 * 메시지는 "무엇이 잘못됐고 어떻게 고치면 되는지" 알 수 있게 씁니다.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<ErrorResponse> handleOptimisticLock(ObjectOptimisticLockingFailureException ex) {
        log.warn("동시 수정 충돌: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ErrorResponse(409, "다른 곳에서 먼저 수정된 항목입니다. 페이지를 새로고침한 뒤 다시 시도해주세요."));
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<ErrorResponse> handleRuntime(RuntimeException ex) {
        log.error("Runtime error: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse(400, ex.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> e.getDefaultMessage())
                .reduce((a, b) -> a + ", " + b)
                .orElse("입력값을 확인해주세요");
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse(400, message));
    }

    /**
     * 본문을 읽지 못한 경우.
     *
     * 정해진 값 중에서 골라야 하는 항목(enum)에 다른 값이 오면
     * 무엇을 보낼 수 있는지 알려 줍니다. "형식이 올바르지 않습니다"만으로는
     * 어디를 고쳐야 할지 알 수 없기 때문입니다.
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleUnreadable(HttpMessageNotReadableException ex) {
        String message = "요청 형식이 올바르지 않습니다";

        if (ex.getCause() instanceof InvalidFormatException) {
            InvalidFormatException cause = (InvalidFormatException) ex.getCause();
            Class<?> target = cause.getTargetType();
            if (target != null && target.isEnum()) {
                String allowed = Arrays.stream(target.getEnumConstants())
                        .map(String::valueOf)
                        .collect(Collectors.joining(", "));
                message = String.format("'%s' 은(는) 사용할 수 없는 값입니다. %s 중 하나여야 합니다",
                        cause.getValue(), allowed);
            }
        }

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ErrorResponse(400, message));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ErrorResponse> handleMaxUpload(MaxUploadSizeExceededException ex) {
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
                .body(new ErrorResponse(413, "파일 크기가 10MB 제한을 초과했습니다"));
    }
}
