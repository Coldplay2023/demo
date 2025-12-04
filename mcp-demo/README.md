# MCP Demo Module

Minimal Spring Boot MCP server that exposes two tools (`echo-intent`, `sum-numbers`) and can run with SSE (default) or streamable-http transport.

## Build
```
cd mcp-demo
mvn clean package
```

## Run (SSE default)
```
cd mcp-demo
mvn spring-boot:run
# MCP endpoints:
#   Tool list / calls (SSE): /sse and /sse/message
#   Health: http://localhost:8085/healthz
```

## Run (streamable-http)
```
cd mcp-demo
mvn spring-boot:run -Dspring-boot.run.profiles=stream
# MCP endpoint: http://localhost:8086/api/mcp
# Health: http://localhost:8086/healthz
```

## How it works
- Uses the reactive WebFlux/Netty stack (`spring-boot-starter-webflux` + `spring-ai-starter-mcp-server-webflux`); endpoints are the same as before.
- `application.yml` sets `spring.ai.mcp.server.protocol=SSE`; `application-stream.yml` switches to `STREAMABLE` when profile `stream` is active.
- `McpWebFluxSseConfiguration` and `McpWebFluxStreamableHttpConfiguration` customize transports and propagate `scope` headers into `McpTransportContext`.
- `ToolService` defines the MCP tools, and `ToolProviders` registers them via `MethodToolCallbackProvider`.
