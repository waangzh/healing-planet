package com.healingplanet.ai.ingestion;

import java.util.regex.Pattern;

/**
 * 将知识库中的常见 Markdown 标记转换为纯文本，同时保留正文中的范围符号等字面内容。
 */
final class MarkdownPlainTextSanitizer {
    private static final Pattern IMAGE = Pattern.compile("!\\[[^]]*]\\([^)]*\\)");
    private static final Pattern LINK = Pattern.compile("\\[([^]]+)]\\([^)]*\\)");
    private static final Pattern ATX_HEADING = Pattern.compile("(?m)^ {0,3}#{1,6}[ \\t]+");
    private static final Pattern BLOCK_QUOTE = Pattern.compile("(?m)^ {0,3}> ?");
    private static final Pattern BULLET_LIST = Pattern.compile("(?m)^ {0,3}[-+*][ \\t]+(?:\\[[ xX]]\\][ \\t]+)?");
    private static final Pattern ORDERED_LIST = Pattern.compile("(?m)^ {0,3}\\d{1,9}[.)][ \\t]+(?:\\[[ xX]]\\][ \\t]+)?");
    private static final Pattern THEMATIC_BREAK = Pattern.compile("(?m)^[ \\t]{0,3}(?:(?:-\\s*){3,}|(?:_\\s*){3,}|(?:\\*\\s*){3,})$");
    private static final Pattern CODE_SPAN = Pattern.compile("(?s)(?<!`)`+\\s*(.*?)\\s*`+(?!`)");
    private static final Pattern STRONG_ASTERISK = Pattern.compile("(?<!\\*)\\*\\*(?=\\S)(.+?)(?<=\\S)\\*\\*(?!\\*)");
    private static final Pattern EMPHASIS_ASTERISK = Pattern.compile("(?<!\\*)\\*(?=\\S)(.+?)(?<=\\S)\\*(?!\\*)");
    private static final Pattern STRONG_UNDERSCORE = Pattern.compile("(?<![\\p{L}\\p{N}_])__(?=\\S)(.+?)(?<=\\S)__(?![\\p{L}\\p{N}_])");
    private static final Pattern EMPHASIS_UNDERSCORE = Pattern.compile("(?<![\\p{L}\\p{N}_])_(?=\\S)(.+?)(?<=\\S)_(?![\\p{L}\\p{N}_])");
    private static final Pattern STRIKETHROUGH = Pattern.compile("~~(?=\\S)(.+?)(?<=\\S)~~");
    private static final Pattern TRAILING_SPACES = Pattern.compile("(?m)[ \\t]+$");
    private static final Pattern LEADING_SPACES = Pattern.compile("(?m)^[ \\t]+");
    private static final Pattern INLINE_SPACES = Pattern.compile("[ \\t]+");

    private MarkdownPlainTextSanitizer() {
    }

    static String strip(String value) {
        if (value == null) {
            return "";
        }
        String sanitized = value.replace("\r\n", "\n").replace('\r', '\n');
        sanitized = IMAGE.matcher(sanitized).replaceAll(" ");
        sanitized = LINK.matcher(sanitized).replaceAll("$1");
        sanitized = ATX_HEADING.matcher(sanitized).replaceAll("");
        sanitized = BLOCK_QUOTE.matcher(sanitized).replaceAll("");
        sanitized = BULLET_LIST.matcher(sanitized).replaceAll("");
        sanitized = ORDERED_LIST.matcher(sanitized).replaceAll("");
        sanitized = THEMATIC_BREAK.matcher(sanitized).replaceAll("");
        sanitized = replaceRepeatedly(sanitized, CODE_SPAN, "$1");
        sanitized = replaceRepeatedly(sanitized, STRIKETHROUGH, "$1");
        sanitized = replaceRepeatedly(sanitized, STRONG_ASTERISK, "$1");
        sanitized = replaceRepeatedly(sanitized, STRONG_UNDERSCORE, "$1");
        sanitized = replaceRepeatedly(sanitized, EMPHASIS_ASTERISK, "$1");
        sanitized = replaceRepeatedly(sanitized, EMPHASIS_UNDERSCORE, "$1");
        sanitized = TRAILING_SPACES.matcher(sanitized).replaceAll("");
        sanitized = LEADING_SPACES.matcher(sanitized).replaceAll("");
        sanitized = INLINE_SPACES.matcher(sanitized).replaceAll(" ");
        return sanitized.trim();
    }

    private static String replaceRepeatedly(String value, Pattern pattern, String replacement) {
        String current = value;
        while (true) {
            String next = pattern.matcher(current).replaceAll(replacement);
            if (next.equals(current)) {
                return next;
            }
            current = next;
        }
    }
}
