package com.nexarag.model.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.nexarag.model.enums.ModelRouteStrategy;
import com.nexarag.model.enums.ModelType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 模型路由实体，对应 model_route 表，定义业务场景使用的模型路由。
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("model_route")
public class ModelRoute {

    /**
     * 模型路由ID。
     */
    @TableId("route_id")
    private Long routeId;

    /**
     * 业务使用的路由唯一标识。
     */
    private String routeKey;

    /**
     * 路由对应的模型类型。
     */
    private ModelType modelType;

    /**
     * 路由策略。
     */
    private ModelRouteStrategy strategy;

    /**
     * 是否启用。
     */
    private Boolean enabled;

    /**
     * 备注。
     */
    private String remark;

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
