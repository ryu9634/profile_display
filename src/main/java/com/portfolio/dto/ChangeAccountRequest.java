package com.portfolio.dto;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;
import lombok.Data;

/**
 * 관리자 계정 변경 요청. 현재 비밀번호는 항상 필요하고,
 * 새 아이디와 새 비밀번호는 바꾸고 싶은 것만 보내면 됩니다.
 */
@Data
public class ChangeAccountRequest {

    @NotBlank(message = "현재 비밀번호를 입력해주세요")
    private String currentPassword;

    @Size(min = 3, max = 64, message = "아이디는 3~64자여야 합니다")
    @Pattern(regexp = "^[A-Za-z0-9._-]+$", message = "아이디는 영문, 숫자, 점(.), 밑줄(_), 하이픈(-)만 사용할 수 있습니다")
    private String newUsername;

    @Size(min = 8, max = 72, message = "새 비밀번호는 8자 이상 72자 이하여야 합니다")
    private String newPassword;
}
