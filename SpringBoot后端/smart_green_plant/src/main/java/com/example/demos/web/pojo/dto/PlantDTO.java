package com.example.demos.web.pojo.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class PlantDTO {
    private String name;
    private String imgUrl;
    private String careInstructions;
}
