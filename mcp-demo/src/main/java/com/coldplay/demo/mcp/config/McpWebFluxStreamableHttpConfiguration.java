package com.coldplay.demo.mcp.config;

import java.util.HashMap;
import java.util.Map;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import io.modelcontextprotocol.common.McpTransportContext;
import io.modelcontextprotocol.json.McpJsonMapper;
import io.modelcontextprotocol.server.transport.WebFluxStreamableServerTransportProvider;
import org.springframework.ai.mcp.server.common.autoconfigure.properties.McpServerStreamableHttpProperties;

/**
 * WebFlux streamable HTTP transport wiring (Netty/reactive stack).
 */
@Configuration
@ConditionalOnProperty(
        prefix = "spring.ai.mcp.server",
        name = "protocol",
        havingValue = "STREAMABLE"
)
@EnableConfigurationProperties(McpServerStreamableHttpProperties.class)
public class McpWebFluxStreamableHttpConfiguration {

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
}
