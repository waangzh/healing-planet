package com.green.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.green.entity.Tag;
import org.springframework.stereotype.Repository;

import java.util.List;


@Repository
public interface TagMapper extends BaseMapper<Tag> {


    void updateCount(List<String> ids);
}
