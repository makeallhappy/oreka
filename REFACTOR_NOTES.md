# Oreka 现代化改造记录

**日期:** 2026-10-16
**分支:** `updateOldLibs`
**仓库:** git@github.com:makeallhappy/oreka.git
**目标:** 在现代 Linux(Ubuntu 26.04 / Debian 13)上编译并容器化运行 oreka 全栈

---

## Commit 记录（全部 push 成功）

| Commit | 内容 | 文件变化 |
|---|---|---|
| `6cbc1de` | **C++**: 移除 silk/opus/livestream 插件，g729 用系统 bcg729,Boost 1.90 兼容修复 | 12 files, +20/-28 |
| `b8edc9e` | **文档**: BUILD_UBUNTU26.md 构建说明 | 1 file, +118 |
| `13df757` | **Java**: Lombok 1.16.20→1.18.30(JDK 11 修复）+ docker-compose(tomcat9+mysql8) | 7 files, +152/-7 |
| `63f11cf` | **Docker**: orkaudio 服务（Debian 13 多阶段构建 + host 网络抓包） | 3 files, +148 |

---

## C++ 部分（commit 6cbc1de)

### 移除的插件
- **SILK** — 只用于 Skype，已过时；删 SUBDIRS + AC_OUTPUT
- **Opus** — 避免编译 2017 老版本；删 7 处构建引用 + BatchProcessing.cpp(FfOpus 降级 WAV)+ OrkAudio.cpp 删注册；保留枚举做防御
- **LiveStream** — 实时流推送，默认禁用；删 SUBDIRS + AC_OUTPUT

### 保留的插件
- **G.729** — Ubuntu 26.04 官方仓库有 `libbcg729-dev 1.1.1`,Makefile.am 去 `/opt/bcg729` 硬编码改系统路径
- libvoip / libgenerator / librtpmixer

### Boost 1.90 兼容（3 处）
| 文件 | 旧 API | 新 API |
|---|---|---|
| `orkbasecxx/SslUtils.h` | `io_service` | `io_context` |
| `orkbasecxx/SslUtils.cpp` | `resolver::query`/`iterator` | `results_type`/`endpoint` |
| `orkbasecxx/configure.in` | `-lboost_system` | 空（1.69+ header-only) |

### 产物
- `orkbasecxx/.libs/liborkbase.so.0.0.0` (23MB)
- `orkaudio/orkaudio` (2.1MB)
- 4 插件：libvoip / libgenerator / librtpmixer / libg729codec

---

## Java 部分（commit 13df757)

- **环境**: OpenJDK 11.0.32.1 + Maven 3.9.12
- **Lombok**: 1.16.20 → 1.18.30（三个 pom.xml;1.16.20 在 JDK 11 上 `TypeTags` 崩溃）
- **测试**: `-DskipTests` 绕过（orktrack 测试硬编码 `C:/oreka/...` Windows 路径）
- **产物**: orkbasej.jar (85KB) / orktrack.war (21.6MB) / orkweb.war (23.3MB)

---

## Docker 运行环境（commit 13df757 + 63f11cf)

### 三服务编排（docker-compose.yml)

| 服务 | 镜像 | 网络 | 说明 |
|---|---|---|---|
| mysql | mysql:8.0 | bridge | oreka 库，root/oreka123,healthcheck 通过后 tomcat 启动 |
| tomcat | tomcat:9.0-jdk11-temurin | bridge | 挂两个 war + docker-config→/etc/oreka,8080 |
| orkaudio | oreka-orkaudio:latest | **host** | NET_RAW/NET_ADMIN 抓包，跟踪 localhost:8080 |

### orkaudio 镜像（Debian 13 多阶段）
- builder: debian:13 + 全部 -dev 包，编译 orkbasecxx+orkaudio
- runtime: debian:13 + 运行库，COPY 二进制+插件，93MB content
- G.729:`libbcg729-dev` 官方包，链接 `/lib/x86_64-linux-gnu/libbcg729.so.0` ✅
- USTC apt 镜像（deb.debian.org 直连卡死）
- 配置文件 `docker-config/orkaudio-config.xml`(Devices=any,Tracker=localhost:8080)

### 验证结果
- `docker compose up -d` 全绿（mysql healthy → tomcat → orkaudio)
- orktrack `/orktrack/command` 200 / orkweb `/orkweb/app` 200(Welcome to OrkWeb)
- MySQL oreka 库已建，hibernate hbm2ddl=update 自动建表
- **Web UI 登录：admin / admin** ✅ 用户已验证

### 国内镜像源（/etc/docker/daemon.json)
docker.1ms.run / docker.m.daocloud.io / docker.xuanyuan.me
(USTC docker 镜像站已挂，域名解析失败）

---

## 遗留问题（非阻塞）

- orktrack 单元测试硬编码 Windows 路径（`-DskipTests` 绕过）
- orkbasecxx 未 `make install`（容器内已 COPY 进 /usr/lib，宿主机靠 LIBRARY_PATH)
- orkaudio 实机抓包未验证（需要真实 SIP/RTP 流量）
