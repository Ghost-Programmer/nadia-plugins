package com.nadia.plugin.slack.markdown;

import org.commonmark.ext.gfm.strikethrough.Strikethrough;
import org.commonmark.ext.gfm.tables.TableBlock;
import org.commonmark.ext.gfm.tables.TableBody;
import org.commonmark.ext.gfm.tables.TableCell;
import org.commonmark.ext.gfm.tables.TableHead;
import org.commonmark.ext.gfm.tables.TableRow;
import org.commonmark.node.AbstractVisitor;
import org.commonmark.node.BlockQuote;
import org.commonmark.node.BulletList;
import org.commonmark.node.Code;
import org.commonmark.node.CustomBlock;
import org.commonmark.node.CustomNode;
import org.commonmark.node.Emphasis;
import org.commonmark.node.FencedCodeBlock;
import org.commonmark.node.HardLineBreak;
import org.commonmark.node.Heading;
import org.commonmark.node.HtmlBlock;
import org.commonmark.node.HtmlInline;
import org.commonmark.node.Image;
import org.commonmark.node.IndentedCodeBlock;
import org.commonmark.node.Link;
import org.commonmark.node.ListItem;
import org.commonmark.node.Node;
import org.commonmark.node.OrderedList;
import org.commonmark.node.Paragraph;
import org.commonmark.node.SoftLineBreak;
import org.commonmark.node.StrongEmphasis;
import org.commonmark.node.Text;
import org.commonmark.node.ThematicBreak;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * Custom CommonMark AST visitor that transforms standard Markdown AST nodes
 * into Slack {@code mrkdwn} text.
 *
 * <ul>
 * <li>Bold: {@code **text**} -&gt; {@code *text*}</li>
 * <li>Italics: {@code *text*} -&gt; {@code _text_}</li>
 * <li>Strikethrough: {@code ~~text~~} -&gt; {@code ~text~}</li>
 * <li>Links: {@code [label](url)} -&gt; {@code <url|label>}</li>
 * <li>Headings: {@code # Header} -&gt; {@code *Header*}</li>
 * <li>Code &amp; Fenced Blocks, Blockquotes, Nested Bullet/Ordered Lists, and
 * ASCII Monospace Tables</li>
 * </ul>
 */
public class SlackMarkdownVisitor extends AbstractVisitor {

    private final StringBuilder builder = new StringBuilder();
    private final Deque<ListState> listStack = new ArrayDeque<>();

    /**
     * Constructs a new {@code SlackMarkdownVisitor}.
     */
    public SlackMarkdownVisitor() {
        super();
    }

    private static final class ListState {
        final boolean isOrdered;
        int nextNumber;

        ListState(boolean isOrdered, int startNumber) {
            this.isOrdered = isOrdered;
            this.nextNumber = startNumber;
        }
    }

    /**
     * Returns the accumulated Slack {@code mrkdwn} string.
     *
     * @return trimmed Slack {@code mrkdwn} text
     */
    public String getSlackText() {
        return builder.toString().trim();
    }

    @Override
    public void visit(Text text) {
        builder.append(text.getLiteral());
    }

    @Override
    public void visit(StrongEmphasis strongEmphasis) {
        builder.append("*");
        visitChildren(strongEmphasis);
        builder.append("*");
    }

    @Override
    public void visit(Emphasis emphasis) {
        builder.append("_");
        visitChildren(emphasis);
        builder.append("_");
    }

    @Override
    public void visit(CustomNode customNode) {
        if (customNode instanceof Strikethrough) {
            builder.append("~");
            visitChildren(customNode);
            builder.append("~");
        } else {
            visitChildren(customNode);
        }
    }

    @Override
    public void visit(CustomBlock customBlock) {
        if (customBlock instanceof TableBlock tableBlock) {
            renderTable(tableBlock);
        } else {
            visitChildren(customBlock);
        }
    }

    @Override
    public void visit(Link link) {
        String destination = link.getDestination();
        String title = extractChildText(link);

        if (destination == null || destination.trim().isEmpty()) {
            builder.append(title);
            return;
        }

        if (title.isEmpty() || title.equals(destination)) {
            builder.append("<").append(destination).append(">");
        } else {
            builder.append("<").append(destination).append("|").append(title).append(">");
        }
    }

    @Override
    public void visit(Image image) {
        String destination = image.getDestination();
        String title = extractChildText(image);
        if (title.isEmpty()) {
            title = image.getTitle() != null ? image.getTitle() : "Image";
        }
        if (destination != null && !destination.isEmpty()) {
            builder.append("<").append(destination).append("|").append(title).append(">");
        } else {
            builder.append("[").append(title).append("]");
        }
    }

    @Override
    public void visit(Heading heading) {
        ensureTrailingNewlines(2);
        builder.append("*");
        visitChildren(heading);
        builder.append("*\n\n");
    }

    @Override
    public void visit(Code code) {
        builder.append("`").append(code.getLiteral()).append("`");
    }

    @Override
    public void visit(FencedCodeBlock fencedCodeBlock) {
        ensureTrailingNewlines(2);
        builder.append("```\n");
        String literal = fencedCodeBlock.getLiteral();
        builder.append(literal != null ? literal : "");
        if (literal == null || !literal.endsWith("\n")) {
            builder.append("\n");
        }
        builder.append("```\n\n");
    }

    @Override
    public void visit(IndentedCodeBlock indentedCodeBlock) {
        ensureTrailingNewlines(2);
        builder.append("```\n");
        String literal = indentedCodeBlock.getLiteral();
        builder.append(literal != null ? literal : "");
        if (literal == null || !literal.endsWith("\n")) {
            builder.append("\n");
        }
        builder.append("```\n\n");
    }

    @Override
    public void visit(BlockQuote blockQuote) {
        ensureTrailingNewlines(2);
        String quoteText = extractChildText(blockQuote);
        String[] lines = quoteText.split("\n");
        for (String line : lines) {
            builder.append("> ").append(line).append("\n");
        }
        builder.append("\n");
    }

    @Override
    public void visit(BulletList bulletList) {
        ensureTrailingNewlines(listStack.isEmpty() ? 2 : 1);
        listStack.push(new ListState(false, 1));
        visitChildren(bulletList);
        listStack.pop();
        if (listStack.isEmpty()) {
            builder.append("\n");
        }
    }

    @Override
    public void visit(OrderedList orderedList) {
        ensureTrailingNewlines(listStack.isEmpty() ? 2 : 1);
        listStack.push(new ListState(true, orderedList.getStartNumber()));
        visitChildren(orderedList);
        listStack.pop();
        if (listStack.isEmpty()) {
            builder.append("\n");
        }
    }

    @Override
    public void visit(ListItem listItem) {
        ListState currentState = listStack.peek();
        int depth = Math.max(0, listStack.size() - 1);
        String indent = "  ".repeat(depth);

        if (currentState != null && currentState.isOrdered) {
            builder.append(indent).append(currentState.nextNumber++).append(". ");
        } else {
            builder.append(indent).append("• ");
        }

        visitChildren(listItem);
        if (!builder.toString().endsWith("\n")) {
            builder.append("\n");
        }
    }

    @Override
    public void visit(Paragraph paragraph) {
        visitChildren(paragraph);
        if (!(paragraph.getParent() instanceof ListItem)) {
            builder.append("\n\n");
        }
    }

    @Override
    public void visit(SoftLineBreak softLineBreak) {
        builder.append("\n");
    }

    @Override
    public void visit(HardLineBreak hardLineBreak) {
        builder.append("\n");
    }

    @Override
    public void visit(ThematicBreak thematicBreak) {
        ensureTrailingNewlines(2);
        builder.append("──────────\n\n");
    }

    @Override
    public void visit(HtmlBlock htmlBlock) {
        builder.append(htmlBlock.getLiteral());
    }

    @Override
    public void visit(HtmlInline htmlInline) {
        builder.append(htmlInline.getLiteral());
    }

    private String extractChildText(Node parent) {
        SlackMarkdownVisitor subVisitor = new SlackMarkdownVisitor();
        for (Node child = parent.getFirstChild(); child != null; child = child.getNext()) {
            child.accept(subVisitor);
        }
        return subVisitor.getSlackText();
    }

    private void ensureTrailingNewlines(int count) {
        if (builder.isEmpty()) {
            return;
        }
        int existing = 0;
        int i = builder.length() - 1;
        while (i >= 0 && builder.charAt(i) == '\n') {
            existing++;
            i--;
        }
        while (existing < count) {
            builder.append("\n");
            existing++;
        }
    }

    private void renderTable(TableBlock tableBlock) {
        List<List<String>> rows = new ArrayList<>();
        List<Integer> colWidths = new ArrayList<>();

        for (Node blockChild = tableBlock.getFirstChild(); blockChild != null; blockChild = blockChild.getNext()) {
            if (blockChild instanceof TableHead || blockChild instanceof TableBody) {
                for (Node rowNode = blockChild.getFirstChild(); rowNode != null; rowNode = rowNode.getNext()) {
                    if (rowNode instanceof TableRow) {
                        List<String> rowCells = new ArrayList<>();
                        int colIndex = 0;
                        for (Node cellNode = rowNode.getFirstChild(); cellNode != null; cellNode = cellNode.getNext()) {
                            if (cellNode instanceof TableCell) {
                                String cellText = extractChildText(cellNode).trim();
                                rowCells.add(cellText);
                                int cellLen = cellText.length();
                                if (colIndex >= colWidths.size()) {
                                    colWidths.add(cellLen);
                                } else {
                                    colWidths.set(colIndex, Math.max(colWidths.get(colIndex), cellLen));
                                }
                                colIndex++;
                            }
                        }
                        rows.add(rowCells);
                    }
                }
            }
        }

        if (rows.isEmpty()) {
            return;
        }

        ensureTrailingNewlines(2);
        builder.append("```\n");
        for (int r = 0; r < rows.size(); r++) {
            List<String> row = rows.get(r);
            StringBuilder line = new StringBuilder();
            for (int c = 0; c < row.size(); c++) {
                String cell = row.get(c);
                int width = colWidths.get(c);
                line.append(String.format("%-" + width + "s", cell));
                if (c < row.size() - 1) {
                    line.append(" | ");
                }
            }
            builder.append(line).append("\n");

            if (r == 0 && rows.size() > 1) {
                StringBuilder sep = new StringBuilder();
                for (int c = 0; c < colWidths.size(); c++) {
                    sep.append("-".repeat(Math.max(1, colWidths.get(c))));
                    if (c < colWidths.size() - 1) {
                        sep.append("-+-");
                    }
                }
                builder.append(sep).append("\n");
            }
        }
        builder.append("```\n\n");
    }
}
