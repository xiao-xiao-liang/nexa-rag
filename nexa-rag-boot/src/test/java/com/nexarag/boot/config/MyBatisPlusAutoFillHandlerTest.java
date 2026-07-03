package com.nexarag.boot.config;

import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.reflection.SystemMetaObject;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * MyBatis-Plus 自动填充处理器测试。
 */
class MyBatisPlusAutoFillHandlerTest {

    @Test
    void insertFillShouldInitializeBaseFields() {
        MyBatisPlusAutoFillHandler handler = new MyBatisPlusAutoFillHandler();
        TestEntity entity = new TestEntity();
        MetaObject metaObject = SystemMetaObject.forObject(entity);

        handler.insertFill(metaObject);

        assertThat(entity.createTime).isNotNull();
        assertThat(entity.updateTime).isNotNull();
        assertThat(entity.delFlag).isZero();
        assertThat(entity.deleteTime).isNull();
    }

    @Test
    void logicDeleteFillShouldSetDeleteTimeAndDelFlag() {
        MyBatisPlusAutoFillHandler handler = new MyBatisPlusAutoFillHandler();
        TestEntity entity = new TestEntity();
        MetaObject metaObject = SystemMetaObject.forObject(entity);

        handler.logicDeleteFill(metaObject);

        assertThat(entity.deleteTime).isNotNull();
        assertThat(entity.updateTime).isNotNull();
        assertThat(entity.delFlag).isEqualTo(1);
    }

    static class TestEntity {

        private LocalDateTime createTime;

        private LocalDateTime updateTime;

        private Integer delFlag;

        private LocalDateTime deleteTime;

        public LocalDateTime getCreateTime() {
            return createTime;
        }

        public void setCreateTime(LocalDateTime createTime) {
            this.createTime = createTime;
        }

        public LocalDateTime getUpdateTime() {
            return updateTime;
        }

        public void setUpdateTime(LocalDateTime updateTime) {
            this.updateTime = updateTime;
        }

        public Integer getDelFlag() {
            return delFlag;
        }

        public void setDelFlag(Integer delFlag) {
            this.delFlag = delFlag;
        }

        public LocalDateTime getDeleteTime() {
            return deleteTime;
        }

        public void setDeleteTime(LocalDateTime deleteTime) {
            this.deleteTime = deleteTime;
        }
    }
}
