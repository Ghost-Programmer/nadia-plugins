package com.nadia.plugin.slack.markdown;

import org.commonmark.Extension;
import org.commonmark.ext.gfm.strikethrough.StrikethroughExtension;
import org.commonmark.ext.gfm.tables.TablesExtension;
import org.commonmark.node.Node;
import org.commonmark.parser.Parser;

import java.util.List;

/**
 * Parses CommonMark + GitHub Flavored Markdown (Strikethrough &amp; Tables) and
 * produces Slack {@code mrkdwn} text.
 */
public class SlackMarkdownConverter {

    private final Parser parser;

    /**
     * Constructs a {@code SlackMarkdownConverter} configured with Strikethrough and
     * Tables extensions.
     */
    public SlackMarkdownConverter() {
        List<Extension> extensions = List.of(
                StrikethroughExtension.create(),
                TablesExtension.create());
        this.parser = Parser.builder()
                .extensions(extensions)
                .build();
    }

    /**
     * Converts standard Markdown into Slack {@code mrkdwn} format.
     *
     * @param markdown standard Markdown input
     * @return converted Slack {@code mrkdwn} text
     */
    public String toSlackMrkdwn(String markdown) {
        if (markdown == null || markdown.trim().isEmpty()) {
            return "";
        }
        Node document = parser.parse(markdown);
        SlackMarkdownVisitor visitor = new SlackMarkdownVisitor();
        document.accept(visitor);
        return visitor.getSlackText();
    }
}
