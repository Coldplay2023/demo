package com.coldplay.demo.mcp.config;

import java.util.HashMap;
import java.util.Map;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.web.reactive.function.server.RouterFunction;

import io.modelcontextprotocol.common.McpTransportContext;
import io.modelcontextprotocol.json.McpJsonMapper;
import io.modelcontextprotocol.server.transport.WebFluxSseServerTransportProvider;
import org.springframework.ai.mcp.server.common.autoconfigure.properties.McpServerSseProperties;

/**
 * WebFlux SSE transport wiring (Netty/reactive stack).
 */
@Configuration
@ConditionalOnProperty(
        prefix = "spring.ai.mcp.server",
        name = "protocol",
        havingValue = "SSE",
        matchIfMissing = true
)
@EnableConfigurationProperties(McpServerSseProperties.class)
public class McpWebFluxSseConfiguration {

    @Bean
    @Primary
    public WebFluxSseServerTransportProvider webFluxSseServerTransportProvider(McpJsonMapper jsonMapper,
            McpServerSseProperties properties) {
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

    @Bean
    public RouterFunction<?> reactiveMcpRouterFunction(WebFluxSseServerTransportProvider transportProvider) {
        return transportProvider.getRouterFunction();
    }
}
