package com.fairvalue.web

import com.fairvalue.dto.UpdateRoleRequest
import com.fairvalue.dto.UserListResponse
import com.fairvalue.dto.UserWrapper
import com.fairvalue.security.AuthPrincipal
import com.fairvalue.service.UserAdminService
import jakarta.validation.Valid
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController

/**
 * openapi: GET /admin/users, PATCH /admin/users/{id} (ORG_ADMIN).
 * 조직 격리: org_id 는 경로/본문이 아니라 @AuthenticationPrincipal(JWT)에서만 읽는다.
 */
@RestController
class AdminUserController(private val userAdminService: UserAdminService) {

    @GetMapping("/admin/users")
    fun listUsers(@AuthenticationPrincipal caller: AuthPrincipal): UserListResponse =
        UserListResponse(items = userAdminService.listUsers(caller))

    @PatchMapping("/admin/users/{id}")
    fun updateRole(
        @AuthenticationPrincipal caller: AuthPrincipal,
        @PathVariable id: Long,
        @Valid @RequestBody req: UpdateRoleRequest,
    ): UserWrapper = UserWrapper(user = userAdminService.updateRole(caller, id, req))
}
