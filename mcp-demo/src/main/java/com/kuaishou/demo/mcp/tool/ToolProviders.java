package com.kuaishou.demo.mcp.tool;

import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ToolProviders {

    @Bean
    public ToolCallbackProvider mcpTools(ToolService toolService) {
        return MethodToolCallbackProvider.builder()
                .toolObjects(toolService)
                .build();
    }
}
