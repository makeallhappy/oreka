# Orkweb Spring 重写设计文档

**分支:** `SpringOrkweb`
**日期:** 2026-10-16
**状态:** ✅ 已完成(M1-M4 全部交付,端到端验证通过,2026-10-16 收官)

## 交付总览

| 里程碑 | commit | 内容 | 状态 |
|---|---|---|---|
| 设计 | `5decfe3` | 本文档 | ✅ |
| M1 | `93c1aed` `b944491` | orkbasej-jakarta 迁移 + 查询 API + 测试数据 | ✅ |
| M2 | `398ee3e` `090a4c1` `3b3862e` | /command 协议兼容 + 6 种消息 + 13/13 契约测试绿 | ✅ |
| M3 | `25c22b5` | 用户/Program CRUD + 音频流(Range) + JWT 认证 | ✅ |
| M4 | `690fec5` | Vue 3 前端(6 页面 + 波形播放器) | ✅ |
| 工具 | `0607309` | dev-run.sh 环境变量支持 | ✅ |

**端到端验证(2026-10-16):** 11 项 API 调用链全绿(登录→查询→播放→下载→CRUD→安全拦截)。

---

## 1. 现状盘点

### 1.1 代码规模

| 模块 | Java 文件 | 行数 | 职责 |
|---|---|---|---|
| orkweb | 24 | 1865 | Web UI(Tapestry 4.0.2,2006 年技术） |
| orktrack | 24 | 2661 | orkaudio 上报接收 + 业务逻辑 |
| orkbasej | 14 实体 + 工具 | ~4000 | 领域模型（JPA 注解，已是现代风格） |

### 1.2 领域模型（10 个业务实体）

```
OrkSession ──┐
             │ 1:N
OrkTape ─────┼─── OrkSegment(录音段,核心实体)
             │       │ N:1
OrkService ──┘       ├──→ OrkUser(说话人)
 (orkaudio实例)      ├──→ OrkPort(捕获端口/分机)
                     └──→ OrkProgram(录音计划)

OrkUser ──1:N── OrkLoginString(登录名/分机绑定)
OrkDomain(多租户域,当前实现较简单)
OrkPortFace(端口界面元数据)
```

### 1.3 关键发现

| 发现 | 影响 |
|---|---|
| 实体已是 `@Entity` JPA 注解 | **迁移顺**，几乎可直接复用，只需升级 `javax.persistence` → `jakarta.persistence` |
| orkaudio 协议是 **key=value 单行文本**(`message=tape recid=xx stage=START duration=30 ...`) | 不是 JSON!**必须保留 SingleLineSerializer 兼容层**,orkaudio C++ 端不动 |
| CommandServlet 仅 67 行，真正逻辑在 6 个 Manager | 重写重点是 Manager 层，不是 Servlet |
| 7 种消息：Init/Ping/Tape/Metadata/UserState/ConfigureLog/TapeResponse | Tape 消息是核心（录音生命周期 START/STOP/METADATA) |
| TapeManager 有"运行中 tape 匹配"逻辑（nativeCallId + 时间窗） | 处理 orkaudio 重连/重启后的 tape 合并，业务关键 |

---

## 2. 目标架构

### 2.1 技术栈

| 层 | 选型 | 版本 | 理由 |
|---|---|---|---|
| 语言 | Java | **21 LTS** | 当前最新 LTS,Spring Boot 3 要求 17+ |
| 框架 | Spring Boot | **3.4.x** | oreka 本来就用 Spring，生态延续 |
| ORM | Spring Data JPA(Hibernate 6) | - | 实体注解平移，`javax→jakarta` 包名替换 |
| 构建 | Maven | 3.9+ | 沿用，与现有模块一致 |
| 认证 | Spring Security + JWT | - | 前后端分离标配 |
| API 文档 | SpringDoc OpenAPI | 2.x | 自动生成 Swagger UI |
| 实时推送 | Spring WebSocket | - | 正在录音 session 实时刷新（新能力） |
| 数据库 | MySQL 8 | 沿用 | **schema 不动**,hbm2ddl=update |
| 前端 | Vue 3 + Vite + TS + Element Plus | - | 独立子项目，另立文档 |
| 音频播放 | wavesurfer.js | - | 波形可视化 |

### 2.2 模块结构（不动老代码，新建模块）

```
oreka/
├── orkbasej/          # 保留(实体+序列化器,重写复用)
├── orktrack/          # 保留(过渡期并存)
├── orkweb/            # 保留(过渡期并存)
└── orkweb-spring/     # ★ 新模块
    ├── pom.xml
    └── src/main/java/net/sf/oreka/spring/
        ├── OrkwebSpringApplication.java
        ├── config/          # Security/WebSocket/JPA 配置
        ├── domain/          # 复用 orkbasej 实体(依赖 orkbasej jar)
        ├── repository/      # Spring Data JPA Repository
        ├── service/         # 业务逻辑(迁移 6 个 Manager)
        ├── controller/      # REST API
        ├── legacy/          # ★ orkaudio 协议兼容层
        │   ├── SingleLineSerializer(复用 orkbasej)
        │   ├── messages/    # 7 种消息类(从 orktrack 迁移)
        │   └── CommandController  # POST /command(orkaudio 对接)
        ├── websocket/       # 实时推送
        └── dto/             # API 出入参
```

**关键决策：新模块依赖 orkbasej jar**（实体和 SingleLineSerializer 直接复用），不重写领域模型。

### 2.3 API 设计（REST)

#### 2.3.1 对外 API（前端用，JSON)

```
# 认证
POST   /api/v1/auth/login          # 返回 JWT
POST   /api/v1/auth/refresh

# 录音(Tape/Segment 查询是核心)
GET    /api/v1/segments            # 分页+搜索(时间/主被叫/方向/端口/时长)
GET    /api/v1/segments/{id}       # 详情
GET    /api/v1/segments/{id}/audio # 音频流(Range 支持)
GET    /api/v1/segments/{id}/download
GET    /api/v1/tapes/{id}
GET    /api/v1/tapes/{id}/audio

# 端口(分机)
GET    /api/v1/ports
GET    /api/v1/ports/{id}/segments

# 用户管理
GET    /api/v1/users               # admin
POST   /api/v1/users
PUT    /api/v1/users/{id}
DELETE /api/v1/users/{id}

# 录音计划(Program)
GET    /api/v1/programs
POST   /api/v1/programs
PUT    /api/v1/programs/{id}

# 实时状态
WS     /ws/live                    # 正在录音的 session 推送

# 服务实例(orkaudio)
GET    /api/v1/services            # 注册的 orkaudio 列表+在线状态
```

#### 2.3.2 orkaudio 对接 API（兼容层，key=value 文本）

```
POST/GET /command?message=tape&recid=xx&stage=START&...
         → 返回 key=value 单行响应(success=true comment=)
```

**这是协议的活化石，格式一个字不能改。** 迁移方案：
1. 从 orkbasej 复用 `SingleLineSerializer`/`ServletRequestSerializer`
2. 从 orktrack 迁移 7 个 messages 类（基本原样，只换包名）
3. 业务逻辑走新 service 层（Manager → @Service)

### 2.4 数据库策略

- **schema 完全不动** —— 新老系统共用一个 `oreka` 库
- 过渡期：老 orktrack 和新 orkweb-spring 可同时跑（不同 HTTP 路径），但**同一时间只让一个写 tape 表**（避免双重写入）
- Hibernate 实体映射验证：启动时 `hbm2ddl.auto=validate`（不改表，只验证）

---

## 3. 核心迁移难点与对策

### 3.1 orkaudio 协议兼容（风险最高）

| 难点 | 对策 |
|---|---|
| key=value 单行解析不是标准 HTTP | 复用 orkbasej 的 SingleLineSerializer，先写**协议契约测试**：用真实 orkaudio 抓的包做 fixture |
| TapeMessage 的 tape 合并逻辑（getBestMatchingRunningTape) | 原样迁移 TapeManager 的 HQL，改成 JPA Query，加单元测试覆盖 START→STOP 生命周期 |
| Direction/CaptureStage 等枚举序列化 | 枚举定义在 orkbasej，直接复用 |
| orkaudio 用 HTTP GET 带 query string 上报 | CommandController 同时支持 GET/POST |

### 3.2 音频文件服务

现状：orkaudio 写 WAV 到本地盘，orkweb 通过 HTTP 流式返回。
新方案：
- `GET /api/v1/tapes/{id}/audio` 后端从配置的 `AudioOutputPath` 读文件流
- 支持 HTTP Range（拖动播放必须）
- 后续可扩展 S3/MinIO 存储（接口抽象，本期不实现）

### 3.3 认证迁移

- 老系统：session + Tapestry 组件
- 新系统：JWT(access 2h + refresh 7d)
- 用户表 `orkuser` 不动，密码字段当前**明文**（老代码就这样）—— 重写时**顺手改成 BCrypt**，并写一次性迁移脚本

---

## 4. 实施计划（4 个里程碑）

### M1：骨架 + 只读查询（约 4 天）
- [ ] Spring Boot 3.4 + Java 21 模块初始化
- [ ] 依赖 orkbasej，实体 `javax→jakarta` 兼容验证（连现有库 `validate` 通过）
- [ ] 10 个实体的 Repository
- [ ] `GET /api/v1/segments` 分页搜索（核心查询，JPA Specification)
- [ ] JWT 登录（admin/admin 能登）
- [ ] Swagger UI
- **验收：** 用现有数据库能查出录音列表，API 文档可看

### M2：orkaudio 对接（约 4 天，风险最高）
- [ ] 迁移 7 个 messages + SingleLineSerializer 接线
- [ ] `POST /command` 全协议兼容
- [ ] TapeManager/PortManager/ServiceManager → @Service
- [ ] **协议契约测试**：模拟 orkaudio 发 START/STOP/METADATA
- [ ] docker-compose 里 orkaudio 切到新服务验证
- **验收：** docker 起真实 orkaudio，发起一通 SIP 呼叫，录音入库且或 audio 可播

### M3：管理功能（约 3 天）
- [ ] 用户 CRUD + 密码 BCrypt 迁移
- [ ] Program（录音计划）CRUD
- [ ] Port/Service 只读视图
- [ ] 音频流（Range)
- **验收：** 覆盖老 orkweb 全部功能

### M4：前端 + 实时（约 5 天）
- [ ] Vue 3 工程初始化（`orkweb-ui/`)
- [ ] 录音列表页（搜索/分页/排序）
- [ ] 录音详情 + wavesurfer 播放
- [ ] 用户/计划管理页
- [ ] WebSocket 实时录音状态
- **验收：** 替代老 orkweb UI，老系统下线

**总计：约 16 个工作日（1 个熟手全栈）**

---

## 5. 绞杀者迁移路径

```
阶段 0: 现状 ──→ mysql + tomcat(old orktrack+orkweb) + orkaudio
                     │
阶段 1: +orkweb-spring(只读 API,8081) ──→ 新前端内部试用
                     │
阶段 2: orkaudio 切到 orkweb-spring /command ──→ 老 orktrack 下线
                     │
阶段 3: 前端全量上线 ──→ 老 orkweb 下线,tomcat 只剩历史
                     │
阶段 4: tomcat 容器退役,docker-compose 只剩 mysql + orkweb-spring + orkaudio
```

每阶段可回退：切回 tomcat 即可，数据库全程不动。

---

## 6. 明确不做的（Out of Scope)

- ❌ orkaudio C++ 改动（协议冻结）
- ❌ 数据库 schema 变更
- ❌ 微服务拆分
- ❌ 多租户 SaaS 化（OrkDomain 保留现状）
- ❌ S3/对象存储（接口预留，本期本地盘）
- ❌ OpenID Connect / LDAP（预留 Security 配置点）

---

## 7. 风险清单

| 风险 | 等级 | 缓解 |
|---|---|---|
| orkaudio 协议兼容踩坑 | **高** | M2 先写协议契约测试；真实 docker orkaudio 端到端验证 |
| javax→jakarta 迁移实体映射不一致 | 中 | M1 用 `hbm2ddl=validate` 自动校验 |
| 老系统隐藏业务逻辑（Tapestry 页面里的隐式行为） | 中 | M3 对照老 UI 逐页走查 |
| 工期低估 | 中 | M1 完成后重估，M2 是最大不确定点 |
| 密码明文迁移破坏现有登录 | 低 | BCrypt 迁移脚本 + 双写过渡期 |

---

## 8. 下一步（本文档评审通过后）

1. 评审本文档 → 确认范围和技术选型
2. M1 开工：初始化 orkweb-spring 模块
3. 每完成一个 M 做一次 demo + 重估
