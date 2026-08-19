package com.green.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class PlantAliasVO {

    private String id;
    private String alias;
    private String aliasType;
    private Boolean enabled;
}
