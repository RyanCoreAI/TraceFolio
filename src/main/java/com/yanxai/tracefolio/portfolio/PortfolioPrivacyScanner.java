package com.yanxai.tracefolio.portfolio;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

@Service
public class PortfolioPrivacyScanner {

    private static final Pattern WINDOWS_ABSOLUTE_PATH = Pattern.compile("[A-Za-z]:[\\\\/][^\\s)`\"']+");
    private static final Pattern SECRET_ASSIGNMENT = Pattern.compile(
            "(?i)(\\b(?:[a-z0-9]+_)*(?:api[_-]?key|token|password|passwd|secret|client[_-]?secret|private[_-]?key)\\b[\"']?\\s*[:=]\\s*)"
                    + "(?:\"(?:\\\\.|[^\"\\\\\\r\\n])*\"|'(?:\\\\.|[^'\\\\\\r\\n])*'|[^\\s'\"`,}]+)");

    public PrivacyScanResult scan(String content) {
        String text = content == null ? "" : content;
        List<String> issues = new ArrayList<>();
        if (WINDOWS_ABSOLUTE_PATH.matcher(text).find()) {
            issues.add("content contains Windows absolute path");
        }
        if (SECRET_ASSIGNMENT.matcher(text).find()) {
            issues.add("content may contain secret-like assignment");
        }
        if (text.contains("DEEPSEEK_API_KEY=") || text.contains("GITHUB_TOKEN=")) {
            issues.add("content contains environment variable assignment example");
        }
        return new PrivacyScanResult(issues.isEmpty() ? "PASS" : "REDACTED", issues);
    }

    public String redact(String content) {
        String text = content == null ? "" : content;
        text = WINDOWS_ABSOLUTE_PATH.matcher(text).replaceAll("[REDACTED_LOCAL_PATH]");
        text = SECRET_ASSIGNMENT.matcher(text).replaceAll("$1[REDACTED]");
        text = text.replaceAll("DEEPSEEK_API_KEY=[^\\s`]+", "DEEPSEEK_API_KEY=[REDACTED]");
        text = text.replaceAll("GITHUB_TOKEN=[^\\s`]+", "GITHUB_TOKEN=[REDACTED]");
        return text;
    }
}
