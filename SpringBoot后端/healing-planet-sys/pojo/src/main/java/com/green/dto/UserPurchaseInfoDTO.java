package com.green.dto;


import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@AllArgsConstructor
@Data
@NoArgsConstructor
@Builder(toBuilder = true)
public class UserPurchaseInfoDTO {
    private Boolean isPurchasedDevice;
    private String recommendTags;
    private List<String> plantNames;
}
