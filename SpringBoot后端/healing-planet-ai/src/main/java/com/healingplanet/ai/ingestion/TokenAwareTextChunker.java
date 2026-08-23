package com.healingplanet.ai.ingestion;

import com.knuddels.jtokkit.Encodings;
import com.knuddels.jtokkit.api.Encoding;
import com.knuddels.jtokkit.api.EncodingRegistry;
import com.knuddels.jtokkit.api.EncodingType;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 优先保留文档结构，仅在结构边界无法满足 token 预算时才硬切文本。
 */
final class TokenAwareTextChunker {
    static final int COMMUNITY_MAX_TOKENS = 800;
    static final int DISEASE_MAX_TOKENS = 800;
    private static final double HARD_SPLIT_OVERLAP_RATIO = 0.10d;
    private static final Pattern MARKDOWN_HEADING = Pattern.compile("(?m)^#{1,6}\\s+(.+?)\\s*$");
    private static final Encoding TOKEN_ENCODING = defaultEncoding();

    private TokenAwareTextChunker() {
    }

    static List<Chunk> split(String text, int maxTokens) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        if (maxTokens < 1) {
            throw new IllegalArgumentException("maxTokens 必须大于 0");
        }

        List<Chunk> result = new ArrayList<>();
        for (Section section : sections(text)) {
            result.addAll(splitSection(section, maxTokens));
        }
        return result;
    }

    static int countTokens(String text) {
        return text == null || text.isBlank() ? 0 : TOKEN_ENCODING.countTokens(text);
    }

    private static Encoding defaultEncoding() {
        EncodingRegistry registry = Encodings.newDefaultEncodingRegistry();
        return registry.getEncoding(EncodingType.CL100K_BASE);
    }

    private static List<Section> sections(String text) {
        String normalized = text.replace("\r\n", "\n").replace('\r', '\n').trim();
        Matcher matcher = MARKDOWN_HEADING.matcher(normalized);
        List<Section> result = new ArrayList<>();
        String heading = "";
        int contentStart = 0;
        while (matcher.find()) {
            addSection(result, heading, normalized.substring(contentStart, matcher.start()));
            heading = cleanInlineMarkdown(matcher.group(1));
            contentStart = matcher.end();
        }
        addSection(result, heading, normalized.substring(contentStart));
        return result;
    }

    private static void addSection(List<Section> target, String heading, String content) {
        String cleaned = cleanInlineMarkdown(content);
        if (!cleaned.isBlank()) {
            target.add(new Section(heading, cleaned));
        }
    }

    private static List<Chunk> splitSection(Section section, int maxTokens) {
        String prefix = section.heading().isBlank() ? "" : "小标题：%s\n\n".formatted(section.heading());
        int contentBudget = maxTokens - countTokens(prefix);
        if (contentBudget < 1) {
            return hardSplit(prefix + section.content(), maxTokens)
                    .stream().map(content -> new Chunk(content, section.heading())).toList();
        }

        List<String> pieces = new ArrayList<>();
        for (String paragraph : section.content().split("\\n{2,}")) {
            String value = paragraph.trim();
            if (!value.isBlank()) {
                pieces.addAll(splitParagraph(value, contentBudget));
            }
        }
        return combinePieces(pieces, prefix, contentBudget, section.heading());
    }

    private static List<Chunk> combinePieces(List<String> pieces, String prefix, int contentBudget, String section) {
        List<Chunk> result = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String piece : pieces) {
            String separator = current.length() == 0 ? "" : "\n\n";
            if (countTokens(current + separator + piece) > contentBudget && current.length() > 0) {
                result.add(new Chunk(prefix + current, section));
                current.setLength(0);
                separator = "";
            }
            current.append(separator).append(piece);
        }
        if (current.length() > 0) {
            result.add(new Chunk(prefix + current, section));
        }
        return result;
    }

    private static List<String> splitParagraph(String paragraph, int maxTokens) {
        List<String> result = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String sentence : sentences(paragraph)) {
            if (countTokens(sentence) > maxTokens) {
                if (current.length() > 0) {
                    result.add(current.toString());
                    current.setLength(0);
                }
                result.addAll(hardSplit(sentence, maxTokens));
            } else if (countTokens(current + sentence) > maxTokens && current.length() > 0) {
                result.add(current.toString());
                current.setLength(0);
                current.append(sentence);
            } else {
                current.append(sentence);
            }
        }
        if (current.length() > 0) {
            result.add(current.toString());
        }
        return result;
    }

    private static List<String> sentences(String value) {
        List<String> result = new ArrayList<>();
        int start = 0;
        for (int index = 0; index < value.length(); ) {
            int codePoint = value.codePointAt(index);
            index += Character.charCount(codePoint);
            if (codePoint == '。' || codePoint == '！' || codePoint == '？' || codePoint == '；'
                    || codePoint == '.' || codePoint == '!' || codePoint == '?' || codePoint == ';') {
                result.add(value.substring(start, index));
                start = index;
            }
        }
        if (start < value.length()) {
            result.add(value.substring(start));
        }
        return result;
    }

    private static List<String> hardSplit(String value, int maxTokens) {
        List<String> result = new ArrayList<>();
        String overlap = "";
        int cursor = 0;
        int overlapTokens = Math.max(1, (int) Math.ceil(maxTokens * HARD_SPLIT_OVERLAP_RATIO));
        while (cursor < value.length()) {
            StringBuilder current = new StringBuilder(overlap);
            int beforeNewContent = cursor;
            while (cursor < value.length()) {
                int next = cursor + Character.charCount(value.codePointAt(cursor));
                if (countTokens(current + value.substring(cursor, next)) > maxTokens && cursor > beforeNewContent) {
                    break;
                }
                current.append(value, cursor, next);
                cursor = next;
            }
            result.add(current.toString());
            overlap = tokenTail(current.toString(), overlapTokens);
        }
        return result;
    }

    private static String tokenTail(String value, int maxTokens) {
        int start = value.length();
        while (start > 0) {
            int candidate = value.offsetByCodePoints(start, -1);
            if (countTokens(value.substring(candidate)) > maxTokens) {
                break;
            }
            start = candidate;
        }
        return value.substring(start);
    }

    private static String cleanInlineMarkdown(String value) {
        return MarkdownPlainTextSanitizer.strip(value);
    }

    record Chunk(String content, String section) {
    }

    private record Section(String heading, String content) {
    }
}
