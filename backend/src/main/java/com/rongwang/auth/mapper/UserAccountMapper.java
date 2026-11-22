package com.rongwang.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.rongwang.auth.model.UserAccount;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface UserAccountMapper extends BaseMapper<UserAccount> {
}
