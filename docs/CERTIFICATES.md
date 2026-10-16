# orekaweb 证书更换指南

> 适用范围：orkweb-spring(Spring Boot 3,内嵌 Tomcat)
> 最后更新：2026-10-17

orekaweb 涉及**两个方向**的证书，先分清再操作：

| 方向 | 作用 | 配置文件 | 谁需要信任 |
|---|---|---|---|
| **服务器证书** | orekaweb 对外提供 HTTPS(:8443)出示的证书 | `server.ssl.*` | **浏览器/客户端**信任签发 CA |
| **客户端信任库** | orekaweb 作为客户端去调别的内部 HTTPS 服务时，验证对方证书 | JVM `javax.net.ssl.trustStore` | orekaweb 信任对方 CA |

```
浏览器 ──HTTPS──▶ orekaweb(:8443,出示服务器证书)
                    │
                    └──HTTPS──▶ 别的内部服务(orkaweb 需信任对方 CA)
```

---

## 一、当前证书位置(现状)

```
/home/nathan/cert/ork.p12                      ← 服务器证书(AVAYA CA 签发,orkweb.soundhao.com)
/home/nathan/certs/avaya-ca.crt                ← 自建 CA 根证书(PEM 源文件)
/home/nathan/certs/orekaweb-truststore.p12     ← orekaweb 客户端信任库(公共CA + AVAYA CA)
/home/nathan/oreka/orkweb-spring/src/main/resources/keystore/orkweb.p12  ← 开发用自签名(回退)
```

---

## 二、更换服务器证书(对外 HTTPS)

### 1. 准备新证书(PKCS12 格式)

如果拿到的是 PEM(`server.crt` + `server.key` + `ca.crt`),先打包成 p12:

```bash
openssl pkcs12 -export \
  -in server.crt -inkey server.key \
  -certfile ca.crt \
  -name <别名,建议用域名> \
  -out new-server.p12 -password pass:<密码>
```

> **必须含完整证书链**(`-certfile ca.crt`),否则浏览器无法验证。
> 已有 p12 直接跳到第 2 步。

### 2. 验证新证书有效

```bash
JH=/usr/lib/jvm/java-21-openjdk-amd64
$JH/bin/keytool -list -v -keystore /path/to/new-server.p12 \
  -storepass '<密码>' -storetype PKCS12 \
  | grep -iE "Alias|Owner:|Issuer:|Valid"
```

确认：别名、`Owner`(CN=你的域名)、`Issuer`(签发 CA)、有效期。

### 3. 改配置(二选一)

**方式 A — 环境变量(推荐,不改代码):**
```bash
ORKWEB_SSL_KEYSTORE=file:/path/to/new-server.p12
ORKWEB_SSL_KEYSTORE_PASSWORD=<密码>
ORKWEB_SSL_KEY_ALIAS=<别名>
```

**方式 B — 改 application.yml 默认值**(`orkweb-spring/src/main/resources/application.yml`):
```yaml
server:
  ssl:
    key-store: ${ORKWEB_SSL_KEYSTORE:file:/path/to/new-server.p12}
    key-store-password: ${ORKWEB_SSL_KEYSTORE_PASSWORD:<密码>}
    key-alias: ${ORKWEB_SSL_KEY_ALIAS:<别名>}
```
> ⚠️ **密码必须写真实值**,不能写 `***`(会被当成 YAML alias 导致启动崩溃 `ScannerException`)。
> 改完需重新 `mvn package`。

### 4. 重启验证

```bash
cd /home/nathan/oreka/orkweb-spring
systemctl --user stop orkweb-spring-dev && systemctl --user reset-failed orkweb-spring-dev
OREKA_AUDIO_PATH=/tmp/orkaudio-audio ./dev-run.sh start

# 验证出示的证书
echo | openssl s_client -connect localhost:8443 -servername <域名> 2>/dev/null \
  | openssl x509 -noout -subject -issuer -dates

# 验证证书链 + 用 CA 根证书验证
echo | openssl s_client -connect localhost:8443 -showcerts 2>/dev/null | grep -E "s:|i:"
curl -s -k -o /dev/null -w "%{http_code}\n" https://localhost:8443/
```

期望：`subject` 是新证书 CN,`issuer` 是签发 CA,`openssl -CAfile <根证书>` 验证返回 `Verify return code: 0 (ok)`。

### 5. 回退到自签名(开发用)

```bash
ORKWEB_SSL_KEYSTORE=classpath:keystore/orkweb.p12
ORKWEB_SSL_KEYSTORE_PASSWORD=changeit
ORKWEB_SSL_KEY_ALIAS=orkweb
```
(自签名证书已打包进 jar,随时可回退)

---

## 三、更换/新增信任的 CA(客户端信任库)

orekaweb 要调别的"用某 CA 签的证书"的内部 HTTPS 服务时,把那个 CA 的根证书加进信任库。

### 1. 信任库位置

`/home/nathan/certs/orekaweb-truststore.p12`(PKCS12,密码 `oreka123`)
当前内容 = JVM 默认公共 CA(121 条) + AVAYA CA(别名 `avaya-ca`)。

### 2. 新增一个 CA 根证书(PEM)

```bash
JH=/usr/lib/jvm/java-21-openjdk-amd64
$JH/bin/keytool -import -trustcacerts \
  -alias <取个别名,如 new-ca> \
  -file /path/to/new-ca.crt \
  -keystore /home/nathan/certs/orekaweb-truststore.p12 \
  -storetype PKCS12 -storepass oreka123 -noprompt

# 确认
$JH/bin/keytool -list -keystore /home/nathan/certs/orekaweb-truststore.p12 \
  -storepass oreka123 | grep <别名>
```

### 3. 从零重建信任库(含公共 CA)

如果信任库损坏或要重做:

```bash
JH=/usr/lib/jvm/java-21-openjdk-amd64
TS=/home/nathan/certs/orekaweb-truststore.p12
PW=oreka123
rm -f "$TS"

# a) 导入 JVM 默认全部公共 CA(保留公网 HTTPS 信任)
$JH/bin/keytool -importkeystore \
  -srckeystore "$JH/lib/security/cacerts" -srcstorepass changeit \
  -destkeystore "$TS" -deststoretype PKCS12 -deststorepass "$PW" -noprompt

# b) 逐个加自建 CA
$JH/bin/keytool -import -trustcacerts -alias avaya-ca \
  -file /home/nathan/certs/avaya-ca.crt \
  -keystore "$TS" -storetype PKCS12 -storepass "$PW" -noprompt

chmod 600 "$TS"
```

### 4. 生效方式

dev-run.sh 启动时已自动带上(文件存在才加):
```bash
-Djavax.net.ssl.trustStore=/home/nathan/certs/orekaweb-truststore.p12
-Djavax.net.ssl.trustStorePassword=oreka123
-Djavax.net.ssl.trustStoreType=PKCS12
```
改了信任库后**重启 orekaweb** 生效:
```bash
cd /home/nathan/oreka/orkweb-spring && OREKA_AUDIO_PATH=/tmp/orkaudio-audio ./dev-run.sh restart
```

> 环境变量可覆盖:`ORKWEB_TRUSTSTORE` / `ORKWEB_TRUSTSTORE_PASSWORD`。

---

## 四、让浏览器不再弹证书警告

把 CA 根证书(`avaya-ca.crt`)导入访问机器的信任库:

**Ubuntu(系统级,Chrome/Edge 生效):**
```bash
sudo cp /home/nathan/certs/avaya-ca.crt /usr/local/share/ca-certificates/avaya-ca.crt
sudo update-ca-certificates
```

**Firefox(用自己的库,单独导入):**
设置 → 隐私与安全 → 证书 → 查看证书 → 导入 → 选 `avaya-ca.crt` → 勾选"信任由此 CA 标识的网站"。

之后访问 `https://orkweb.soundhao.com:8443` 不再弹警告(前提是域名解析正确 + 证书未过期)。

---

## 五、常见问题

| 现象 | 原因 | 解决 |
|---|---|---|
| 启动报 `ScannerException: while scanning an alias` | yml 密码写成了 `***` | 密码写真实值 |
| 启动报 `keystore password was incorrect` | 密码错(注意拼写,如 Weclome/Welcome) | 用正确密码 |
| 浏览器仍弹警告 | CA 根证书没导入浏览器,或域名不匹配 | 导入根证书;访问域名=证书 CN/SAN |
| `Verify return code: 21 (unable to verify...)` | 服务器证书缺证书链 | p12 打包时加 `-certfile ca.crt` |
| orekaweb 调内部服务报 `PKIX path building failed` | 对方 CA 没在信任库 | 把对方 CA 根证书 import 进 truststore 并重启 |
| 8081 不响应 | 那是 orkaudio 上报专用 HTTP 口,浏览器/HTTPS 不走它 | 正常,Web 访问走 8443 |

---

## 六、关键文件速查

| 文件 | 说明 |
|---|---|
| `orkweb-spring/src/main/resources/application.yml` | `server.ssl.*` 服务器证书配置(env 可覆盖) |
| `orkweb-spring/dev-run.sh` | 启动脚本,自动加 trustStore JVM 参数 |
| `orkweb-spring/src/main/resources/keystore/orkweb.p12` | 开发用自签名证书(回退用) |
| `orkweb-spring/src/main/java/net/sf/oreka/spring/config/ExtraHttpConnectorConfig.java` | 额外 HTTP 8081 连接器(orkaudio 上报) |
