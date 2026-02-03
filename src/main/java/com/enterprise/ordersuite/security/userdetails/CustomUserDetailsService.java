package com.enterprise.ordersuite.security.userdetails;

import com.enterprise.ordersuite.identity.domain.User;
import com.enterprise.ordersuite.identity.persistence.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        User user = userRepository.findByEmailIgnoreCaseFetchingRole(email)
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));

        if (!Boolean.TRUE.equals(user.getActive())) {
            // Treat inactive users as disabled accounts, blocks authentication.
            throw new DisabledException("User account is disabled.");
        }

        return new CustomUserDetails(user);
    }
}
