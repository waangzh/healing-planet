package com.green.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@TableName(value = "plant_aliases")
public class PlantAlias {

    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private String id;

    @TableField(value = "plant_id")
    private String plantId;

    @TableField(value = "alias")
    private String alias;

    @TableField(value = "normalized_alias")
    private String normalizedAlias;

    @TableField(value = "alias_type")
    private String aliasType;

    @TableField(value = "enabled")
    private Boolean enabled;
}
