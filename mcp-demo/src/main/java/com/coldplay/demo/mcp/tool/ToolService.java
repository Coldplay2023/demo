package com.coldplay.demo.mcp.tool;

import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

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
            @ToolParam(description = "Optional tag to prove parameters arrive correctly") String tag) {
        String payload = "echo: " + text + " | tag: " + tag + " | at: " + Instant.now();
        log.info("Echo tool invoked: {}", payload);
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
            @ToolParam(description = "List of integers to sum") List<Integer> numbers) {
        int total = numbers == null ? 0 : numbers.stream().mapToInt(Integer::intValue).sum();
        String joined = numbers == null ? "" : numbers.stream().map(String::valueOf).collect(Collectors.joining(" + "));
        String response = joined + " = " + total;
        log.info("Sum tool invoked: {}", response);
        return response;
    }
}
