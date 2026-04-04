package com.bipros.api.config;

import com.bipros.admin.domain.model.Currency;
import com.bipros.admin.domain.model.GlobalSetting;
import com.bipros.admin.domain.repository.CurrencyRepository;
import com.bipros.admin.domain.repository.GlobalSettingRepository;
import com.bipros.calendar.domain.model.Calendar;
import com.bipros.calendar.domain.model.CalendarType;
import com.bipros.calendar.domain.repository.CalendarRepository;
import com.bipros.security.domain.model.Role;
import com.bipros.security.domain.model.User;
import com.bipros.security.domain.model.UserRole;
import com.bipros.security.domain.repository.RoleRepository;
import com.bipros.security.domain.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Slf4j
@Component
@RequiredArgsConstructor
public class DataSeeder implements CommandLineRunner {

  private final RoleRepository roleRepository;
  private final UserRepository userRepository;
  private final CalendarRepository calendarRepository;
  private final CurrencyRepository currencyRepository;
  private final GlobalSettingRepository globalSettingRepository;
  private final PasswordEncoder passwordEncoder;

  @Override
  @Transactional
  public void run(String... args) throws Exception {
    // Check if data already seeded by checking role count
    if (roleRepository.count() > 0) {
      log.info("Data already seeded, skipping initialization");
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

    // Get ADMIN role
    Role adminRole =
        roleRepository
            .findByName("ADMIN")
            .orElseThrow(() -> new RuntimeException("ADMIN role not found"));

    // Create admin user
    User adminUser = new User("admin", "admin@bipros.local", passwordEncoder.encode("admin123"));
    adminUser.setFirstName("System");
    adminUser.setLastName("Administrator");
    adminUser.setEnabled(true);
    adminUser.setAccountLocked(false);

    User savedUser = userRepository.save(adminUser);
    log.debug("Created admin user: {}", savedUser.getUsername());

    // Create UserRole association
    UserRole userRole = new UserRole(savedUser.getId(), adminRole.getId());
    savedUser.getRoles().add(userRole);
    userRepository.save(savedUser);

    log.info("Seeded admin user with ADMIN role");
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

    calendarRepository.save(globalCalendar);
    log.info("Created global calendar: Standard");
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
