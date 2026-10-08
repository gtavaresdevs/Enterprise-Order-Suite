package com.enterprise.ordersuite.architecture;

import com.enterprise.ordersuite.common.tenancy.TenantUnscoped;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.domain.JavaParameterizedType;
import com.tngtech.archunit.core.domain.JavaType;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.NoRepositoryBean;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// Tenancy & Identity §5.1.3, acceptance 12. An entity is restaurant-owned when it has a
// restaurantId field. Every method its repository declares must take the restaurant id: a
// derived query naming RestaurantId, or a @Param("restaurantId") parameter. A method that
// cannot (the lookup that finds the tenant) says why with @TenantUnscoped. Inherited
// JpaRepository methods are not checked.
class TenantRepositoryRuleTest {

  static final ArchRule RULE = classes()
    .that().areInterfaces().and().areAssignableTo(Repository.class)
    .should(scopeEveryMethodOfARestaurantOwnedEntityByRestaurantId());

  @Test
  void productionRepositories_followTheTenantRule() {
    JavaClasses production = new ClassFileImporter()
      .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
      .importPackages("com.enterprise.ordersuite");

    assertThat(production.stream().filter(c -> c.getSimpleName().equals("MembershipRepository")))
      .as("the rule must see the repositories, or it proves nothing")
      .isNotEmpty();
    RULE.check(production);
  }

  @Test
  void finderWithoutRestaurantId_failsTheRule() {
    JavaClasses fixture = new ClassFileImporter().importClasses(UnscopedRepository.class, OwnedRow.class);

    assertThatThrownBy(() -> RULE.check(fixture))
      .isInstanceOf(AssertionError.class)
      .hasMessageContaining("findByName");
  }

  @Test
  void scopedOrExemptedFinders_passTheRule() {
    JavaClasses fixture = new ClassFileImporter().importClasses(ScopedRepository.class, OwnedRow.class);

    RULE.check(fixture);
  }

  private static ArchCondition<JavaClass> scopeEveryMethodOfARestaurantOwnedEntityByRestaurantId() {
    return new ArchCondition<>("scope every method of a restaurant-owned entity by restaurant id") {
      @Override
      public void check(JavaClass repository, ConditionEvents events) {
        Optional<JavaClass> entity = entityOf(repository);
        if (entity.isEmpty() || !isRestaurantOwned(entity.get())) {
          return;
        }
        for (JavaMethod method : repository.getMethods()) {
          if (!method.isAnnotatedWith(TenantUnscoped.class) && !takesRestaurantId(method)) {
            events.add(SimpleConditionEvent.violated(method,
              method.getFullName() + " does not take a restaurant id and is not @TenantUnscoped"));
          }
        }
      }
    };
  }

  private static Optional<JavaClass> entityOf(JavaClass repository) {
    for (JavaType type : repository.getInterfaces()) {
      if (type instanceof JavaParameterizedType parameterized
        && parameterized.toErasure().isAssignableTo(Repository.class)
        && !parameterized.getActualTypeArguments().isEmpty()) {
        return Optional.of(parameterized.getActualTypeArguments().get(0).toErasure());
      }
    }
    return Optional.empty();
  }

  private static boolean isRestaurantOwned(JavaClass entity) {
    return entity.getAllFields().stream().anyMatch(field -> field.getName().equals("restaurantId"));
  }

  private static boolean takesRestaurantId(JavaMethod method) {
    return method.getName().contains("RestaurantId")
      || method.getParameters().stream().anyMatch(parameter -> parameter
        .tryGetAnnotationOfType(Param.class)
        .map(param -> param.value().equals("restaurantId"))
        .orElse(false));
  }

  static class OwnedRow {
    String id;
    String restaurantId;
    String name;
  }

  @NoRepositoryBean
  interface UnscopedRepository extends Repository<OwnedRow, String> {
    List<OwnedRow> findByName(String name);
  }

  @NoRepositoryBean
  interface ScopedRepository extends Repository<OwnedRow, String> {
    List<OwnedRow> findByRestaurantIdAndName(String restaurantId, String name);

    @Query("select r from OwnedRow r where r.restaurantId = :restaurantId")
    List<OwnedRow> listFor(@Param("restaurantId") String restaurantId);

    @TenantUnscoped("fixture: shows the exemption")
    Optional<OwnedRow> findByName(String name);
  }
}
