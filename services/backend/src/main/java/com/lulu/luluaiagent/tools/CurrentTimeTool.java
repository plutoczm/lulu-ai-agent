package com.lulu.luluaiagent.tools;

import org.springframework.ai.tool.annotation.Tool;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Exact local date/time from the host system clock.
 */
public class CurrentTimeTool {

    private static final DateTimeFormatter FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss EEEE XXX", Locale.SIMPLIFIED_CHINESE);

    @Tool(description = "Get the exact current local date and time from the system clock")
    public String currentDateTime() {
        ZonedDateTime now = ZonedDateTime.now();
        return "当前系统时间：" + now.format(FORMATTER)
                + "；时区：" + now.getZone().getId();
    }
}
