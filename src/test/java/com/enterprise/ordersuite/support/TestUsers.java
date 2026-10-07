package com.enterprise.ordersuite.support;

import com.enterprise.ordersuite.identity.domain.Membership;
import com.enterprise.ordersuite.identity.domain.MembershipRole;
import com.enterprise.ordersuite.identity.domain.User;
import com.enterprise.ordersuite.identity.persistence.MembershipRepository;
import com.enterprise.ordersuite.identity.persistence.UserRepository;
import com.enterprise.ordersuite.restaurants.domain.Restaurant;
import com.enterprise.ordersuite.restaurants.persistence.RestaurantRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.UUID;

// Creates users straight in the database. Signup is invite-only (Tenancy & Identity D-5), so
// integration tests no longer have a public endpoint to make one.
@Component
public class TestUsers {

  private final UserRepository userRepository;
  private final RestaurantRepository restaurantRepository;
  private final MembershipRepository membershipRepository;
  private final PasswordEncoder passwordEncoder;

  public TestUsers(
    UserRepository userRepository,
    RestaurantRepository restaurantRepository,
    MembershipRepository membershipRepository,
    PasswordEncoder passwordEncoder
  ) {
    this.userRepository = userRepository;
    this.restaurantRepository = restaurantRepository;
    this.membershipRepository = membershipRepository;
    this.passwordEncoder = passwordEncoder;
  }

  // A member of a restaurant of their own.
  public User member(String email, String rawPassword, MembershipRole role) {
    User user = save(email, rawPassword, false);
    Restaurant restaurant = restaurantRepository.save(
      new Restaurant("Test Restaurant", "r-" + UUID.randomUUID(), "America/Sao_Paulo"));
    membershipRepository.save(new Membership(restaurant.getId(), user, role));
    return user;
  }

  public User owner(String email, String rawPassword) {
    return member(email, rawPassword, MembershipRole.OWNER);
  }

  public User platformAdmin(String email, String rawPassword) {
    return save(email, rawPassword, true);
  }

  private User save(String email, String rawPassword, boolean platformAdmin) {
    User user = new User();
    user.setFirstName("Test");
    user.setLastName("User");
    user.setEmail(email.trim().toLowerCase(Locale.ROOT));
    user.setPassword(passwordEncoder.encode(rawPassword));
    user.setPlatformAdmin(platformAdmin);
    user.setActive(true);
    return userRepository.save(user);
  }
}
