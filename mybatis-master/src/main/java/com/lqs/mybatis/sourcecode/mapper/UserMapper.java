package com.lqs.mybatis.sourcecode.mapper;

import com.lqs.mybatis.sourcecode.domain.User;

/**
 * @author : 李奇凇
 * @date : 2022/5/14 10:25
 * @do :
 */
public interface UserMapper {
    User selectOne(Long id);
}
