package com.nexarag.boot.config;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * MyBatis-Plus 自动填充处理器，用于统一维护创建时间、更新时间和逻辑删除字段。
 */
@Component
public class MyBatisPlusAutoFillHandler implements MetaObjectHandler {

    @Override
    public void insertFill(MetaObject metaObject) {
        LocalDateTime now = LocalDateTime.now();

        // 1. 填充创建时间和更新时间
        fillStrategy(metaObject, "createTime", now);
        fillStrategy(metaObject, "updateTime", now);

        // 2. 填充逻辑删除默认值
        fillStrategy(metaObject, "delFlag", 0);
    }

    @Override
    public void updateFill(MetaObject metaObject) {
        // 1. 更新修改时间
        fillStrategy(metaObject, "updateTime", LocalDateTime.now());
    }

    /**
     * 填充逻辑删除字段。
     *
     * <p>MyBatis-Plus 默认逻辑删除不会自动写入 deleteTime，业务删除入口需要在执行逻辑删除更新前调用该方法。</p>
     *
     * @param metaObject 元对象
     */
    public void logicDeleteFill(MetaObject metaObject) {
        LocalDateTime now = LocalDateTime.now();

        // 1. 标记逻辑删除状态
        fillStrategy(metaObject, "delFlag", 1);

        // 2. 记录删除时间和更新时间
        fillStrategy(metaObject, "deleteTime", now);
        fillStrategy(metaObject, "updateTime", now);
    }
}
