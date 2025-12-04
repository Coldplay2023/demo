package com.kuaishou.demo.mcp.config;

import java.util.HashMap;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.ServerResponse;

import io.modelcontextprotocol.common.McpTransportContext;
import io.modelcontextprotocol.json.McpJsonMapper;
import io.modelcontextprotocol.server.transport.WebMvcSseServerTransportProvider;
import org.springframework.ai.mcp.server.common.autoconfigure.properties.McpServerSseProperties;

@Configuration
@ConditionalOnProperty(
        prefix = "spring.ai.mcp.server",
        name = "protocol",
        havingValue = "SSE",
        matchIfMissing = true
)
@EnableConfigurationProperties(McpServerSseProperties.class)
public class McpSseConfiguration {

    @Bean
    @Primary
    public WebMvcSseServerTransportProvider webMvcSseServerTransportProvider(McpJsonMapper jsonMapper,
            McpServerSseProperties properties) {
        String baseUrl = properties.getBaseUrl() == null ? "" : properties.getBaseUrl();
        return WebMvcSseServerTransportProvider.builder()
                .jsonMapper(jsonMapper)
                .baseUrl(baseUrl)
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

    @Bean
    public RouterFunction<ServerResponse> mvcMcpRouterFunction(WebMvcSseServerTransportProvider transportProvider) {
        return transportProvider.getRouterFunction();
    }
}
