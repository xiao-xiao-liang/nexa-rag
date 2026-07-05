package com.nexarag.model.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.nexarag.model.enums.ModelProvider;
import com.nexarag.model.enums.ModelType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 模型配置实体，对应 model_config 表，保存一个具体可调用模型实例。
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("model_config")
public class ModelConfig {

    /**
     * 模型配置ID。
     */
    @TableId("config_id")
    private Long configId;

    /**
     * 模型配置唯一标识。
     */
    private String configKey;

    /**
     * 模型类型。
     */
    private ModelType modelType;

    /**
     * 模型厂商。
     */
    private ModelProvider provider;

    /**
     * 模型服务地址。
     */
    private String baseUrl;

    /**
     * 加密后的 API Key。
     */
    private String apiKeyCipher;

    /**
     * API Key 脱敏展示值。
     */
    private String apiKeyMask;

    /**
     * 模型名称。
     */
    private String modelName;

    /**
     * 是否启用。
     */
    private Boolean enabled;

    /**
     * 超时时间，单位毫秒。
     */
    private Integer timeoutMs;

    /**
     * 最大重试次数。
     */
    private Integer maxRetries;

    /**
     * 单条模型配置版本。
     */
    @Version
    private Long version;

    /**
     * 扩展配置 JSON。
     */
    private String extraConfig;

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
}
