package com.kh.back.auth.service;

import com.kh.back.auth.dto.LockedAccountDto;
import java.util.List;

public interface AccountLockService {
    List<LockedAccountDto> getLockedAccounts();
    void unlockAccount(String loginId);
}
