## 我的总结
（预留空白，后续补充项目整体理解与注意事项）

## 关键类与核心代码
- `com.coldplay.demo.mcp.McpDemoApplication`：Spring Boot 启动入口，启动后按配置加载 WebFlux + MCP 相关 Bean。
  ```java
  @SpringBootApplication
  public class McpDemoApplication {
      public static void main(String[] args) {
          SpringApplication.run(McpDemoApplication.class, args);
      }
  }
  ```
- `com.coldplay.demo.mcp.config.McpJsonMapperConfiguration`：提供 MCP 使用的 `McpJsonMapper`，默认基于 Jackson。
  ```java
  @Bean
  @ConditionalOnMissingBean(McpJsonMapper.class)
  public McpJsonMapper mcpJsonMapper(ObjectMapper objectMapper) {
      return new JacksonMcpJsonMapper(objectMapper);
  }
  ```
- `com.coldplay.demo.mcp.config.McpWebFluxSseConfiguration`：当 `spring.ai.mcp.server.protocol=SSE`（默认）时，装配 SSE 传输，暴露 RouterFunction。
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
                  String scopeHeader = serverRequest.headers().firstHeader("scope");
                  if (scopeHeader != null) {
                      context.put("scope", scopeHeader);
                  }
                  return context.isEmpty() ? McpTransportContext.EMPTY : McpTransportContext.create(context);
              })
              .build();
  }
  ```
- `com.coldplay.demo.mcp.config.McpWebFluxStreamableHttpConfiguration`：当协议为 `STREAMABLE` 时启用，配置可流式的 HTTP 传输。
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
                  String scopeHeader = serverRequest.headers().firstHeader("scope");
                  if (scopeHeader != null) {
                      context.put("scope", scopeHeader);
                  }
                  return context.isEmpty() ? McpTransportContext.EMPTY : McpTransportContext.create(context);
              })
              .build();
  }
  ```
- `com.coldplay.demo.mcp.tool.ToolProviders`：注册基于方法反射的 MCP 工具回调提供者，扫描 `ToolService` 上的 @Tool 方法。
  ```java
  @Bean
  public ToolCallbackProvider mcpTools(ToolService toolService) {
      return MethodToolCallbackProvider.builder()
              .toolObjects(toolService)
              .build();
  }
  ```
- `com.coldplay.demo.mcp.tool.ToolService`：具体工具实现，提供 echo 与求和能力，日志记录每次调用。
  ```java
  @Tool(name = "echo-intent", description = "Echo helper ...")
  public String echoIntent(@ToolParam String text, @ToolParam String tag) {
      String payload = "echo: " + text + " | tag: " + tag + " | at: " + Instant.now();
      log.info("Echo tool invoked: {}", payload);
      return payload;
  }

  @Tool(name = "sum-numbers", description = "Sums a list of integers...")
  public String sumNumbers(@ToolParam List<Integer> numbers) {
      int total = numbers == null ? 0 : numbers.stream().mapToInt(Integer::intValue).sum();
      String joined = numbers == null ? "" : numbers.stream()
              .map(String::valueOf)
              .collect(Collectors.joining(" + "));
      String response = joined + " = " + total;
      log.info("Sum tool invoked: {}", response);
      return response;
  }
  ```
- `com.coldplay.demo.mcp.web.InfoController`：健康检查控制器，提供 `/health`。
  ```java
  @GetMapping("/health")
  public Map<String, String> health() {
      return Map.of("status", "ok");
  }
  ```

## 调用/依赖关系概览
- 启动链路：`McpDemoApplication` -> Spring Boot 自动装配 -> `McpJsonMapperConfiguration` 提供 JSON 映射 -> 根据 `spring.ai.mcp.server.protocol` 选择 `McpWebFluxSseConfiguration`（默认）或 `McpWebFluxStreamableHttpConfiguration` 装配传输 Provider 与 RouterFunction。
- 传输与工具注册：WebFlux Router 接收 MCP 请求 -> 传入的 headers 经过 `contextExtractor` 构造成 `McpTransportContext` -> Spring AI MCP Server 发现 `ToolProviders` 注册的工具回调 -> 调度到 `ToolService` 中标注的 @Tool 方法。
- 工具调用链：客户端调用 MCP 工具 -> transport 将 payload 解析为方法参数 -> `ToolService.echoIntent` 或 `sumNumbers` 执行业务逻辑并记录日志 -> 结果经 MCP JSON Mapper 序列化返回客户端。
- 管理/探针：`InfoController` 提供 `/health`；Actuator 通过 `management.endpoints.web.exposure.include=health,info` 暴露基础健康检查。
