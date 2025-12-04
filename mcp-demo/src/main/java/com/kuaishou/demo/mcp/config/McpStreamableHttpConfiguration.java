package com.kuaishou.demo.mcp.config;

import java.util.HashMap;
import java.util.Map;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import io.modelcontextprotocol.common.McpTransportContext;
import io.modelcontextprotocol.json.McpJsonMapper;
import io.modelcontextprotocol.server.transport.WebMvcStreamableServerTransportProvider;
import org.springframework.ai.mcp.server.common.autoconfigure.properties.McpServerStreamableHttpProperties;

@Configuration
@ConditionalOnProperty(
        prefix = "spring.ai.mcp.server",
        name = "protocol",
        havingValue = "STREAMABLE"
)
@EnableConfigurationProperties(McpServerStreamableHttpProperties.class)
public class McpStreamableHttpConfiguration {

    @Bean
    @Primary
    public WebMvcStreamableServerTransportProvider webMvcStreamableServerTransportProvider(McpJsonMapper jsonMapper,
            McpServerStreamableHttpProperties properties) {
        String mcpEndpoint = properties.getMcpEndpoint() == null ? "/mcp" : properties.getMcpEndpoint();
        return WebMvcStreamableServerTransportProvider.builder()
                .jsonMapper(jsonMapper)
                .mcpEndpoint(mcpEndpoint)
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
}
