package com.nexarag.boot.integration;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.time.Duration;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * 外部基础设施冒烟测试，用于显式开启后验证 MySQL、Redis、Elasticsearch 和 Milvus 连通性。
 */
@Tag("integration")
class ExternalInfrastructureSmokeTest {

    private static final Duration NETWORK_TIMEOUT = Duration.ofSeconds(5);

    @Test
    void mysqlShouldConnectAndRunFlywayMigration() throws Exception {
        assumeIntegrationEnabled();
        String url = "jdbc:mysql://%s:%s/%s?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true"
                .formatted(property("nexa.mysql.host", "192.168.0.134"),
                        property("nexa.mysql.port", "3306"),
                        property("nexa.mysql.database", "nexa_rag"));

        Flyway flyway = Flyway.configure()
                .dataSource(url, property("nexa.mysql.username", "root"), property("nexa.mysql.password", ""))
                .locations("classpath:db/migration")
                .baselineOnMigrate(true)
                .validateOnMigrate(true)
                .load();

        // 1. 先执行真实迁移，验证 Flyway 可以接入当前数据库
        flyway.migrate();

        // 2. 再用最小查询确认连接可用
        try (Connection connection = DriverManager.getConnection(url,
                property("nexa.mysql.username", "root"), property("nexa.mysql.password", ""));
             ResultSet resultSet = connection.createStatement().executeQuery("SELECT 1")) {
            assertThat(resultSet.next()).isTrue();
            assertThat(resultSet.getInt(1)).isEqualTo(1);
        }
    }

    @Test
    void redisShouldAuthenticateAndRespondPong() throws Exception {
        assumeIntegrationEnabled();

        try (Socket socket = openSocket(property("nexa.redis.host", "192.168.0.134"),
                Integer.parseInt(property("nexa.redis.port", "6379")));
             BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
             BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8))) {
            String password = property("nexa.redis.password", "");
            if (!password.isBlank()) {
                // 1. 先认证 Redis，避免日志输出密码
                writer.write("*2\r\n$4\r\nAUTH\r\n$" + password.length() + "\r\n" + password + "\r\n");
                writer.flush();
                assertThat(reader.readLine()).startsWith("+OK");
            }

            // 2. 发送 PING 验证服务可用
            writer.write("*1\r\n$4\r\nPING\r\n");
            writer.flush();
            assertThat(reader.readLine()).isEqualTo("+PONG");
        }
    }

    @Test
    void elasticsearchShouldReturnClusterInfo() throws Exception {
        assumeIntegrationEnabled();
        String endpoint = "%s://%s:%s/"
                .formatted(property("nexa.elasticsearch.scheme", "http"),
                        property("nexa.elasticsearch.host", "192.168.0.134"),
                        property("nexa.elasticsearch.port", "9200"));
        String credentials = property("nexa.elasticsearch.username", "elastic")
                + ":" + property("nexa.elasticsearch.password", "");

        HttpRequest request = HttpRequest.newBuilder(URI.create(endpoint))
                .timeout(NETWORK_TIMEOUT)
                .header("Authorization", "Basic " + Base64.getEncoder()
                        .encodeToString(credentials.getBytes(StandardCharsets.UTF_8)))
                .GET()
                .build();

        // 1. 请求 ES 根路径，验证账号和服务状态
        HttpResponse<String> response = HttpClient.newHttpClient()
                .send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        // 2. 只断言状态和关键字段，不输出响应正文，避免日志泄露环境信息
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("cluster_name");
    }

    @Test
    void milvusDefaultPortShouldAcceptTcpConnection() throws Exception {
        assumeIntegrationEnabled();

        try (Socket socket = openSocket(property("nexa.milvus.host", "192.168.0.134"),
                Integer.parseInt(property("nexa.milvus.port", "19530")))) {
            // 1. Milvus 使用 gRPC，阶段一只验证默认端口可连接
            assertThat(socket.isConnected()).isTrue();
        }
    }

    /**
     * 判断是否开启真实环境集成测试。
     */
    private void assumeIntegrationEnabled() {
        assumeTrue(Boolean.parseBoolean(property("nexa.integration.enabled", "false")),
                "未开启真实环境集成测试");
    }

    /**
     * 打开带超时的 TCP 连接。
     *
     * @param host 主机地址
     * @param port 端口
     * @return 已连接 Socket
     * @throws Exception 连接失败时抛出
     */
    private Socket openSocket(String host, int port) throws Exception {
        Socket socket = new Socket();
        socket.connect(new InetSocketAddress(host, port), Math.toIntExact(NETWORK_TIMEOUT.toMillis()));
        socket.setSoTimeout(Math.toIntExact(NETWORK_TIMEOUT.toMillis()));
        return socket;
    }

    /**
     * 读取系统属性，缺省时再读取环境变量。
     *
     * @param key 属性键
     * @param defaultValue 默认值
     * @return 属性值
     */
    private String property(String key, String defaultValue) {
        String value = System.getProperty(key);
        if (value != null) {
            return value;
        }
        String envKey = key.replace('.', '_').replace('-', '_').toUpperCase();
        return System.getenv().getOrDefault(envKey, defaultValue);
    }
}
