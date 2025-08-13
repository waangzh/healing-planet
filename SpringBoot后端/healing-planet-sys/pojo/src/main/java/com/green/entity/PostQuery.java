package com.green.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;
import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class PostQuery {

    /**
     * 作者id
     */
    private String authorId;

    /**
     * 查询起始时间
     */
    private Date startTime;

    /**
     * 查询结束时间
     */
    private Date endTime;


    /**
     * 标签id
     */
    private List<String> tagIds;

    /**
     * 文章状态 1-已发布 0-审核中 -1-未审核 -2-未通过
     */
    private Integer status;

    /**
     * 页码
     */
    private Integer pageNo;

    /**
     * 页大小
     */
    private Integer pageSize;

}
