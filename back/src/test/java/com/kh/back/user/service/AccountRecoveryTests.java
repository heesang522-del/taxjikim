package com.kh.back.user.service;

import com.kh.back.auth.service.EmailVerificationService;
import com.kh.back.auth.service.AuthService;
import com.kh.back.auth.controller.AuthApiController;
import com.kh.back.common.exception.ApiException;
import com.kh.back.common.util.FileUtil;
import com.kh.back.user.dto.UserDto;
import com.kh.back.user.mapper.UserMapper;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AccountRecoveryTests {
    private UserMapper mapper;
    private EmailVerificationService emails;
    private UserService service;
    private UserDto user;

    @BeforeEach
    void setUp() {
        // 실제 DB와 메일을 사용하지 않고 계정 복구의 소유권·상태 조건을 검증한다.
        mapper = mock(UserMapper.class);
        emails = mock(EmailVerificationService.class);
        service = new UserServiceImpl(mapper, mock(PasswordEncoder.class), emails, mock(FileUtil.class));
        user = new UserDto();
        user.setLoginId("test_user");
        user.setEmail("owner@example.com");
        user.setUserStatus("DORMANT");
        when(mapper.findByLoginId("test_user")).thenReturn(user);
        when(emails.isVerified("owner@example.com")).thenReturn(true);
    }

    @Test
    void restoresVerifiedOwnerOfDormantAccount() {
        when(mapper.activateDormantAccount("test_user", "owner@example.com")).thenReturn(1);
        service.restoreAccountStatus("test_user", "owner@example.com", UserService.AccountRestoreType.DORMANT);
        verify(emails).removeVerification("owner@example.com");
        verify(mapper, never()).cancelDeleteUser(any(), any(), any());
    }

    @Test
    void restoresDeletedAccountWithinExistingThirtyDayPeriod() {
        user.setUserStatus("DELETED");
        user.setDeletedAt(LocalDateTime.now().minusDays(1));
        when(mapper.cancelDeleteUser(eq("test_user"), eq("owner@example.com"), any())).thenReturn(1);
        service.restoreAccountStatus("test_user", "owner@example.com", UserService.AccountRestoreType.DELETE);
        verify(emails).removeVerification("owner@example.com");
        verify(mapper, never()).activateDormantAccount(any(), any());
    }

    @Test
    void rejectsAnotherVerifiedEmail() {
        when(emails.isVerified("other@example.com")).thenReturn(true);
        ApiException error = assertThrows(ApiException.class, () ->
                service.restoreAccountStatus("test_user", "other@example.com", UserService.AccountRestoreType.DORMANT));
        assertEquals("ACCOUNT_RECOVERY_REJECTED", error.getCode());
        verifyNoInteractions(emails);
        verify(mapper, never()).activateDormantAccount(any(), any());
    }

    @Test
    void rejectsMissingAccount() {
        when(mapper.findByLoginId("test_user")).thenReturn(null);
        assertThrows(ApiException.class, () ->
                service.restoreAccountStatus("test_user", "owner@example.com", UserService.AccountRestoreType.DORMANT));
        verifyNoInteractions(emails);
    }

    @Test
    void rejectsUnverifiedOwner() {
        when(emails.isVerified("owner@example.com")).thenReturn(false);
        assertThrows(ApiException.class, () ->
                service.restoreAccountStatus("test_user", "owner@example.com", UserService.AccountRestoreType.DORMANT));
        verify(mapper, never()).activateDormantAccount(any(), any());
        verify(emails, never()).removeVerification(any());
    }

    @Test
    void rejectsWrongStatusForBothRecoveryTypes() {
        for (String status : new String[]{"ACTIVE", "LOCKED", "BANNED", "DELETED"}) {
            user.setUserStatus(status);
            assertThrows(ApiException.class, () ->
                    service.restoreAccountStatus("test_user", "owner@example.com", UserService.AccountRestoreType.DORMANT));
        }
        for (String status : new String[]{"ACTIVE", "LOCKED", "BANNED", "DORMANT"}) {
            user.setUserStatus(status);
            assertThrows(ApiException.class, () ->
                    service.restoreAccountStatus("test_user", "owner@example.com", UserService.AccountRestoreType.DELETE));
        }
        verify(mapper, never()).activateDormantAccount(any(), any());
        verify(mapper, never()).cancelDeleteUser(any(), any(), any());
    }

    @Test
    void rejectsExpiredOrMissingDeletionDate() {
        user.setUserStatus("DELETED");
        for (LocalDateTime deletedAt : new LocalDateTime[]{null, LocalDateTime.now().minusDays(31)}) {
            user.setDeletedAt(deletedAt);
            assertThrows(ApiException.class, () ->
                    service.restoreAccountStatus("test_user", "owner@example.com", UserService.AccountRestoreType.DELETE));
        }
        verify(mapper, never()).cancelDeleteUser(any(), any(), any());
    }

    @Test
    void rejectsConcurrentChangeWithoutConsumingVerification() {
        when(mapper.activateDormantAccount(any(), any())).thenReturn(0);
        ApiException error = assertThrows(ApiException.class, () ->
                service.restoreAccountStatus("test_user", "owner@example.com", UserService.AccountRestoreType.DORMANT));
        assertEquals("ACCOUNT_RECOVERY_CONFLICT", error.getCode());
        verify(emails, never()).removeVerification(any());
    }

    @Test
    void rejectsIncompleteRequestBeforeDatabaseAccess() {
        assertThrows(ApiException.class, () -> service.restoreAccountStatus("", "owner@example.com", UserService.AccountRestoreType.DORMANT));
        assertThrows(ApiException.class, () -> service.restoreAccountStatus("test_user", null, UserService.AccountRestoreType.DORMANT));
        assertThrows(ApiException.class, () -> service.restoreAccountStatus("test_user", "owner@example.com", null));
        verifyNoInteractions(mapper, emails);
    }

    @Test
    void apiRejectsWrongCodeBeforeRestoringAccount() {
        UserService userService = mock(UserService.class);
        AuthApiController controller = new AuthApiController(userService, mock(AuthService.class), emails);
        ApiException error = assertThrows(ApiException.class, () -> controller.unlockDormant(
                Map.of("loginId", "test_user", "email", "owner@example.com", "code", "wrong")));
        assertEquals("VERIFICATION_CODE_INVALID", error.getCode());
        verifyNoInteractions(userService);
    }

    @Test
    void apiPropagatesRecoveryConflictInsteadOfReturningSuccessStatus() {
        UserService userService = mock(UserService.class);
        AuthApiController controller = new AuthApiController(userService, mock(AuthService.class), emails);
        when(emails.verifyCode("owner@example.com", "test-code")).thenReturn(true);
        ApiException conflict = new ApiException(org.springframework.http.HttpStatus.CONFLICT, "ACCOUNT_NOT_DORMANT", "test");
        doThrow(conflict).when(userService).restoreAccountStatus("test_user", "owner@example.com", UserService.AccountRestoreType.DORMANT);
        assertSame(conflict, assertThrows(ApiException.class, () -> controller.unlockDormant(
                Map.of("loginId", "test_user", "email", "owner@example.com", "code", "test-code"))));
    }

    @Test
    void sqlRechecksEmailStatusAndDeletionDate() throws Exception {
        Configuration config = new Configuration();
        try (var input = getClass().getClassLoader().getResourceAsStream("mappers/user/UserMapper.xml")) {
            assertNotNull(input);
            new XMLMapperBuilder(input, config, "mappers/user/UserMapper.xml", config.getSqlFragments()).parse();
        }
        var parameters = Map.of("loginId", "test_user", "email", "owner@example.com", "cutoffDate", LocalDateTime.now().minusDays(30));
        String dormantSql = config.getMappedStatement(UserMapper.class.getName() + ".activateDormantAccount").getBoundSql(parameters).getSql();
        assertTrue(dormantSql.contains("AND EMAIL = ?"));
        assertTrue(dormantSql.contains("AND USER_STATUS = 'DORMANT'"));
        String deletedSql = config.getMappedStatement(UserMapper.class.getName() + ".cancelDeleteUser").getBoundSql(parameters).getSql();
        assertTrue(deletedSql.contains("AND EMAIL = ?"));
        assertTrue(deletedSql.contains("AND USER_STATUS = 'DELETED'"));
        assertTrue(deletedSql.contains("AND DELETED_AT > ?"));
    }
}
