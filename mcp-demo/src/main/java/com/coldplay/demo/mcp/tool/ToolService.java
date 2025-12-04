package com.coldplay.demo.mcp.tool;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import io.modelcontextprotocol.common.McpTransportContext;
import io.modelcontextprotocol.server.McpSyncServerExchange;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.mcp.McpToolUtils;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Service;

import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class ToolService {

    @Tool(
            name = "echo-intent",
            description = """
                    Echo helper to verify MCP transport works end-to-end.
                    Returns the text you send plus a timestamp so you can see fresh calls.
                    """
    )
    public String echoIntent(
            @ToolParam(description = "Any text you want the server to echo back") String text,
            @ToolParam(description = "Optional tag to prove parameters arrive correctly") String tag,
            ToolContext toolContext) {
        String payload = "echo: " + text + " | tag: " + tag + " | at: " + Instant.now();
        log.info("Echo tool invoked: {} | context={}", payload, buildContextSummary(toolContext));
        return payload;
    }

    @Tool(
            name = "sum-numbers",
            description = """
                    Sums a list of integers. Use it to sanity-check multi-parameter tool calls.
                    Returns both the expression and the computed result.
                    """
    )
    public String sumNumbers(
            @ToolParam(description = "List of integers to sum") List<Integer> numbers,
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
        McpSyncServerExchange serverExchange = exchange.get();
        McpTransportContext transportContext = serverExchange.transportContext();
        String apiKey = transportContext == null ? null : Objects.toString(transportContext.get("apikey"), null);
        String sessionId = serverExchange.sessionId();
        return "sessionId=" + sessionId + (apiKey == null ? "" : ", apikey=" + apiKey);
    }
}
