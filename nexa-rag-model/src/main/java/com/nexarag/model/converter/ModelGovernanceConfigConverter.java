package com.nexarag.model.converter;

import com.nexarag.model.dto.ModelGovernanceConfigRequest;
import com.nexarag.model.dto.ModelGovernanceConfigResponse;
import com.nexarag.model.entity.ModelGovernanceConfig;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;
import org.mapstruct.ReportingPolicy;

/**
 * 模型治理配置转换器，负责响应组装和局部更新字段映射。
 */
@Mapper(
        componentModel = MappingConstants.ComponentModel.SPRING,
        unmappedTargetPolicy = ReportingPolicy.ERROR
)
public interface ModelGovernanceConfigConverter {

    /**
     * 将治理配置实体转换为前端响应对象。
     *
     * @param source 治理配置实体
     * @return 治理配置响应，source 为 null 时返回 null
     */
    ModelGovernanceConfigResponse toResponse(ModelGovernanceConfig source);

    /**
     * 将请求中的非空策略字段应用到已保存实体。
     *
     * @param source 前端局部更新请求
     * @param target 已保存治理配置
     */
    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "governanceId", ignore = true)
    @Mapping(target = "bindingMode", ignore = true)
    @Mapping(target = "configId", ignore = true)
    @Mapping(target = "routeKey", ignore = true)
    @Mapping(target = "createTime", ignore = true)
    @Mapping(target = "updateTime", ignore = true)
    @Mapping(target = "delFlag", ignore = true)
    @Mapping(target = "deleteTime", ignore = true)
    void patch(ModelGovernanceConfigRequest source, @MappingTarget ModelGovernanceConfig target);
}
