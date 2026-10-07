package com.enterprise.ordersuite.identity.application;

import com.enterprise.ordersuite.identity.api.dto.MeResponse;
import com.enterprise.ordersuite.identity.api.dto.UpdateMeRequest;
import com.enterprise.ordersuite.identity.domain.Membership;
import com.enterprise.ordersuite.identity.domain.User;
import com.enterprise.ordersuite.identity.persistence.MembershipRepository;
import com.enterprise.ordersuite.storage.ObjectStorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.util.Optional;
import java.util.UUID;

// The signed-in user's own account (Tenancy & Identity, Own account; D-18 folds the profile
// into /me). The restaurant comes from the user's membership.
@Service
@RequiredArgsConstructor
public class MeService {

    private static final String WEBP = "image/webp";

    private final CurrentUserService currentUserService;
    private final MembershipRepository membershipRepository;
    private final RestaurantLookup restaurantLookup;
    private final ObjectStorageService objectStorageService;
    private final AvatarValidator avatarValidator;
    private final AvatarImageProcessor avatarImageProcessor;

    @Transactional(readOnly = true)
    public MeResponse getMe() {
        return toResponse(currentUserService.requireActiveUser());
    }

    @Transactional
    public MeResponse updateMe(UpdateMeRequest request) {
        User user = currentUserService.requireActiveUser();

        if (request.getFirstName() != null) {
            user.setFirstName(request.getFirstName().trim());
        }
        if (request.getLastName() != null) {
            user.setLastName(request.getLastName().trim());
        }
        if (request.isPhoneSent()) {
            user.setPhone(request.getPhone());
        }

        return toResponse(user);
    }

    // Stored as WebP under the restaurant's prefix (§5.2), or the user's own prefix for the
    // platform admin. The replaced object is deleted only after commit; if the transaction
    // rolls back, the new object is deleted instead, so the row never points at a missing key.
    @Transactional
    public MeResponse uploadAvatar(MultipartFile file) {
        User user = currentUserService.requireActiveUser();
        avatarValidator.validate(file);
        byte[] webp = avatarImageProcessor.convertToWebp(file);

        String proposedKey = avatarPrefix(user) + "avatar-" + UUID.randomUUID() + ".webp";
        // The key the storage layer reports is the canonical one.
        String storedKey = objectStorageService.upload(proposedKey, new ByteArrayInputStream(webp), webp.length, WEBP);

        String previousKey = user.getAvatarKey();
        user.setAvatarKey(storedKey);
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status == STATUS_COMMITTED) {
                    if (previousKey != null) {
                        objectStorageService.delete(previousKey);
                    }
                } else {
                    objectStorageService.delete(storedKey);
                }
            }
        });

        return toResponse(user);
    }

    @Transactional
    public void deleteAvatar() {
        User user = currentUserService.requireActiveUser();
        String key = user.getAvatarKey();
        if (key == null) {
            return;
        }

        user.setAvatarKey(null);
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                objectStorageService.delete(key);
            }
        });
    }

    private String avatarPrefix(User user) {
        return membershipRepository.findByUserId(user.getId())
                .map(m -> "restaurants/" + m.getRestaurantId() + "/users/" + user.getId() + "/")
                .orElse("users/" + user.getId() + "/");
    }

    private MeResponse toResponse(User user) {
        Optional<Membership> membership = membershipRepository.findByUserId(user.getId());

        return new MeResponse(
                user.getId(),
                user.getEmail(),
                user.getFirstName(),
                user.getLastName(),
                user.getPhone(),
                user.getAvatarKey() == null ? null : objectStorageService.getUrl(user.getAvatarKey()),
                user.isPlatformAdmin(),
                membership.map(m -> new MeResponse.MembershipView(
                        m.getRestaurantId(), m.getRole().name(), m.getCreatedAt())).orElse(null),
                membership.flatMap(m -> restaurantLookup.findSummary(m.getRestaurantId())).orElse(null)
        );
    }
}
