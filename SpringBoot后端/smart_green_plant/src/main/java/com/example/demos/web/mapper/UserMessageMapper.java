package com.example.demos.web.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.demos.web.pojo.entity.UserMessage;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface UserMessageMapper extends BaseMapper<UserMessage> {
}
