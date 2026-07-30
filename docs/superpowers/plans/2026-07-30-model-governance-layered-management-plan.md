# 模型治理分层配置 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 以数据库 CHAT 默认值和 MapStruct 局部映射实现可管理、可回退的配置级与路由级模型治理策略。

**Architecture:** 用独立的 MapStruct 转换器代替手写响应与请求赋值；治理服务按绑定目标提供查询、保存和路由标识迁移。运行时注册表保留关闭配置，使路由级策略能够覆盖或显式关闭配置级策略。

**Tech Stack:** Java 21、Spring Boot、MyBatis-Plus、MapStruct、Flyway、JUnit 5、Mockito。

---

### Task 1: MapStruct 映射与编译配置

**Files:**
- Modify: `pom.xml`
- Modify: `nexa-rag-model/pom.xml`
- Create: `nexa-rag-model/src/main/java/com/nexarag/model/converter/ModelGovernanceConfigConverter.java`
- Test: `nexa-rag-model/src/test/java/com/nexarag/model/converter/ModelGovernanceConfigConverterTest.java`

- [ ] **Step 1: 编写失败测试**

验证 `patch` 只复制非空策略字段、忽略实体身份和审计字段，并验证 `toResponse` 复制全部响应字段。

- [ ] **Step 2: 运行单测并确认失败**

运行：`mvn -pl nexa-rag-model -Dtest=ModelGovernanceConfigConverterTest test`

预期：因转换器不存在而编译失败。

- [ ] **Step 3: 添加 MapStruct 依赖及转换器**

在父 POM 的 `maven-compiler-plugin` 注解处理器中加入 MapStruct 和 Lombok 绑定；转换器使用 Spring 组件模型、`ReportingPolicy.ERROR` 和 `NullValuePropertyMappingStrategy.IGNORE`。

- [ ] **Step 4: 运行单测并确认通过**

运行：`mvn -pl nexa-rag-model -Dtest=ModelGovernanceConfigConverterTest test`

预期：测试通过，且生成实现类。

### Task 2: 数据库默认值与默认策略工厂

**Files:**
- Modify: `nexa-rag-boot/src/main/resources/db/schema/nexa_rag_schema.sql`
- Create: `nexa-rag-boot/src/main/resources/db/migration/V14__align_model_governance_defaults.sql`
- Modify: `nexa-rag-model/src/main/java/com/nexarag/model/governance/DefaultModelGovernancePolicyFactory.java`
- Test: `nexa-rag-model/src/test/java/com/nexarag/model/governance/DefaultModelGovernancePolicyFactoryTest.java`

- [ ] **Step 1: 编写失败测试**

验证 CHAT 工厂只生成治理 ID 和绑定身份，Embedding/Rerank 仅生成与 CHAT 数据库基线不同的字段。

- [ ] **Step 2: 运行单测并确认失败**

运行：`mvn -pl nexa-rag-model -Dtest=DefaultModelGovernancePolicyFactoryTest test`

预期：断言现有工厂写入 CHAT 全量字段而失败。

- [ ] **Step 3: 实现最小默认策略与迁移**

将 CHAT 基线写入建表脚本和 Flyway 迁移；工厂仅对 Embedding/Rerank 写差异值，不维护 CHAT 全量默认值。

- [ ] **Step 4: 运行单测并确认通过**

运行：`mvn -pl nexa-rag-model -Dtest=DefaultModelGovernancePolicyFactoryTest test`

预期：测试通过。

### Task 3: 治理服务、配置级和路由级接口

**Files:**
- Modify: `nexa-rag-model/src/main/java/com/nexarag/model/service/ModelGovernanceConfigService.java`
- Modify: `nexa-rag-model/src/main/java/com/nexarag/model/service/impl/ModelGovernanceConfigServiceImpl.java`
- Modify: `nexa-rag-model/src/main/java/com/nexarag/model/controller/ModelConfigController.java`
- Modify: `nexa-rag-model/src/main/java/com/nexarag/model/controller/ModelRouteController.java`
- Test: `nexa-rag-model/src/test/java/com/nexarag/model/service/impl/ModelGovernanceConfigServiceImplTest.java`
- Test: `nexa-rag-model/src/test/java/com/nexarag/model/controller/ModelConfigControllerTest.java`
- Test: `nexa-rag-model/src/test/java/com/nexarag/model/controller/ModelRouteControllerTest.java`

- [ ] **Step 1: 编写失败测试**

覆盖未保存配置返回 `null`、按路由 key 保存、空字段不覆盖、插入后回查默认值，以及路由端点按 `routeId` 解析绑定目标。

- [ ] **Step 2: 运行单测并确认失败**

运行：`mvn -pl nexa-rag-model -Dtest=ModelGovernanceConfigServiceImplTest,ModelConfigControllerTest,ModelRouteControllerTest test`

预期：因路由治理 API 和 MapStruct 注入缺失失败。

- [ ] **Step 3: 实现服务与接口**

用转换器替换 `toResponse`、`applyRequest` 和全字段更新；首次创建最小实体并回查。配置级请求与路由级请求均不接收绑定身份，路由控制器使用 `routeId` 获取 route key 后调用治理服务。

- [ ] **Step 4: 运行单测并确认通过**

运行：`mvn -pl nexa-rag-model -Dtest=ModelGovernanceConfigServiceImplTest,ModelConfigControllerTest,ModelRouteControllerTest test`

预期：测试通过。

### Task 4: 分层解析、改名迁移和刷新

**Files:**
- Modify: `nexa-rag-model/src/main/java/com/nexarag/model/governance/ModelGovernanceResolver.java`
- Modify: `nexa-rag-model/src/main/java/com/nexarag/model/registry/ModelRegistryRefresher.java`
- Modify: `nexa-rag-model/src/main/java/com/nexarag/model/service/impl/ModelRouteServiceImpl.java`
- Test: `nexa-rag-model/src/test/java/com/nexarag/model/governance/ModelGovernanceResolverTest.java`
- Test: `nexa-rag-model/src/test/java/com/nexarag/model/service/impl/ModelRouteServiceImplTest.java`

- [ ] **Step 1: 编写失败测试**

覆盖路由级覆盖、路由级缺失回退、路由级显式关闭，以及 route key 改名时迁移治理配置并只递增一次注册表版本。

- [ ] **Step 2: 运行单测并确认失败**

运行：`mvn -pl nexa-rag-model -Dtest=ModelGovernanceResolverTest,ModelRouteServiceImplTest test`

预期：现有全局绑定模式解析和未迁移 route key 导致断言失败。

- [ ] **Step 3: 实现解析与迁移**

解析器固定按 ROUTE、CONFIG 顺序查找；注册表刷新器保留关闭治理记录；路由更新事务中迁移 ROUTE 绑定并在提交后发布一次刷新消息。

- [ ] **Step 4: 运行单测并确认通过**

运行：`mvn -pl nexa-rag-model -Dtest=ModelGovernanceResolverTest,ModelRouteServiceImplTest test`

预期：测试通过。

### Task 5: 回归验证

**Files:**
- Modify: `docs/superpowers/specs/2026-07-30-model-governance-layered-management-design.md`
- Modify: `docs/superpowers/plans/2026-07-30-model-governance-layered-management-plan.md`

- [ ] **Step 1: 运行模块测试**

运行：`mvn -pl nexa-rag-model test`

预期：所有模型模块测试通过。

- [ ] **Step 2: 编译受影响模块**

运行：`mvn -pl nexa-rag-model,nexa-rag-boot -am -DskipTests compile`

预期：MapStruct 生成代码和 Flyway 资源均通过编译。

