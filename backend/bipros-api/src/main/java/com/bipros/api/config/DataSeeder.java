package com.bipros.api.config;

import com.bipros.admin.domain.model.Currency;
import com.bipros.admin.domain.model.GlobalSetting;
import com.bipros.admin.domain.repository.CurrencyRepository;
import com.bipros.admin.domain.repository.GlobalSettingRepository;
import com.bipros.calendar.domain.model.Calendar;
import com.bipros.calendar.domain.model.CalendarType;
import com.bipros.calendar.domain.model.CalendarWorkWeek;
import com.bipros.calendar.domain.model.DayType;
import com.bipros.calendar.domain.repository.CalendarRepository;
import com.bipros.calendar.domain.repository.CalendarWorkWeekRepository;
import com.bipros.security.domain.model.Role;
import com.bipros.security.domain.model.User;
import com.bipros.security.domain.model.UserRole;
import com.bipros.security.domain.repository.RoleRepository;
import com.bipros.security.domain.repository.UserRepository;
import com.bipros.security.domain.repository.UserRoleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalTime;

@Slf4j
@Component
@Profile("dev")
@Order(100)
@RequiredArgsConstructor
public class DataSeeder implements CommandLineRunner {

  private final RoleRepository roleRepository;
  private final UserRepository userRepository;
  private final UserRoleRepository userRoleRepository;
  private final CalendarRepository calendarRepository;
  private final CalendarWorkWeekRepository calendarWorkWeekRepository;
  private final CurrencyRepository currencyRepository;
  private final GlobalSettingRepository globalSettingRepository;
  private final PasswordEncoder passwordEncoder;

  @Value("${bipros.admin.username:admin}")
  private String adminUsername;

  @Value("${bipros.admin.password:admin123}")
  private String adminPassword;

  @Value("${bipros.admin.email:admin@bipros.local}")
  private String adminEmail;

  @Override
  @Transactional
  public void run(String... args) throws Exception {
    // Check if data already seeded by checking role count
    if (roleRepository.count() > 0) {
      log.info("Data already seeded, skipping bulk initialization");
      // Still ensure admin has ADMIN role (idempotent self-heal for missing association)
      ensureAdminHasAdminRole();
      return;
    }

    log.info("Starting data seeding...");

    seedRoles();
    seedAdminUser();
    seedGlobalCalendar();
    seedBaseCurrency();
    seedGlobalSettings();

    log.info("Data seeding completed successfully");
  }

  private void seedRoles() {
    log.debug("Seeding roles");

    String[] roleNames = {"ADMIN", "PROJECT_MANAGER", "SCHEDULER", "RESOURCE_MANAGER", "VIEWER"};
    String[] roleDescriptions = {
      "Administrator with full system access",
      "Project Manager with project control",
      "Scheduler for task scheduling",
      "Resource Manager for resource allocation",
      "Viewer with read-only access"
    };

    for (int i = 0; i < roleNames.length; i++) {
      Role role = new Role(roleNames[i], roleDescriptions[i]);
      roleRepository.save(role);
      log.debug("Created role: {}", roleNames[i]);
    }
    log.info("Seeded {} roles", roleNames.length);
  }

  private void seedAdminUser() {
    log.debug("Seeding admin user");

    // Create admin user
    User adminUser = new User(adminUsername, adminEmail, passwordEncoder.encode(adminPassword));
    adminUser.setFirstName("System");
    adminUser.setLastName("Administrator");
    adminUser.setEnabled(true);
    adminUser.setAccountLocked(false);

    User savedUser = userRepository.save(adminUser);
    log.debug("Created admin user: {}", savedUser.getUsername());

    assignAdminRole(savedUser);
    log.info("Seeded admin user with ADMIN role");
  }

  /**
   * Idempotent: ensures the seeded admin user exists and has the ADMIN role linked via
   * {@code user_roles}. Safe to call on every startup even if the user/role already exist.
   */
  private void ensureAdminHasAdminRole() {
    userRepository
        .findByUsername(adminUsername)
        .ifPresent(
            adminUser -> {
              Role adminRole = roleRepository.findByName("ADMIN").orElse(null);
              if (adminRole == null) {
                log.warn("ADMIN role missing while attempting to self-heal admin user mapping");
                return;
              }
              if (!userRoleRepository.existsByUserIdAndRoleId(adminUser.getId(), adminRole.getId())) {
                UserRole userRole = new UserRole(adminUser.getId(), adminRole.getId());
                userRoleRepository.save(userRole);
                log.info("Self-heal: linked ADMIN role to existing admin user '{}'", adminUsername);
              }
            });
  }

  private void assignAdminRole(User adminUser) {
    Role adminRole =
        roleRepository
            .findByName("ADMIN")
            .orElseThrow(() -> new RuntimeException("ADMIN role not found"));

    if (userRoleRepository.existsByUserIdAndRoleId(adminUser.getId(), adminRole.getId())) {
      return;
    }

    // Persist UserRole explicitly via its own repository — the @OneToMany on User.roles
    // is not cascaded, so saving the User alone will NOT insert the user_roles row.
    UserRole userRole = new UserRole(adminUser.getId(), adminRole.getId());
    userRoleRepository.save(userRole);
    adminUser.getRoles().add(userRole);
  }

  private void seedGlobalCalendar() {
    log.debug("Seeding global calendar");

    Calendar globalCalendar =
        Calendar.builder()
            .name("Standard")
            .description("Default global calendar with 5-day work week")
            .calendarType(CalendarType.GLOBAL)
            .isDefault(true)
            .standardWorkHoursPerDay(8.0)
            .standardWorkDaysPerWeek(5)
            .build();

    Calendar savedCalendar = calendarRepository.save(globalCalendar);
    log.info("Created global calendar: Standard");

    // Create CalendarWorkWeek entries for 7 days
    // Monday-Friday: WORKING days with 8 work hours
    DayOfWeek[] workDays = {
      DayOfWeek.MONDAY,
      DayOfWeek.TUESDAY,
      DayOfWeek.WEDNESDAY,
      DayOfWeek.THURSDAY,
      DayOfWeek.FRIDAY
    };

    for (DayOfWeek day : workDays) {
      CalendarWorkWeek workWeek =
          CalendarWorkWeek.builder()
              .calendarId(savedCalendar.getId())
              .dayOfWeek(day)
              .dayType(DayType.WORKING)
              .startTime1(LocalTime.of(8, 0))
              .endTime1(LocalTime.of(12, 0))
              .startTime2(LocalTime.of(13, 0))
              .endTime2(LocalTime.of(17, 0))
              .totalWorkHours(8.0)
              .build();
      calendarWorkWeekRepository.save(workWeek);
      log.debug("Created work week entry for {}", day);
    }

    // Saturday-Sunday: NON_WORKING days
    DayOfWeek[] weekendDays = {DayOfWeek.SATURDAY, DayOfWeek.SUNDAY};

    for (DayOfWeek day : weekendDays) {
      CalendarWorkWeek nonWorkWeek =
          CalendarWorkWeek.builder()
              .calendarId(savedCalendar.getId())
              .dayOfWeek(day)
              .dayType(DayType.NON_WORKING)
              .totalWorkHours(0.0)
              .build();
      calendarWorkWeekRepository.save(nonWorkWeek);
      log.debug("Created non-work week entry for {}", day);
    }

    log.info("Created 7 CalendarWorkWeek entries for Standard calendar");
  }

  private void seedBaseCurrency() {
    log.debug("Seeding base currency");

    Currency usd =
        new Currency("USD", "United States Dollar", "$", BigDecimal.ONE, true, 2);

    currencyRepository.save(usd);
    log.debug("Created base currency: USD");
  }

  private void seedGlobalSettings() {
    log.debug("Seeding global settings");

    GlobalSetting schedulingOption =
        new GlobalSetting(
            "default.scheduling.option",
            "RETAINED_LOGIC",
            "Default scheduling option for projects",
            "scheduling");

    GlobalSetting evmTechnique =
        new GlobalSetting(
            "default.evm.technique",
            "ACTIVITY_PERCENT_COMPLETE",
            "Default EVM technique for projects",
            "evm");

    globalSettingRepository.save(schedulingOption);
    globalSettingRepository.save(evmTechnique);

    log.debug("Created global settings");
  }
}
