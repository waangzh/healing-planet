package com.example.demos.web.pojo.entity;


import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@AllArgsConstructor
@NoArgsConstructor
@Data
@Builder
public class BindingRecords {
    private Integer id;
    private Integer userId;
    private Integer deviceId;
    private String deviceKey;
}
