package com.enterprise.ordersuite.identity.api;

import com.enterprise.ordersuite.common.util.PagedResult;
import com.enterprise.ordersuite.identity.api.dto.InviteMemberRequest;
import com.enterprise.ordersuite.identity.api.dto.MemberResponse;
import com.enterprise.ordersuite.identity.api.dto.UpdateMemberRequest;
import com.enterprise.ordersuite.identity.application.TeamService;
import com.enterprise.ordersuite.identity.domain.MembershipRole;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

// The caller's team (§5.3). Reads: OWNER and MANAGER (MANAGER covers OWNER through the
// hierarchy), or a platform admin's support read. Writes: OWNER and MANAGER, narrowed per
// target and per granted role by @teamAccess; never with the support header.
@RestController
@RequestMapping("/team/members")
public class TeamController {

    private static final String ULID = "^[0-9A-HJKMNP-TV-Z]{26}$";

    private final TeamService teamService;

    public TeamController(TeamService teamService) {
        this.teamService = teamService;
    }

    @GetMapping
    @PreAuthorize("hasRole('MANAGER') or @tenantAccess.supportRead()")
    public ResponseEntity<PagedResult<MemberResponse>> list(
            @RequestParam(required = false) MembershipRole role,
            @RequestParam(required = false) Boolean active,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @RequestParam(required = false) String sort
    ) {
        return ResponseEntity.ok(teamService.list(role, active, page, size, sort));
    }

    @GetMapping("/{memberId}")
    @PreAuthorize("hasRole('MANAGER') or @tenantAccess.supportRead()")
    public ResponseEntity<MemberResponse> get(@PathVariable @Pattern(regexp = ULID) String memberId) {
        return ResponseEntity.ok(teamService.get(memberId));
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasRole('MANAGER') and @teamAccess.canInvite(#request.role())")
    public ResponseEntity<MemberResponse> invite(@Valid @RequestBody InviteMemberRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(teamService.invite(request));
    }

    @PatchMapping(value = "/{memberId}", consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasRole('MANAGER') and @teamAccess.canUpdate(#memberId, #request.role())")
    public ResponseEntity<MemberResponse> update(
            @PathVariable @Pattern(regexp = ULID) String memberId,
            @Valid @RequestBody UpdateMemberRequest request
    ) {
        return ResponseEntity.ok(teamService.update(memberId, request));
    }

    @PostMapping("/{memberId}/deactivate")
    @PreAuthorize("hasRole('MANAGER') and @teamAccess.canActOn(#memberId)")
    public ResponseEntity<MemberResponse> deactivate(@PathVariable @Pattern(regexp = ULID) String memberId) {
        return ResponseEntity.ok(teamService.deactivate(memberId));
    }

    @PostMapping("/{memberId}/reactivate")
    @PreAuthorize("hasRole('MANAGER') and @teamAccess.canActOn(#memberId)")
    public ResponseEntity<MemberResponse> reactivate(@PathVariable @Pattern(regexp = ULID) String memberId) {
        return ResponseEntity.ok(teamService.reactivate(memberId));
    }

    @PostMapping("/{memberId}/resend-invite")
    @PreAuthorize("hasRole('MANAGER') and @teamAccess.canActOn(#memberId)")
    public ResponseEntity<Void> resendInvite(@PathVariable @Pattern(regexp = ULID) String memberId) {
        teamService.resendInvite(memberId);
        return ResponseEntity.noContent().build();
    }
}
