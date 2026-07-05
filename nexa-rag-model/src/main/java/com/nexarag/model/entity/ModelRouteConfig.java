package com.nexarag.model.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.nexarag.model.enums.ModelRouteRole;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 模型路由配置关联实体，对应 model_route_config 表，定义路由可选择的模型配置。
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("model_route_config")
public class ModelRouteConfig {

    /**
     * 模型路由配置关联ID。
     */
    @TableId("route_config_id")
    private Long routeConfigId;

    /**
     * 模型路由ID。
     */
    private Long routeId;

    /**
     * 模型配置ID。
     */
    private Long configId;

    /**
     * 路由下模型配置角色。
     */
    private ModelRouteRole role;

    /**
     * 优先级。
     */
    private Integer priority;

    /**
     * 权重。
     */
    private Integer weight;

    /**
     * 是否启用。
     */
    private Boolean enabled;

    /**
     * 创建时间。
     */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /**
     * 更新时间。
     */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    /**
     * 删除标记：0未删除，1已删除。
     */
    @TableLogic(value = "0", delval = "1")
    @TableField(fill = FieldFill.INSERT)
    private Integer delFlag;

    /**
     * 删除时间。
     */
    private LocalDateTime deleteTime;

    /**
     * 乐观锁版本号。
     */
    @Version
    private Integer version;
}
