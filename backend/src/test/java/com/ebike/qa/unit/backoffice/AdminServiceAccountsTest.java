package com.ebike.qa.unit.backoffice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ebike.dto.admin.request.AdminAccountCreateRequest;
import com.ebike.dto.admin.request.AdminAccountRoleUpdateRequest;
import com.ebike.dto.admin.request.AdminAccountStatusUpdateRequest;
import com.ebike.dto.admin.request.AdminAccountUpdateRequest;
import com.ebike.dto.admin.response.AdminAccountResponse;
import com.ebike.model.entity.auth.User;
import com.ebike.repository.admin.AdminAuditLogRepository;
import com.ebike.repository.admin.PricingRuleRepository;
import com.ebike.repository.admin.PromotionRepository;
import com.ebike.repository.auth.AuthenticationLogRepository;
import com.ebike.repository.auth.RoleRepository;
import com.ebike.repository.auth.UserRepository;
import com.ebike.repository.order.OrderRepository;
import com.ebike.repository.testride.TestRideBookingRepository;
import com.ebike.service.admin.AuditLogService;
import com.ebike.service.admin.impl.AdminServiceImpl;
import com.ebike.service.product.ProductReviewService;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

class AdminServiceAccountsTest {

    PricingRuleRepository pricing = mock(PricingRuleRepository.class);
    PromotionRepository promotions = mock(PromotionRepository.class);
    AdminAuditLogRepository auditRepo = mock(AdminAuditLogRepository.class);
    UserRepository users = mock(UserRepository.class);
    RoleRepository roles = mock(RoleRepository.class);
    PasswordEncoder encoder = mock(PasswordEncoder.class);
    OrderRepository orders = mock(OrderRepository.class);
    AuthenticationLogRepository authLogs = mock(AuthenticationLogRepository.class);
    TestRideBookingRepository rides = mock(TestRideBookingRepository.class);
    ProductReviewService reviews = mock(ProductReviewService.class);
    AuditLogService audit = mock(AuditLogService.class);
    AdminServiceImpl service;

    User admin = Fx.user(1L, "root", "root@shop.com", "ADMIN");
    User admin2 = Fx.user(2L, "boss2", "boss2@shop.com", "ADMIN");
    User customer = Fx.user(10L, "cust", "cust@x.com", "CUSTOMER");

    @BeforeEach
    void setUp() {
        service = new AdminServiceImpl(pricing, promotions, auditRepo, users, roles, encoder, orders, authLogs, rides, reviews, audit);
        when(encoder.encode(anyString())).thenAnswer(i -> "ENC(" + i.getArgument(0) + ")");
        when(users.save(any(User.class))).thenAnswer(i -> i.getArgument(0));
        when(users.saveAndFlush(any(User.class))).thenAnswer(i -> i.getArgument(0));
        when(users.findAll()).thenReturn(List.of(admin, admin2, customer));
        for (User u : List.of(admin, admin2, customer)) {
            when(users.findById(u.getId())).thenReturn(Optional.of(u));
        }
        for (String r : List.of("ADMIN", "MANAGER", "CUSTOMER", "OPERATOR", "SUPPORT", "MAINTENANCE")) {
            when(roles.findByName(r)).thenReturn(Optional.of(Fx.role(r)));
        }
        Fx.signIn("root");
    }

    @AfterEach
    void tearDown() {
        Fx.signOut();
    }

    private static void assertStatus(Throwable t, HttpStatus status) {
        assertThat(t).isInstanceOf(ResponseStatusException.class);
        assertThat(((ResponseStatusException) t).getStatusCode().value()).isEqualTo(status.value());
    }

    private static AdminAccountCreateRequest create(String name, String username, String email, String password, String role, String status) {
        return new AdminAccountCreateRequest(name, username, email, password, role, status, null);
    }

    // ------------------------------------------------------------------ create

    @Test
    void create_happyPath_hashesPassword_normalisesEmail_splitsName() {
        AdminAccountResponse r = service.createAccount(create(" Le Van Tien ", " newbie ", "  New@Shop.COM ", "secret12", "manager", null));
        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(users).save(saved.capture());
        User u = saved.getValue();
        assertThat(u.getEmail()).isEqualTo("new@shop.com");
        assertThat(u.getUsername()).isEqualTo("newbie");
        assertThat(u.getFirstName()).isEqualTo("Le");
        assertThat(u.getLastName()).isEqualTo("Van Tien");
        assertThat(u.getPasswordHash()).isEqualTo("ENC(secret12)");
        assertThat(u.getActive()).isTrue();
        assertThat(u.getAccountStatus()).isEqualTo("ACTIVE");
        assertThat(u.getVerified()).isFalse();
        assertThat(r.role()).isEqualTo("MANAGER");
        verify(audit).record("tạo tài khoản", "new@shop.com");
    }

    @Test
    void create_usernameDefaultsToEmailLocalPart() {
        service.createAccount(create("A B", null, "jane.doe@shop.com", "secret12", "SUPPORT", null));
        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(users).save(saved.capture());
        assertThat(saved.getValue().getUsername()).isEqualTo("jane.doe");
    }

    @Test
    void create_superAdminRoleIsStoredAsAdmin() {
        AdminAccountResponse r = service.createAccount(create("A B", "x1", "x1@shop.com", "secret12", "super_admin", null));
        assertThat(r.role()).isEqualTo("ADMIN");
    }

    @Test
    void create_missingRole_defaultsToCustomer_neverToAdmin() {
        AdminAccountResponse r = service.createAccount(create("A B", "x1", "x1@shop.com", "secret12", null, null));
        assertThat(r.role()).isEqualTo("CUSTOMER");
    }

    @Test
    void create_lockedStatus_createsInactiveAccount() {
        AdminAccountResponse r = service.createAccount(create("A B", "x1", "x1@shop.com", "secret12", "MANAGER", "locked"));
        assertThat(r.status()).isEqualTo("LOCKED");
        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(users).save(saved.capture());
        assertThat(saved.getValue().getActive()).isFalse();
    }

    @Test
    void create_unknownRoleOrStatus_is400() {
        assertThatThrownBy(() -> service.createAccount(create("A B", "x1", "x1@shop.com", "secret12", "GOD", null)))
            .satisfies(t -> assertStatus(t, HttpStatus.BAD_REQUEST));
        assertThatThrownBy(() -> service.createAccount(create("A B", "x1", "x1@shop.com", "secret12", "MANAGER", "banana")))
            .satisfies(t -> assertStatus(t, HttpStatus.BAD_REQUEST));
        verify(users, never()).save(any());
    }

    @Test
    void create_roleMissingFromDatabase_is400() {
        when(roles.findByName("OPERATOR")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.createAccount(create("A B", "x1", "x1@shop.com", "secret12", "OPERATOR", null)))
            .satisfies(t -> assertStatus(t, HttpStatus.BAD_REQUEST));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"  ", "12345", "  1234  "})
    void create_shortOrBlankPassword_is400(String pw) {
        assertThatThrownBy(() -> service.createAccount(create("A B", "x1", "x1@shop.com", pw, "MANAGER", null)))
            .satisfies(t -> assertStatus(t, HttpStatus.BAD_REQUEST));
        verify(users, never()).save(any());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void create_blankNameOrEmail_is400(String blank) {
        assertThatThrownBy(() -> service.createAccount(create(blank, "x1", "x1@shop.com", "secret12", "MANAGER", null)))
            .satisfies(t -> assertStatus(t, HttpStatus.BAD_REQUEST));
        assertThatThrownBy(() -> service.createAccount(create("A B", "x1", blank, "secret12", "MANAGER", null)))
            .satisfies(t -> assertStatus(t, HttpStatus.BAD_REQUEST));
    }

    @Test
    void create_duplicateEmail_is409_caseAndWhitespaceInsensitive() {
        when(users.existsByEmail("root@shop.com")).thenReturn(true);
        assertThatThrownBy(() -> service.createAccount(create("A B", "x1", "  ROOT@Shop.com ", "secret12", "MANAGER", null)))
            .satisfies(t -> assertStatus(t, HttpStatus.CONFLICT));
        verify(users, never()).save(any());
    }

    @Test
    void create_duplicateUsername_is409() {
        when(users.existsByUsername("taken")).thenReturn(true);
        assertThatThrownBy(() -> service.createAccount(create("A B", " taken ", "x1@shop.com", "secret12", "MANAGER", null)))
            .satisfies(t -> assertStatus(t, HttpStatus.CONFLICT));
    }

    /** Live finding: POST /admin/accounts saves email "zzz". Update validates the email; create must too. */
    @Test
    void regression_create_acceptsMalformedEmail() {
        assertThatThrownBy(() -> service.createAccount(create("A B", "zzz1", "zzz", "secret12", "MANAGER", null)))
            .satisfies(t -> {
                assertThat(t).isInstanceOf(ResponseStatusException.class);
                assertStatus(t, HttpStatus.BAD_REQUEST);
            });
    }

    /** Without an "@" and without a username the default username is built with substring(0, indexOf('@')) and blows up (HTTP 500). */
    @Test
    void regression_create_emailWithoutAtAndNoUsername_mustBe400NotStringIndexError() {
        assertThatThrownBy(() -> service.createAccount(create("A B", null, "zzz", "secret12", "MANAGER", null)))
            .satisfies(t -> assertStatus(t, HttpStatus.BAD_REQUEST));
    }

    @Test
    void regression_create_emailStartingWithAtGivesBlankUsername() {
        try {
            service.createAccount(create("A B", null, "@shop.com", "secret12", "MANAGER", null));
        } catch (ResponseStatusException expected) {
            return;
        }
        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(users).save(saved.capture());
        assertThat(saved.getValue().getUsername()).as("username must not be blank").isNotBlank();
    }

    /** create applies no length rule, update does (100 per name part): a 300-character name reaches the database (HTTP 500). */
    @Test
    void regression_create_nameParts_over100CharsMustBe400() {
        assertThatThrownBy(() -> service.createAccount(create("A".repeat(300) + " B", "x1", "x1@shop.com", "secret12", "MANAGER", null)))
            .satisfies(t -> assertStatus(t, HttpStatus.BAD_REQUEST));
    }

    @Test
    void regression_create_usernameWithSpacesAccepted() {
        assertThatThrownBy(() -> service.createAccount(create("A B", "qa user x", "x1@shop.com", "secret12", "MANAGER", null)))
            .satisfies(t -> assertStatus(t, HttpStatus.BAD_REQUEST));
    }

    // ------------------------------------------------------------------ role

    @Test
    void role_changeOtherAdminToManager_allowedWhenAnotherActiveAdminRemains() {
        AdminAccountResponse r = service.updateAccountRole(2L, new AdminAccountRoleUpdateRequest("manager"));
        assertThat(r.role()).isEqualTo("MANAGER");
        verify(audit).record(anyString(), anyString());
    }

    @Test
    void role_adminCannotDemoteSelf() {
        assertThatThrownBy(() -> service.updateAccountRole(1L, new AdminAccountRoleUpdateRequest("MANAGER")))
            .satisfies(t -> assertStatus(t, HttpStatus.BAD_REQUEST));
        assertThat(admin.getRoles()).extracting(r -> r.getName()).containsExactly("ADMIN");
        verify(users, never()).save(any());
    }

    @Test
    void role_selfCheckMatchesEmailToo_andIgnoresCase() {
        Fx.signIn("ROOT@shop.com");
        assertThatThrownBy(() -> service.updateAccountRole(1L, new AdminAccountRoleUpdateRequest("MANAGER")))
            .satisfies(t -> assertStatus(t, HttpStatus.BAD_REQUEST));
    }

    @Test
    void role_lastActiveAdminCannotBeDemoted_evenByAnotherUser() {
        admin2.setActive(false);
        Fx.signIn("someone-else");
        assertThatThrownBy(() -> service.updateAccountRole(1L, new AdminAccountRoleUpdateRequest("MANAGER")))
            .satisfies(t -> assertStatus(t, HttpStatus.BAD_REQUEST));
    }

    @Test
    void role_unknownRole_is400_andNothingChanges() {
        assertThatThrownBy(() -> service.updateAccountRole(10L, new AdminAccountRoleUpdateRequest("ROOT")))
            .satisfies(t -> assertStatus(t, HttpStatus.BAD_REQUEST));
        assertThat(customer.getRoles()).extracting(r -> r.getName()).containsExactly("CUSTOMER");
    }

    @Test
    void role_unknownAccount_is404() {
        assertThatThrownBy(() -> service.updateAccountRole(999L, new AdminAccountRoleUpdateRequest("MANAGER")))
            .satisfies(t -> assertStatus(t, HttpStatus.NOT_FOUND));
    }

    @Test
    void role_deletedAccountCannotBeChanged() {
        customer.setAccountStatus("DELETED");
        assertThatThrownBy(() -> service.updateAccountRole(10L, new AdminAccountRoleUpdateRequest("MANAGER")))
            .satisfies(t -> assertStatus(t, HttpStatus.BAD_REQUEST));
    }

    @Test
    void role_replacesRolesWithExactlyOne_noLeftoverAdmin() {
        admin2.getRoles().add(Fx.role("MANAGER"));
        service.updateAccountRole(2L, new AdminAccountRoleUpdateRequest("SUPPORT"));
        assertThat(admin2.getRoles()).extracting(r -> r.getName()).containsExactly("SUPPORT");
    }

    // ------------------------------------------------------------------ status

    @Test
    void status_lockOtherUser_setsInactive() {
        AdminAccountResponse r = service.updateAccountStatus(10L, new AdminAccountStatusUpdateRequest("locked"));
        assertThat(r.status()).isEqualTo("LOCKED");
        assertThat(customer.getActive()).isFalse();
        assertThat(customer.getAccountStatus()).isEqualTo("LOCKED");
    }

    @Test
    void status_reactivate_setsActive() {
        customer.setActive(false);
        customer.setAccountStatus("LOCKED");
        service.updateAccountStatus(10L, new AdminAccountStatusUpdateRequest("ACTIVE"));
        assertThat(customer.getActive()).isTrue();
    }

    @Test
    void status_adminCannotLockSelf() {
        assertThatThrownBy(() -> service.updateAccountStatus(1L, new AdminAccountStatusUpdateRequest("LOCKED")))
            .satisfies(t -> assertStatus(t, HttpStatus.BAD_REQUEST));
        assertThat(admin.getActive()).isTrue();
    }

    @Test
    void status_adminCannotInactivateSelf() {
        assertThatThrownBy(() -> service.updateAccountStatus(1L, new AdminAccountStatusUpdateRequest("INACTIVE")))
            .satisfies(t -> assertStatus(t, HttpStatus.BAD_REQUEST));
    }

    @Test
    void status_lastActiveAdminCannotBeLocked() {
        admin2.setActive(false);
        Fx.signIn("other");
        assertThatThrownBy(() -> service.updateAccountStatus(1L, new AdminAccountStatusUpdateRequest("LOCKED")))
            .satisfies(t -> assertStatus(t, HttpStatus.BAD_REQUEST));
        assertThat(admin.getActive()).isTrue();
    }

    @Test
    void status_lockingOneOfTwoAdminsIsAllowed() {
        service.updateAccountStatus(2L, new AdminAccountStatusUpdateRequest("LOCKED"));
        assertThat(admin2.getActive()).isFalse();
    }

    @Test
    void status_garbageValue_is400() {
        assertThatThrownBy(() -> service.updateAccountStatus(10L, new AdminAccountStatusUpdateRequest("DELETED")))
            .satisfies(t -> assertStatus(t, HttpStatus.BAD_REQUEST));
    }

    @Test
    void status_blankDefaultsToActive_notToLocked() {
        customer.setActive(false);
        service.updateAccountStatus(10L, new AdminAccountStatusUpdateRequest(" "));
        assertThat(customer.getActive()).isTrue();
    }

    @Test
    void status_cannotResurrectErasedAccount() {
        customer.setAccountStatus("DELETED");
        customer.setActive(false);
        assertThatThrownBy(() -> service.updateAccountStatus(10L, new AdminAccountStatusUpdateRequest("ACTIVE")))
            .satisfies(t -> assertStatus(t, HttpStatus.BAD_REQUEST));
        assertThat(customer.getActive()).isFalse();
    }

    // ------------------------------------------------------------------ update details

    @Test
    void updateAccount_changesNameAndEmail() {
        var d = service.updateAccount(10L, new AdminAccountUpdateRequest(" Anh  Nguyen Van ", " NEW@x.com "));
        assertThat(customer.getEmail()).isEqualTo("new@x.com");
        assertThat(customer.getFirstName()).isEqualTo("Anh");
        assertThat(customer.getLastName()).isEqualTo("Nguyen Van");
        assertThat(d.canDelete()).isTrue();
    }

    @Test
    void updateAccount_badEmail_is400() {
        for (String bad : List.of("zzz", "a@b", "a b@c.com", "@c.com", "x".repeat(150) + "@example.com")) {
            assertThatThrownBy(() -> service.updateAccount(10L, new AdminAccountUpdateRequest("A B", bad)))
                .satisfies(t -> assertStatus(t, HttpStatus.BAD_REQUEST));
        }
        assertThat(customer.getEmail()).isEqualTo("cust@x.com");
    }

    @Test
    void updateAccount_emailOfSomeoneElse_is409() {
        when(users.existsByEmail("root@shop.com")).thenReturn(true);
        assertThatThrownBy(() -> service.updateAccount(10L, new AdminAccountUpdateRequest("A B", "ROOT@shop.com")))
            .satisfies(t -> assertStatus(t, HttpStatus.CONFLICT));
    }

    @Test
    void updateAccount_nameParts_100ok_101rejected() {
        service.updateAccount(10L, new AdminAccountUpdateRequest("a".repeat(100) + " " + "b".repeat(100), "cust@x.com"));
        assertThatThrownBy(() -> service.updateAccount(10L, new AdminAccountUpdateRequest("a".repeat(101), "cust@x.com")))
            .satisfies(t -> assertStatus(t, HttpStatus.BAD_REQUEST));
        assertThatThrownBy(() -> service.updateAccount(10L, new AdminAccountUpdateRequest("a " + "b".repeat(101), "cust@x.com")))
            .satisfies(t -> assertStatus(t, HttpStatus.BAD_REQUEST));
    }

    @Test
    void updateAccount_nullRequestOrBlankName_is400() {
        assertThatThrownBy(() -> service.updateAccount(10L, null)).satisfies(t -> assertStatus(t, HttpStatus.BAD_REQUEST));
        assertThatThrownBy(() -> service.updateAccount(10L, new AdminAccountUpdateRequest("  ", "a@b.com")))
            .satisfies(t -> assertStatus(t, HttpStatus.BAD_REQUEST));
    }

    @Test
    void updateAccount_concurrentDuplicate_isMappedTo409() {
        when(users.saveAndFlush(any())).thenThrow(new org.springframework.dao.DataIntegrityViolationException("dup"));
        assertThatThrownBy(() -> service.updateAccount(10L, new AdminAccountUpdateRequest("A B", "n@x.com")))
            .satisfies(t -> assertStatus(t, HttpStatus.CONFLICT));
    }

    @Test
    void getDetail_ofErasedAccount_is400() {
        customer.setAccountStatus("DELETED");
        assertThatThrownBy(() -> service.getAccountDetail(10L)).satisfies(t -> assertStatus(t, HttpStatus.BAD_REQUEST));
    }

    // ------------------------------------------------------------------ delete

    private void stubDeletable() {
        when(orders.existsByUserId(anyLong())).thenReturn(false);
        when(rides.existsByUserIdAndStatusIn(anyLong(), anyList())).thenReturn(false);
    }

    @Test
    void delete_customerWithoutHistory_isRemovedAfterEmailConfirmation_andAuditHasNoEmail() {
        stubDeletable();
        service.deleteAccount(10L, "  CUST@x.com ");
        verify(reviews).removeReviewsOf(10L);
        verify(authLogs).deleteByUserId(10L);
        verify(users).delete(customer);
        verify(audit).record("xóa tài khoản", "tài khoản #10");
    }

    @Test
    void delete_wrongOrMissingConfirmEmail_is400_nothingDeleted() {
        stubDeletable();
        assertThatThrownBy(() -> service.deleteAccount(10L, "other@x.com")).satisfies(t -> assertStatus(t, HttpStatus.BAD_REQUEST));
        assertThatThrownBy(() -> service.deleteAccount(10L, null)).satisfies(t -> assertStatus(t, HttpStatus.BAD_REQUEST));
        assertThatThrownBy(() -> service.deleteAccount(10L, "")).satisfies(t -> assertStatus(t, HttpStatus.BAD_REQUEST));
        verify(users, never()).delete(any());
    }

    @Test
    void delete_self_isRefused_evenWithCorrectEmail() {
        assertThatThrownBy(() -> service.deleteAccount(1L, "root@shop.com")).satisfies(t -> assertStatus(t, HttpStatus.BAD_REQUEST));
        verify(users, never()).delete(any());
    }

    @Test
    void delete_lastActiveAdmin_isRefused() {
        admin2.setActive(false);
        Fx.signIn("other");
        assertThatThrownBy(() -> service.deleteAccount(1L, "root@shop.com")).satisfies(t -> assertStatus(t, HttpStatus.BAD_REQUEST));
        verify(users, never()).delete(any());
    }

    @Test
    void delete_anotherAdminWhileTwoExist_isAllowed() {
        stubDeletable();
        service.deleteAccount(2L, "boss2@shop.com");
        verify(users).delete(admin2);
    }

    @Test
    void delete_accountWithOrders_is409_keepsAccountingRecord() {
        when(orders.existsByUserId(10L)).thenReturn(true);
        assertThatThrownBy(() -> service.deleteAccount(10L, "cust@x.com")).satisfies(t -> assertStatus(t, HttpStatus.CONFLICT));
        verify(users, never()).delete(any());
        verify(reviews, never()).removeReviewsOf(anyLong());
    }

    @Test
    void delete_accountWithOpenTestRide_is409() {
        when(rides.existsByUserIdAndStatusIn(anyLong(), anyList())).thenReturn(true);
        assertThatThrownBy(() -> service.deleteAccount(10L, "cust@x.com")).satisfies(t -> assertStatus(t, HttpStatus.CONFLICT));
        verify(users, never()).delete(any());
    }

    @Test
    void delete_staffWhoAppearsInAuditTrail_isRefused_mustBeDisabledInstead() {
        User mgr = Fx.user(20L, "mgr", "mgr@shop.com", "MANAGER");
        when(users.findById(20L)).thenReturn(Optional.of(mgr));
        when(auditRepo.existsByActorIn(anyList())).thenReturn(true);
        assertThatThrownBy(() -> service.deleteAccount(20L, "mgr@shop.com")).satisfies(t -> assertStatus(t, HttpStatus.BAD_REQUEST));
        verify(users, never()).delete(any());
    }

    @Test
    void delete_integrityViolation_isMappedTo409() {
        stubDeletable();
        org.mockito.Mockito.doThrow(new org.springframework.dao.DataIntegrityViolationException("fk")).when(users).flush();
        assertThatThrownBy(() -> service.deleteAccount(10L, "cust@x.com")).satisfies(t -> assertStatus(t, HttpStatus.CONFLICT));
        verify(audit, never()).record(anyString(), anyString());
    }

    @Test
    void delete_alreadyErased_is400() {
        customer.setAccountStatus("DELETED");
        assertThatThrownBy(() -> service.deleteAccount(10L, "cust@x.com")).satisfies(t -> assertStatus(t, HttpStatus.BAD_REQUEST));
    }

    @Test
    void delete_unknownId_is404() {
        assertThatThrownBy(() -> service.deleteAccount(999L, "x@x.com")).satisfies(t -> assertStatus(t, HttpStatus.NOT_FOUND));
    }

    // ------------------------------------------------------------------ list / overview

    @Test
    void getAccounts_hidesErasedShells_andFiltersByTypeAndSearch() {
        User erased = Fx.user(11L, "deleted_11", "deleted_11@deleted.invalid", "CUSTOMER");
        erased.setAccountStatus("DELETED");
        when(users.findAll()).thenReturn(List.of(admin, admin2, customer, erased));
        assertThat(service.getAccounts(null)).extracting(AdminAccountResponse::id).containsExactlyInAnyOrder(1L, 2L, 10L);
        assertThat(service.getAccounts("customer")).extracting(AdminAccountResponse::id).containsExactly(10L);
        assertThat(service.getAccounts("ADMIN")).extracting(AdminAccountResponse::id).containsExactlyInAnyOrder(1L, 2L);
        assertThat(service.getAccounts(null, "  BOSS2 ")).extracting(AdminAccountResponse::id).containsExactly(2L);
        assertThat(service.getAccounts(null, "nobody")).isEmpty();
    }

    @Test
    void getAccounts_isNewestFirst() {
        assertThat(service.getAccounts(null)).extracting(AdminAccountResponse::id).containsExactly(10L, 2L, 1L);
    }

    @Test
    void getAccounts_neverExposesPasswordHash() {
        assertThat(service.getAccounts(null).toString()).doesNotContain("hash:");
    }

    @Test
    void overview_countsStaffCustomersAndLocked() {
        customer.setActive(false);
        customer.setAccountStatus("LOCKED");
        var o = service.getOverview();
        assertThat(o.toString()).contains("3");
    }

    @Test
    void getRoles_sortedByName() {
        var r1 = Fx.role("MANAGER", "b", "a");
        var r2 = Fx.role("ADMIN", "z");
        when(roles.findAll()).thenReturn(List.of(r1, r2));
        var out = service.getRoles();
        assertThat(out.get(0).role()).isEqualTo("ADMIN");
        assertThat(out.get(1).permissions()).containsExactly("a", "b");
    }
}
