package com.healingplanet.ai.ingestion;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MarkdownPlainTextSanitizerTest {

    @Test
    void shouldPreserveRangeSemanticsWhileRemovingMarkdownMarkers() {
        String markdown = """
                # 养护建议

                - 温度：18-28℃
                - 缓苗：2-3 天
                - 湿度：40%~60%
                - 英文：post-operative
                > **提示**：避免冷风直吹
                ~~删除~~后保留正文与`代码`
                """;

        String sanitized = MarkdownPlainTextSanitizer.strip(markdown);

        assertThat(sanitized).contains("18-28℃", "2-3 天", "40%~60%", "post-operative");
        assertThat(sanitized).contains("提示：避免冷风直吹", "删除后保留正文与代码");
        assertThat(sanitized).doesNotContain("#", "> ", "- 温度", "**", "~~", "`");
    }

    @Test
    void shouldKeepLinkTextAndDropImageMarkup() {
        String markdown = "参考[浇水说明](https://example.com) ![示意图](https://example.com/demo.png)";

        String sanitized = MarkdownPlainTextSanitizer.strip(markdown);

        assertThat(sanitized).isEqualTo("参考浇水说明");
    }
}
