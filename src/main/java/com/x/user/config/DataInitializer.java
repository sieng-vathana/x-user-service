package com.x.user.config;

import com.x.user.model.*;
import com.x.user.repository.*;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Local-only seed for a default admin user, roles, and permissions.
 * Active only with Spring profile {@code local-seed} (used by local).
 *
 * <p>RBAC permission codes follow {@code x-{service}:{action}}:
 * <ul>
 *   <li>Standard: {@code read}, {@code create}, {@code update}, {@code delete}</li>
 *   <li>Extras: e.g. {@code stock-in}, {@code stock-out}, {@code refund}, {@code cancel},
 *       {@code capture}, {@code upload}</li>
 * </ul>
 *
 * <p>Default login (override password with {@code X_SEED_ADMIN_PASSWORD}):
 * <ul>
 *   <li>username: {@code admin}</li>
 *   <li>password: {@code Admin@123456}</li>
 * </ul>
 */
@Configuration
@Profile("local-seed")
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    private static final long DEFAULT_BUSINESS_ID = 1L;
    private static final long DEFAULT_STORE_ID = 101L;

    /** Standard CRUD actions for every service. */
    private static final List<String> CRUD = List.of("read", "create", "update", "delete");

    /**
     * Service → actions. Every service gets CRUD; some add domain-specific actions.
     */
    private static final Map<String, List<String>> SERVICE_ACTIONS = Map.ofEntries(
            Map.entry("user", CRUD),
            Map.entry("business", CRUD),
            Map.entry("store", CRUD),
            Map.entry("customer", CRUD),
            Map.entry("product", concat(CRUD, "unit", "category")),
            Map.entry("inventory", concat(CRUD, "stock-in", "stock-out", "reserve")),
            Map.entry("order", concat(CRUD, "refund", "cancel")),
            Map.entry("delivery", concat(CRUD, "quote", "remit")),
            Map.entry("storage", concat(CRUD, "upload")),
            Map.entry("payment", concat(CRUD, "capture", "refund")),
            Map.entry("report", List.of("read")),
            Map.entry("bff", List.of("read"))
    );

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;
    private final RolePermissionRepository rolePermissionRepository;
    private final StoreMemberRepository storeMemberRepository;

    @Value("${x.seed.admin-username:admin}")
    private String adminUsername;

    @Value("${x.seed.admin-password:Admin@123456}")
    private String adminPassword;

    @Value("${x.seed.admin-full-name:System Administrator}")
    private String adminFullName;

    @Value("${x.seed.admin-email:admin@x.local}")
    private String adminEmail;

    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @Override
    @Transactional
    public void run(String... args) {
        if (!StringUtils.hasText(adminPassword)) {
            throw new IllegalStateException(
                    "x.seed.admin-password / X_SEED_ADMIN_PASSWORD must not be empty when local-seed is active");
        }

        // 1. Permissions: x-{service}:{action}
        Map<String, Permission> byCode = new LinkedHashMap<>();
        for (Map.Entry<String, List<String>> entry : SERVICE_ACTIONS.entrySet()) {
            String service = entry.getKey();
            for (String action : entry.getValue()) {
                String code = code(service, action);
                byCode.put(code, ensurePermission(
                        code,
                        capitalize(service) + " " + action,
                        service.toUpperCase()));
            }
        }

        // 2. Roles
        Role ownerRole = ensureRole("OWNER", "Business Owner", DEFAULT_BUSINESS_ID);

        // 3. Role → permission maps
        // OWNER: every permission
        ensureRolePermissions(ownerRole, new ArrayList<>(byCode.values()));

        // 4. Default admin user
        User admin = userRepository.findByUsername(adminUsername).orElse(null);
        if (admin == null) {
            admin = User.builder()
                    .username(adminUsername)
                    .password(encodePassword(adminPassword))
                    .fullName(adminFullName)
                    .email(adminEmail)
                    .status(1)
                    .isLocked(false)
                    .failedAttempt(0)
                    .build();
            userRepository.save(admin);
            log.info("Seeded default user '{}' (local-seed). Change password before any shared use.", adminUsername);
        } else {
            log.info("Default user '{}' already exists — seed skipped for user row.", adminUsername);
        }

        // 5. Store membership (OWNER on default store)
        final User seededAdmin = admin;
        boolean alreadyMember = storeMemberRepository.findByUser(seededAdmin).stream()
                .anyMatch(sm -> DEFAULT_STORE_ID == sm.getStoreId()
                        && sm.getRole() != null
                        && "OWNER".equals(sm.getRole().getRoleCode()));
        if (!alreadyMember) {
            storeMemberRepository.save(StoreMember.builder()
                    .user(seededAdmin)
                    .storeId(DEFAULT_STORE_ID)
                    .role(ownerRole)
                    .build());
            log.info("Assigned user '{}' to store {} with OWNER role.", adminUsername, DEFAULT_STORE_ID);
        }

        log.info("RBAC seed complete: x-{{service}}:{{action}} — {} permission codes", byCode.size());
    }

    private static List<String> concat(List<String> base, String... extra) {
        return Stream.concat(base.stream(), Stream.of(extra)).collect(Collectors.toList());
    }

    private static String code(String service, String action) {
        return "x-" + service + ":" + action;
    }

    private static String capitalize(String value) {
        if (value == null || value.isEmpty()) {
            return value;
        }
        return Character.toUpperCase(value.charAt(0)) + value.substring(1);
    }

    private Permission ensurePermission(String code, String name, String module) {
        return permissionRepository.findByPermissionCode(code)
                .orElseGet(() -> permissionRepository.save(Permission.builder()
                        .permissionCode(code)
                        .permissionName(name)
                        .moduleName(module)
                        .description("Seeded RBAC: " + code)
                        .build()));
    }

    private Role ensureRole(String code, String name, Long businessId) {
        return roleRepository.findByBusinessIdAndRoleCodeIgnoreCase(businessId, code)
                .orElseGet(() -> roleRepository.save(Role.builder()
                        .roleCode(code)
                        .roleName(name)
                        .businessId(businessId)
                        .isSystem(true)
                        .description("Seeded for local development")
                        .build()));
    }

    private void ensureRolePermissions(Role role, List<Permission> permissions) {
        for (Permission permission : permissions) {
            if (permission == null) {
                continue;
            }
            boolean linked = rolePermissionRepository.findByRole(role).stream()
                    .anyMatch(rp -> rp.getPermission() != null
                            && permission.getId().equals(rp.getPermission().getId()));
            if (!linked) {
                rolePermissionRepository.save(RolePermission.builder()
                        .role(role)
                        .permission(permission)
                        .build());
            }
        }
    }

    private String encodePassword(String rawOrAlreadyHashed) {
        if (rawOrAlreadyHashed != null
                && (rawOrAlreadyHashed.startsWith("$2a$")
                || rawOrAlreadyHashed.startsWith("$2b$")
                || rawOrAlreadyHashed.startsWith("$2y$"))) {
            return rawOrAlreadyHashed;
        }
        return passwordEncoder.encode(rawOrAlreadyHashed);
    }
}
