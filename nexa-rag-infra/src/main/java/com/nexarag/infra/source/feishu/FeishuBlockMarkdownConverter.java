package com.nexarag.infra.source.feishu;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 将飞书 Docx 的扁平 Block 列表按树形关系转换为 Markdown。
 *
 * <p>飞书接口以扁平列表返回 Block，父子关系由 {@code children} 与 {@code parent_id} 表达。
 * 本转换器从页面根节点遍历，避免容器块和子块重复输出。</p>
 */
@Component
public class FeishuBlockMarkdownConverter {

    private static final int PAGE_BLOCK_TYPE = 1;
    private static final int HEADING_START_BLOCK_TYPE = 3;
    private static final int HEADING_END_BLOCK_TYPE = 11;
    private static final int BULLET_BLOCK_TYPE = 12;
    private static final int ORDERED_BLOCK_TYPE = 13;
    private static final int CODE_BLOCK_TYPE = 14;
    private static final int QUOTE_BLOCK_TYPE = 15;
    private static final int TODO_BLOCK_TYPE = 17;
    private static final int CALLOUT_BLOCK_TYPE = 19;
    private static final int DIVIDER_BLOCK_TYPE = 22;
    private static final int GRID_BLOCK_TYPE = 24;
    private static final int GRID_COLUMN_BLOCK_TYPE = 25;
    private static final int TABLE_BLOCK_TYPE = 31;
    private static final int TABLE_CELL_BLOCK_TYPE = 32;
    private static final int VIEW_BLOCK_TYPE = 33;
    private static final int QUOTE_CONTAINER_BLOCK_TYPE = 34;
    private static final int MAX_MARKDOWN_HEADING_LEVEL = 6;

    private static final Map<Integer, String> NON_TEXT_BLOCK_LABELS = Map.ofEntries(
            Map.entry(18, "飞书多维表格"),
            Map.entry(20, "飞书会话卡片"),
            Map.entry(21, "飞书流程图"),
            Map.entry(23, "飞书文件"),
            Map.entry(26, "飞书内嵌内容"),
            Map.entry(27, "飞书图片"),
            Map.entry(28, "飞书第三方组件"),
            Map.entry(29, "飞书思维笔记"),
            Map.entry(30, "飞书电子表格"),
            Map.entry(35, "飞书任务"),
            Map.entry(36, "飞书 OKR"),
            Map.entry(37, "飞书 OKR 目标"),
            Map.entry(38, "飞书 OKR 关键结果"),
            Map.entry(39, "飞书 OKR 进展"),
            Map.entry(40, "飞书文档组件"),
            Map.entry(41, "飞书 Jira 问题"));

    /**
     * 按页面根节点的子节点顺序转换全部 Block。
     *
     * @param blocks 飞书接口返回的扁平 Block 列表
     * @return 标准 Markdown 内容
     */
    public String convert(List<JsonNode> blocks) {
        if (blocks == null || blocks.isEmpty()) {
            return "";
        }

        // 1. 建立 Block 索引，后续按照 children 指针恢复文档树。
        Map<String, JsonNode> blocksById = indexBlocks(blocks);
        RenderContext context = new RenderContext(blocksById);
        List<String> lines = new ArrayList<>();

        // 2. 优先从页面根节点遍历；异常快照没有根节点时再按顶层节点容错处理。
        blocks.stream()
                .filter(block -> block.path("block_type").asInt() == PAGE_BLOCK_TYPE)
                .map(block -> renderChildren(block, context))
                .filter(StringUtils::hasText)
                .forEach(lines::add);
        if (lines.isEmpty()) {
            findTopLevelBlockIds(blocks, blocksById).stream()
                    .map(blockId -> renderBlock(blockId, context))
                    .filter(StringUtils::hasText)
                    .forEach(lines::add);
        }
        return String.join("\n\n", lines);
    }

    private Map<String, JsonNode> indexBlocks(List<JsonNode> blocks) {
        Map<String, JsonNode> blocksById = new LinkedHashMap<>();
        for (JsonNode block : blocks) {
            String blockId = block.path("block_id").asText();
            if (StringUtils.hasText(blockId)) {
                blocksById.putIfAbsent(blockId, block);
            }
        }
        return blocksById;
    }

    private List<String> findTopLevelBlockIds(List<JsonNode> blocks, Map<String, JsonNode> blocksById) {
        Set<String> childBlockIds = new HashSet<>();
        blocks.forEach(block -> block.path("children").forEach(child -> childBlockIds.add(child.asText())));
        List<String> topLevelBlockIds = blocks.stream()
                .map(block -> block.path("block_id").asText())
                .filter(blocksById::containsKey)
                .filter(blockId -> !childBlockIds.contains(blockId))
                .toList();
        return topLevelBlockIds.isEmpty() ? new ArrayList<>(blocksById.keySet()) : topLevelBlockIds;
    }

    private String renderBlock(String blockId, RenderContext context) {
        if (!context.markRendered(blockId)) {
            return "";
        }
        JsonNode block = context.getBlock(blockId);
        if (block == null) {
            return "";
        }
        int blockType = block.path("block_type").asInt();
        if (blockType == PAGE_BLOCK_TYPE || isContainerBlock(blockType)) {
            return renderContainerBlock(block, context);
        }
        if (blockType == TABLE_BLOCK_TYPE) {
            return renderTable(block, context);
        }
        if (blockType == TABLE_CELL_BLOCK_TYPE) {
            return "";
        }
        return renderContentBlock(block, blockType);
    }

    private boolean isContainerBlock(int blockType) {
        return blockType == CALLOUT_BLOCK_TYPE || blockType == GRID_BLOCK_TYPE || blockType == GRID_COLUMN_BLOCK_TYPE
                || blockType == VIEW_BLOCK_TYPE || blockType == QUOTE_CONTAINER_BLOCK_TYPE;
    }

    private String renderContainerBlock(JsonNode block, RenderContext context) {
        String content = renderChildren(block, context);
        if (!StringUtils.hasText(content)) {
            return "";
        }
        if (block.path("block_type").asInt() == CALLOUT_BLOCK_TYPE) {
            return toBlockQuote(content);
        }
        return content;
    }

    private String renderChildren(JsonNode block, RenderContext context) {
        List<String> children = new ArrayList<>();
        block.path("children").forEach(child -> {
            String content = renderBlock(child.asText(), context);
            if (StringUtils.hasText(content)) {
                children.add(content);
            }
        });
        return String.join("\n\n", children);
    }

    private String renderContentBlock(JsonNode block, int blockType) {
        String text = extractText(block);
        if (blockType >= HEADING_START_BLOCK_TYPE && blockType <= HEADING_END_BLOCK_TYPE) {
            return StringUtils.hasText(text) ? "#".repeat(Math.min(blockType - 2, MAX_MARKDOWN_HEADING_LEVEL)) + " " + text : "";
        }
        if (!StringUtils.hasText(text)) {
            if (blockType == DIVIDER_BLOCK_TYPE) {
                return "---";
            }
            return nonTextPlaceholder(blockType);
        }
        return switch (blockType) {
            case BULLET_BLOCK_TYPE -> "- " + text;
            case ORDERED_BLOCK_TYPE -> "1. " + text;
            case CODE_BLOCK_TYPE -> "```\n" + text + "\n```";
            case QUOTE_BLOCK_TYPE -> toBlockQuote(text);
            case TODO_BLOCK_TYPE -> "- [ ] " + text;
            default -> text;
        };
    }

    private String renderTable(JsonNode tableBlock, RenderContext context) {
        JsonNode table = tableBlock.path("table");
        JsonNode cells = table.path("cells");
        int columnSize = table.path("property").path("column_size").asInt();
        if (!cells.isArray() || cells.isEmpty() || columnSize <= 0) {
            return renderChildren(tableBlock, context);
        }

        // 1. 依据 table.cells 顺序读取单元格，单元格及其子节点只在表格中消费一次。
        List<String> cellTexts = new ArrayList<>();
        cells.forEach(cell -> cellTexts.add(extractSubtreeText(cell.asText(), context)));
        int rowSize = table.path("property").path("row_size").asInt();
        int actualRowSize = Math.max(rowSize, (int) Math.ceil((double) cellTexts.size() / columnSize));
        List<String> rows = new ArrayList<>();

        // 2. 输出 Markdown 表格；不完整行以空单元格补齐，避免丢失已解析文本。
        for (int rowIndex = 0; rowIndex < actualRowSize; rowIndex++) {
            List<String> row = new ArrayList<>();
            for (int columnIndex = 0; columnIndex < columnSize; columnIndex++) {
                int cellIndex = rowIndex * columnSize + columnIndex;
                row.add(cellIndex < cellTexts.size() ? escapeTableCell(cellTexts.get(cellIndex)) : "");
            }
            rows.add("| " + String.join(" | ", row) + " |");
            if (rowIndex == 0) {
                rows.add("| " + "--- | ".repeat(columnSize));
            }
        }
        return String.join("\n", rows);
    }

    private String extractSubtreeText(String blockId, RenderContext context) {
        if (!context.markRendered(blockId)) {
            return "";
        }
        JsonNode block = context.getBlock(blockId);
        if (block == null) {
            return "";
        }
        List<String> texts = new ArrayList<>();
        String text = extractText(block);
        if (StringUtils.hasText(text)) {
            texts.add(text);
        }
        block.path("children").forEach(child -> {
            String childText = extractSubtreeText(child.asText(), context);
            if (StringUtils.hasText(childText)) {
                texts.add(childText);
            }
        });
        return String.join(" ", texts);
    }

    private String escapeTableCell(String text) {
        return text.replace("|", "\\|").replace("\r\n", "<br>").replace("\n", "<br>");
    }

    private String toBlockQuote(String content) {
        return "> " + content.replace("\n", "\n> ");
    }

    private String nonTextPlaceholder(int blockType) {
        String label = NON_TEXT_BLOCK_LABELS.get(blockType);
        return label == null ? "[未支持的飞书内容]" : "[" + label + "]";
    }

    private String extractText(JsonNode node) {
        List<String> texts = new ArrayList<>();
        collectText(node, texts);
        return String.join("", texts).trim();
    }

    private void collectText(JsonNode node, List<String> texts) {
        if (node == null) {
            return;
        }
        if (node.isObject()) {
            node.properties().forEach(field -> {
                if ("content".equals(field.getKey()) && field.getValue().isTextual()) {
                    texts.add(field.getValue().asText());
                } else {
                    collectText(field.getValue(), texts);
                }
            });
            return;
        }
        if (node.isArray()) {
            node.forEach(item -> collectText(item, texts));
        }
    }

    /**
     * 单次渲染上下文，统一管理 Block 索引和已消费节点，防止容器与子节点重复输出。
     */
    private static final class RenderContext {

        private final Map<String, JsonNode> blocksById;
        private final Set<String> renderedBlockIds = new HashSet<>();

        private RenderContext(Map<String, JsonNode> blocksById) {
            this.blocksById = new HashMap<>(blocksById);
        }

        private JsonNode getBlock(String blockId) {
            return blocksById.get(blockId);
        }

        private boolean markRendered(String blockId) {
            return StringUtils.hasText(blockId) && renderedBlockIds.add(blockId);
        }
    }
}
