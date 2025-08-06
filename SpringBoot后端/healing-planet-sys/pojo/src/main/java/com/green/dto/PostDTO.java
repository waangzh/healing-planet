package com.green.dto;


import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;
import java.util.List;

@AllArgsConstructor
@NoArgsConstructor
@Data
public class PostDTO {


    /**
     * 文章id
     */
    private String id;

    /**
     * 标题
     */
    private String title;
    /**
     * markdown
     */
    private String content;

    /**
     * 文章封面配图
     */
    private String coverImg;

    /**
     * 文章标签
     */
    private List<String> tags;


    /**
     * 修改时间
     */
    private Date modifyTime;
}
