// 파일 경로: src/main/java/com/gwonsystem/backend/dto/RoleUpdateRequest.java
package com.gwonsystem.backend.dto;

import com.gwonsystem.backend.entity.Role;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RoleUpdateRequest {
    @NotNull(message = "부여할 권한 등급을 지정해야 합니다.")
    private Role targetRole;
}