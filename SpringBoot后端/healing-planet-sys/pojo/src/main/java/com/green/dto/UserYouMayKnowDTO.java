package com.green.dto;


import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class UserYouMayKnowDTO {
    private String id;
    private Boolean isPurchased = false;
    private String bio;
    private String message;
    private String plantsName;
}
