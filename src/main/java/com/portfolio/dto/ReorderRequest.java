package com.portfolio.dto;

import javax.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

/**
 * 순서 변경 요청. ids 배열의 순서가 곧 화면에 표시될 순서가 됩니다.
 */
@Data
public class ReorderRequest {

    @NotEmpty(message = "정렬할 항목이 없습니다")
    private List<String> ids;
}
