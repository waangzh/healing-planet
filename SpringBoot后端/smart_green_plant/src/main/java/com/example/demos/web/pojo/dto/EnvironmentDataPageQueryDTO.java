package com.example.demos.web.pojo.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class EnvironmentDataPageQueryDTO {

    /**
     * 起始页
     */
    private int page;

    /**
     * 页大小
     */
    private int pageSize;

    /**
     * 用户绿植id
     */
    private Integer plantInstanceId;

    @JsonFormat(locale="zh", pattern="yyyy-MM-dd HH:mm:ss")
    private LocalDateTime startDate;//开始日期
    @JsonFormat(locale="zh", pattern="yyyy-MM-dd HH:mm:ss")
    private LocalDateTime endDate;//结束日期
}
