package com.green.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.green.entity.PrivateMessage;
import com.green.vo.PrivateChatSessionVO;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface PrivateMessageMapper extends BaseMapper<PrivateMessage> {

    List<PrivateChatSessionVO> selectSessionList(@Param("userId") String userId);
}
