package net.sf.oreka.spring.config;

import org.apache.catalina.connector.Connector;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.context.annotation.Configuration;

/**
 * 附加 HTTP 连接器 —— 主端口走 HTTPS(8443,Web UI / API),
 * 另开一个纯 HTTP 端口(默认 8081)专门给 orkaudio 的 /command 上报用。
 *
 * 原因:orkaudio 是 C++ 客户端,上报协议是 HTTP(不能改),所以保留一个
 * 明文 HTTP 口仅供它用;人用的 Web/UI 全部走 HTTPS。
 *
 * 关掉方式:ORKWEB_EXTRA_HTTP_PORT 置空或 <=0。
 */
@Configuration
public class ExtraHttpConnectorConfig implements WebServerFactoryCustomizer<TomcatServletWebServerFactory> {

    @Value("${ORKWEB_EXTRA_HTTP_PORT:8081}")
    private int extraHttpPort;

    @Override
    public void customize(TomcatServletWebServerFactory factory) {
        if (extraHttpPort > 0) {
            Connector connector = new Connector(TomcatServletWebServerFactory.DEFAULT_PROTOCOL);
            connector.setPort(extraHttpPort);
            factory.addAdditionalTomcatConnectors(connector);
        }
    }
}
