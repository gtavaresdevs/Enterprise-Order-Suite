package com.enterprise.ordersuite.identity.application;

import com.enterprise.ordersuite.common.errors.InvalidInputException;
import com.enterprise.ordersuite.common.tenancy.TenantContextHolder;
import com.enterprise.ordersuite.common.util.PagedResult;
import com.enterprise.ordersuite.identity.api.dto.InviteMemberRequest;
import com.enterprise.ordersuite.identity.api.dto.MemberResponse;
import com.enterprise.ordersuite.identity.api.dto.UpdateMemberRequest;
import com.enterprise.ordersuite.identity.domain.EmailTakenException;
import com.enterprise.ordersuite.identity.domain.IdentityAuditEventType;
import com.enterprise.ordersuite.identity.domain.InvitationNotPendingException;
import com.enterprise.ordersuite.identity.domain.LastOwnerException;
import com.enterprise.ordersuite.identity.domain.MemberNotFoundException;
import com.enterprise.ordersuite.identity.domain.Membership;
import com.enterprise.ordersuite.identity.domain.MembershipRole;
import com.enterprise.ordersuite.identity.domain.SelfActionNotAllowedException;
import com.enterprise.ordersuite.identity.domain.User;
import com.enterprise.ordersuite.identity.persistence.MembershipRepository;
import com.enterprise.ordersuite.identity.persistence.UserRepository;
import com.enterprise.ordersuite.storage.ObjectStorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.JpaSort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.Map;

/**
 * The caller's team (Tenancy & Identity, Invites and Membership changes). The restaurant
 * always comes from the tenant context, and every member is looked up within it, so a member
 * of another restaurant is 404. Who may do what is decided before this service runs, in
 * @PreAuthorize (TeamAccess); this class holds the business rules (D-16) only.
 */
@Service
@RequiredArgsConstructor
public class TeamService {

    // Default order (x-default-order): OWNER, MANAGER, STAFF, then lastName, then id.
    // In parentheses, so Spring Data takes it as an expression and does not prefix the alias.
    private static final String ROLE_RANK = "(case m.role"
            + " when com.enterprise.ordersuite.identity.domain.MembershipRole.OWNER then 0"
            + " when com.enterprise.ordersuite.identity.domain.MembershipRole.MANAGER then 1"
            + " else 2 end)";

    private final MembershipRepository membershipRepository;
    private final UserRepository userRepository;
    private final CurrentUserService currentUserService;
    private final IdentityAuditService identityAuditService;
    private final MemberInvitations memberInvitations;
    private final MemberSessions memberSessions;
    private final ObjectStorageService objectStorageService;

    @Transactional(readOnly = true)
    public PagedResult<MemberResponse> list(MembershipRole role, Boolean active, int page, int size, String sort) {
        return listIn(TenantContextHolder.requireRestaurantId(), role, active, page, size, sort);
    }

    // The platform admin's view of a restaurant's team (GET /platform/restaurants/{id}/members):
    // the restaurant comes from the path (D-6), and the caller has checked that it exists.
    @Transactional(readOnly = true)
    public PagedResult<MemberResponse> listIn(
            String restaurantId, MembershipRole role, Boolean active, int page, int size, String sort) {
        Page<Membership> members = membershipRepository.findMembers(
                restaurantId, role, active, PageRequest.of(page, size, order(sort)));
        return PagedResult.of(members, members.map(this::toResponse).getContent());
    }

    @Transactional(readOnly = true)
    public MemberResponse get(String memberId) {
        return toResponse(requireMember(memberId));
    }

    @Transactional
    public MemberResponse invite(InviteMemberRequest request) {
        return inviteTo(TenantContextHolder.requireRestaurantId(), request);
    }

    // Invites 1 and 2: a user with no password and the membership, in the caller's
    // transaction; the email goes out after commit. The platform paths (restaurant creation,
    // POST /platform/restaurants/{id}/members) name the restaurant themselves.
    @Transactional
    public MemberResponse inviteTo(String restaurantId, InviteMemberRequest request) {
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new EmailTakenException();
        }

        User user = new User();
        user.setEmail(email);
        user.setFirstName(request.firstName().trim());
        user.setLastName(request.lastName().trim());
        user.setActive(true);
        user = userRepository.save(user);
        Membership membership = membershipRepository.save(new Membership(restaurantId, user, request.role()));

        memberInvitations.sendInvite(user);
        record(IdentityAuditEventType.MEMBER_INVITED, restaurantId, user, Map.of("role", request.role().name()));
        return toResponse(membership);
    }

    @Transactional
    public MemberResponse update(String memberId, UpdateMemberRequest request) {
        Membership membership = requireMember(memberId);
        User user = membership.getUser();

        if (request.role() != null && request.role() != membership.getRole()) {
            MembershipRole from = membership.getRole();
            if (from == MembershipRole.OWNER) {
                requireAnotherActiveOwner(membership);
            }
            requireNotSelf(user);

            membership.setRole(request.role());
            memberSessions.revokeAllFor(user);
            record(IdentityAuditEventType.ROLE_CHANGED, membership.getRestaurantId(), user,
                    Map.of("from", from.name(), "to", request.role().name()));
        }
        if (request.firstName() != null) {
            user.setFirstName(request.firstName().trim());
        }
        if (request.lastName() != null) {
            user.setLastName(request.lastName().trim());
        }
        return toResponse(membership);
    }

    // Repeating it is a no-op (API conventions §11.2).
    @Transactional
    public MemberResponse deactivate(String memberId) {
        Membership membership = requireMember(memberId);
        User user = membership.getUser();
        if (!Boolean.TRUE.equals(user.getActive())) {
            return toResponse(membership);
        }
        if (membership.getRole() == MembershipRole.OWNER) {
            requireAnotherActiveOwner(membership);
        }
        requireNotSelf(user);

        user.setActive(false);
        memberSessions.revokeAllFor(user);
        record(IdentityAuditEventType.MEMBER_DEACTIVATED, membership.getRestaurantId(), user, Map.of());
        return toResponse(membership);
    }

    @Transactional
    public MemberResponse reactivate(String memberId) {
        Membership membership = requireMember(memberId);
        User user = membership.getUser();
        if (Boolean.TRUE.equals(user.getActive())) {
            return toResponse(membership);
        }

        user.setActive(true);
        record(IdentityAuditEventType.MEMBER_REACTIVATED, membership.getRestaurantId(), user, Map.of());
        return toResponse(membership);
    }

    // Invites 3. A deactivated invitee is refused too: /auth/reset-password would reject the
    // link (Gabriel, Build 1 slice 6).
    @Transactional
    public void resendInvite(String memberId) {
        Membership membership = requireMember(memberId);
        User user = membership.getUser();
        if (user.getPassword() != null || !Boolean.TRUE.equals(user.getActive())) {
            throw new InvitationNotPendingException();
        }

        memberInvitations.sendInvite(user);
        record(IdentityAuditEventType.INVITE_RESENT, membership.getRestaurantId(), user, Map.of());
    }

    private Membership requireMember(String memberId) {
        return membershipRepository.findMember(TenantContextHolder.requireRestaurantId(), memberId)
                .orElseThrow(MemberNotFoundException::new);
    }

    // D-16, checked before the self rule so the only owner acting on themselves hears why
    // (Gabriel, Build 1 slice 6). Called only when the target is an owner losing the role or
    // being deactivated.
    private void requireAnotherActiveOwner(Membership target) {
        if (!Boolean.TRUE.equals(target.getUser().getActive())) {
            return;
        }
        String restaurantId = target.getRestaurantId();
        membershipRepository.lockOwners(restaurantId);
        if (membershipRepository.countActiveOwners(restaurantId) <= 1) {
            throw new LastOwnerException();
        }
    }

    private void requireNotSelf(User target) {
        if (target.getId().equals(currentUserService.getUserId())) {
            throw new SelfActionNotAllowedException();
        }
    }

    private void record(IdentityAuditEventType type, String restaurantId, User target, Map<String, Object> details) {
        identityAuditService.recordEvent(type, restaurantId, currentUserService.getUserId(), target.getId(), details);
    }

    // sort=<field>,<asc|desc> for the draft's x-sort fields; anything else is INVALID_INPUT.
    private static Sort order(String sort) {
        if (sort == null || sort.isBlank()) {
            return JpaSort.unsafe(Sort.Direction.ASC, ROLE_RANK)
                    .andUnsafe(Sort.Direction.ASC, "u.lastName", "u.id");
        }
        String[] parts = sort.split(",", -1);
        String field = parts[0].trim();
        Sort.Direction direction = parts.length == 1 ? Sort.Direction.ASC : direction(parts);
        String path = switch (field) {
            case "lastName" -> "u.lastName";
            case "createdAt" -> "m.createdAt";
            default -> throw new InvalidInputException("sort: unknown field " + field);
        };
        return JpaSort.unsafe(direction, path).andUnsafe(Sort.Direction.ASC, "u.id");
    }

    private static Sort.Direction direction(String[] parts) {
        if (parts.length != 2) {
            throw new InvalidInputException("sort: expected <field>,<asc|desc>");
        }
        return switch (parts[1].trim().toLowerCase(Locale.ROOT)) {
            case "asc" -> Sort.Direction.ASC;
            case "desc" -> Sort.Direction.DESC;
            default -> throw new InvalidInputException("sort: direction must be asc or desc");
        };
    }

    private MemberResponse toResponse(Membership membership) {
        User user = membership.getUser();
        return new MemberResponse(
                user.getId(),
                user.getEmail(),
                user.getFirstName(),
                user.getLastName(),
                user.getPhone(),
                user.getAvatarKey() == null ? null : objectStorageService.getUrl(user.getAvatarKey()),
                membership.getRole().name(),
                Boolean.TRUE.equals(user.getActive()),
                user.getPassword() == null,
                membership.getCreatedAt()
        );
    }
}
