## 我的总结
这是一个mcp的demo代码，使用了webflux的方式。
1.其中支持sse和streamable http 两种方式传输。
2.通过自定义Provider可以在header中加入 apikey等认证方式，进而完成认证。
然后通过toolContext的方式获取请求头的信息。


## 关键类与核心代码
- `src/main/java/com/coldplay/demo/mcp/McpDemoApplication.java`：Spring Boot 启动入口，触发 WebFlux 与 MCP 相关自动装配。
```java
@SpringBootApplication
public class McpDemoApplication {
    public static void main(String[] args) {
        SpringApplication.run(McpDemoApplication.class, args);
    }
}
```
- `src/main/java/com/coldplay/demo/mcp/config/McpJsonMapperConfiguration.java`：在容器缺省时提供基于 Jackson 的 `McpJsonMapper`，供传输层序列化 MCP 协议。
```java
@Bean
@ConditionalOnMissingBean(McpJsonMapper.class)
public McpJsonMapper mcpJsonMapper(ObjectMapper objectMapper) {
    return new JacksonMcpJsonMapper(objectMapper);
}
```
- `src/main/java/com/coldplay/demo/mcp/config/McpWebFluxSseConfiguration.java`：默认（或 `spring.ai.mcp.server.protocol=SSE`）装配 SSE 传输，暴露 `/sse` 与 `/sse/message` 路由，并从请求头提取 `apikey` 注入 `McpTransportContext`。
```java
@Bean
@Primary
public WebFluxSseServerTransportProvider webFluxSseServerTransportProvider(
        McpJsonMapper jsonMapper, McpServerSseProperties properties) {
    String basePath = properties.getBaseUrl() == null ? "" : properties.getBaseUrl();
    return WebFluxSseServerTransportProvider.builder()
            .jsonMapper(jsonMapper)
            .basePath(basePath)
            .sseEndpoint(properties.getSseEndpoint())
            .messageEndpoint(properties.getSseMessageEndpoint())
            .keepAliveInterval(properties.getKeepAliveInterval())
            .contextExtractor(serverRequest -> {
                Map<String, Object> context = new HashMap<>();
                String apiKeyHeader = serverRequest.headers().firstHeader("apikey");
                if (apiKeyHeader != null) {
                    context.put("apikey", apiKeyHeader);
                }
                return context.isEmpty() ? McpTransportContext.EMPTY : McpTransportContext.create(context);
            })
            .build();
}

@Bean
public RouterFunction<?> reactiveMcpRouterFunction(WebFluxSseServerTransportProvider transportProvider) {
    return transportProvider.getRouterFunction();
}
```
- `src/main/java/com/coldplay/demo/mcp/config/McpWebFluxStreamableHttpConfiguration.java`：当 `spring.ai.mcp.server.protocol=STREAMABLE` 时启用 streamable-http（无状态）传输，支持自定义 MCP 端点与 `apikey` 上下文。
```java
@Bean
@Primary
public WebFluxStreamableServerTransportProvider webFluxStreamableServerTransportProvider(
        McpJsonMapper jsonMapper, McpServerStreamableHttpProperties properties) {
    String mcpEndpoint = properties.getMcpEndpoint() == null ? "/mcp" : properties.getMcpEndpoint();
    return WebFluxStreamableServerTransportProvider.builder()
            .jsonMapper(jsonMapper)
            .messageEndpoint(mcpEndpoint)
            .keepAliveInterval(properties.getKeepAliveInterval())
            .disallowDelete(properties.isDisallowDelete())
            .contextExtractor(serverRequest -> {
                Map<String, Object> context = new HashMap<>();
                String apiKeyHeader = serverRequest.headers().firstHeader("apikey");
                if (apiKeyHeader != null) {
                    context.put("apikey", apiKeyHeader);
                }
                return context.isEmpty() ? McpTransportContext.EMPTY : McpTransportContext.create(context);
            })
            .build();
}
```
- `src/main/java/com/coldplay/demo/mcp/tool/ToolProviders.java`：注册 `ToolService` 中带 `@Tool` 的方法为 MCP 工具回调。
```java
@Bean
public ToolCallbackProvider mcpTools(ToolService toolService) {
    return MethodToolCallbackProvider.builder()
            .toolObjects(toolService)
            .build();
}
```
- `src/main/java/com/coldplay/demo/mcp/tool/ToolService.java`：示例工具实现，校验参数与传输上下文；`echo-intent` 回显文本，`sum-numbers` 计算整数和。
```java
@Tool(name = "echo-intent", description = "Echo helper ...")
public String echoIntent(@ToolParam(description = "Any text you want the server to echo back") String text,
        @ToolParam(description = "Optional tag to prove parameters arrive correctly") String tag,
        ToolContext toolContext) {
    String payload = "echo: " + text + " | tag: " + tag + " | at: " + Instant.now();
    log.info("Echo tool invoked: {} | context={}", payload, buildContextSummary(toolContext));
    return payload;
}

@Tool(name = "sum-numbers", description = "Sums a list of integers ...")
public String sumNumbers(@ToolParam(description = "List of integers to sum") List<Integer> numbers,
        ToolContext toolContext) {
    int total = numbers == null ? 0 : numbers.stream().mapToInt(Integer::intValue).sum();
    String joined = numbers == null ? "" : numbers.stream().map(String::valueOf).collect(Collectors.joining(" + "));
    String response = joined + " = " + total;
    log.info("Sum tool invoked: {} | context={}", response, buildContextSummary(toolContext));
    return response;
}

private String buildContextSummary(ToolContext toolContext) {
    if (toolContext == null || toolContext.getContext() == null || toolContext.getContext().isEmpty()) {
        return "none";
    }
    Optional<McpSyncServerExchange> exchange = McpToolUtils.getMcpExchange(toolContext);
    if (exchange.isEmpty()) {
        return "toolContext(without MCP exchange)";
    }
    McpTransportContext transportContext = exchange.get().transportContext();
    String apiKey = transportContext == null ? null : Objects.toString(transportContext.get("apikey"), null);
    String sessionId = exchange.get().sessionId();
    return "sessionId=" + sessionId + (apiKey == null ? "" : ", apikey=" + apiKey);
}
```
- `src/main/java/com/coldplay/demo/mcp/web/InfoController.java`：健康检查控制器，提供 `/health`。
```java
@RestController
public class InfoController {
    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of("status", "ok");
    }
}
```
- `src/main/resources/application.yml`：默认使用 SSE 传输，端口 8085，开启工具变更通知与 keep-alive。
```yml
spring:
  application:
    name: mcp-demo
  ai:
    mcp:
      server:
        protocol: SSE
        name: mcp-demo-server
        version: 1.0.0
        type: SYNC
        instructions: "Demo MCP server providing echo/sum tools; default transport is SSE."
        tool-change-notification: true
        sse-endpoint: /sse
        sse-message-endpoint: /sse/message
        keep-alive-interval: 30s
server:
  port: 8085
management:
  endpoints:
    web:
      exposure:
        include: health,info
```
- `src/main/resources/application-stream.yml`：`stream` profile 下改用无状态 streamable-http 传输，端口 8086，自定义 MCP 端点 `/api/mcp`。
```yml
spring:
  config:
    activate:
      on-profile: stream
  ai:
    mcp:
      server:
        protocol: STATELESS # 无状态，每次请求独立
        name: mcp-demo-server
        version: 1.0.0
        type: SYNC
        instructions: "Demo MCP server providing echo/sum tools; streamable-http transport."
        tool-change-notification: true
        streamable-http:
          mcp-endpoint: /api/mcp
          keep-alive-interval: 30s
server:
  port: 8086
management:
  endpoints:
    web:
      exposure:
        include: health,info
```

## 调用/依赖关系概览
- 启动装配：`McpDemoApplication` 引导 -> Spring Boot 自动装配加载 WebFlux、Spring AI MCP -> `McpJsonMapperConfiguration` 提供协议序列化 -> 按 `spring.ai.mcp.server.protocol` 选择 SSE（默认）或 streamable-http 传输路由。
- 请求入口与路由：SSE 通过 `/sse` + `/sse/message`，streamable-http 通过 `/api/mcp`；`contextExtractor` 从 header 注入 `apikey` 至 `McpTransportContext`，并维护 keep-alive。
- 工具链：传输层解析 MCP 请求 -> `ToolCallbackProvider` 绑定到 `ToolService` -> 执行 `echo-intent`/`sum-numbers`，读取 `ToolContext`（sessionId、apikey）并记录日志 -> 结果经对应传输返回。
- 运维探针：`InfoController` `/health` + Actuator `health/info` 端点用于基础可观测性。

## maven 核心代码引用的包和版本
- `org.springframework.boot:spring-boot-starter-parent` 3.3.4（管理 WebFlux、Actuator、测试等依赖）。
- Java 17（`<java.version>17</java.version>`）。
- `org.springframework.ai:spring-ai-bom` 1.1.0-M3；使用 `spring-ai-starter-mcp-server` 与 `spring-ai-starter-mcp-server-webflux`。
- `org.projectlombok:lombok` 1.18.34（provided，用于日志注解等）。
- 构建插件：`org.springframework.boot:spring-boot-maven-plugin`（随父 POM 版本）。
