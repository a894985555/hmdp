package com.hmdp.mapper;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hmdp.entity.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * <p>
 *  Mapper 接口
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */

@Mapper
public interface UserMapper extends BaseMapper<User> {

    @Select("UPDATE tb_user SET password = password + 1  ${ew.customSqlSegment}")
    void updateByIds(@Param("ew")QueryWrapper<User> wrapper);

    List<User>getUserList();
}
