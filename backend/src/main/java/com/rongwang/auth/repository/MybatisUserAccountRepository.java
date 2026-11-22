package com.rongwang.auth.repository;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.rongwang.auth.mapper.UserAccountMapper;
import com.rongwang.auth.model.UserAccount;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public class MybatisUserAccountRepository implements UserAccountRepository {

    private final UserAccountMapper userAccountMapper;

    public MybatisUserAccountRepository(UserAccountMapper userAccountMapper) {
        this.userAccountMapper = userAccountMapper;
    }

    @Override
    public Optional<UserAccount> findByUsername(String username) {
        UserAccount account = userAccountMapper.selectOne(
                Wrappers.<UserAccount>lambdaQuery().eq(UserAccount::getUsername, username));
        return Optional.ofNullable(account);
    }
}
