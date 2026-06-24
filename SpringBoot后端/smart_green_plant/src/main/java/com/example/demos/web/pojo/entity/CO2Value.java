package com.example.demos.web.pojo.entity;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class CO2Value {

    private Double value;
    @JsonFormat(locale="zh",timezone="GMT+8", pattern="yyyy-MM-dd HH:mm:ss")
    private LocalDateTime recordedTime;

}
