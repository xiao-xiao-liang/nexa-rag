package com.nexarag.document.splitter.excel;

import com.nexarag.common.exception.ServiceException;
import com.nexarag.document.dto.ExcelSplitMode;
import com.nexarag.document.dto.ExcelSplitOptions;
import com.nexarag.document.dto.SplitConfigRequest;
import com.nexarag.document.enums.SplitStrategy;
import com.nexarag.document.error.DocumentErrorCode;
import com.nexarag.document.splitter.ChunkDraft;
import com.nexarag.document.splitter.DocumentChunkIdGenerator;
import com.nexarag.document.splitter.DocumentSplitContext;
import com.nexarag.document.splitter.DocumentSplitResult;
import com.nexarag.document.splitter.DocumentSplitter;
import cn.idev.excel.FastExcel;
import cn.idev.excel.context.AnalysisContext;
import cn.idev.excel.read.listener.ReadListener;
import cn.idev.excel.support.ExcelTypeEnum;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.ByteArrayInputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Excel/CSV 文档切分器，负责把表格行转换为结构化文本片段。
 */
@Component
@RequiredArgsConstructor
public class ExcelDocumentSplitter implements DocumentSplitter {

    private final DocumentChunkIdGenerator chunkIdGenerator;

    @Override
    public SplitStrategy strategy() {
        return SplitStrategy.EXCEL;
    }

    /**
     * 切分 Excel 或 CSV 文件字节。
     *
     * @param context 文档切分上下文
     * @return 文档切分结果
     */
    @Override
    public DocumentSplitResult split(DocumentSplitContext context) {
        validateContext(context);
        SplitConfigRequest config = context.config();
        ExcelSplitOptions options = config.excel();
        List<TableSheet> sheets = readSheets(context, options);
        List<ChunkDraft> drafts = new ArrayList<>();

        // 1. 按 sheet 和行边界渲染片段
        for (TableSheet sheet : sheets) {
            drafts.addAll(renderSheet(context, sheet, config, options));
        }
        return DocumentSplitResult.unstructured(drafts);
    }

    private void validateContext(DocumentSplitContext context) {
        if (context == null || context.fileBytes() == null || context.fileBytes().length == 0 || context.config() == null) {
            throw new ServiceException("Excel切分上下文不完整", DocumentErrorCode.DOCUMENT_PROCESS_CONFIG_INVALID);
        }
    }

    private List<TableSheet> readSheets(DocumentSplitContext context, ExcelSplitOptions options) {
        try {
            TableReadListener listener = new TableReadListener(firstRowAsHeader(options));
            ByteArrayInputStream inputStream = new ByteArrayInputStream(context.fileBytes());
            if (isCsv(context.originalFileName())) {
                FastExcel.read(inputStream, listener)
                        .excelType(ExcelTypeEnum.CSV)
                        .charset(resolveCharset(options))
                        .headRowNumber(0)
                        .csv()
                        .doRead();
            } else {
                FastExcel.read(inputStream, listener)
                        .headRowNumber(0)
                        .doReadAll();
            }
            return listener.sheets();
        } catch (RuntimeException exception) {
            throw new ServiceException("读取Excel/CSV文件失败，documentId=" + context.documentId(), exception,
                    DocumentErrorCode.DOCUMENT_PROCESS_CONFIG_INVALID);
        }
    }

    private boolean isCsv(String fileName) {
        return fileName != null && fileName.toLowerCase(java.util.Locale.ROOT).endsWith(".csv");
    }

    private Charset resolveCharset(ExcelSplitOptions options) {
        if (options != null && StringUtils.hasText(options.charset())) {
            return Charset.forName(options.charset());
        }
        return StandardCharsets.UTF_8;
    }

    private boolean firstRowAsHeader(ExcelSplitOptions options) {
        return options == null || !Boolean.FALSE.equals(options.firstRowAsHeader());
    }

    private List<ChunkDraft> renderSheet(DocumentSplitContext context,
                                         TableSheet sheet,
                                         SplitConfigRequest config,
                                         ExcelSplitOptions options) {
        List<ChunkDraft> drafts = new ArrayList<>();
        List<List<TableRow>> rowGroups = groupRows(sheet.rows(), sheet.headers(), config.chunkSize(), options);
        ExcelSplitMode mode = options == null || options.mode() == null ? ExcelSplitMode.KEY_VALUE : options.mode();
        for (int i = 0; i < rowGroups.size(); i++) {
            List<TableRow> rows = rowGroups.get(i);
            String text = mode == ExcelSplitMode.HTML_TABLE
                    ? renderHtmlTable(sheet.headers(), rows)
                    : renderKeyValue(sheet.headers(), rows);
            if (!StringUtils.hasText(text)) {
                continue;
            }
            drafts.add(new ChunkDraft(chunkIdGenerator.nextChunkId(context.documentId()), null, null, text, null,
                    null, metadata(context, sheet, mode, rows, i), false));
        }
        return drafts;
    }

    private List<List<TableRow>> groupRows(List<TableRow> rows,
                                           List<String> headers,
                                           int chunkSize,
                                           ExcelSplitOptions options) {
        List<List<TableRow>> groups = new ArrayList<>();
        List<TableRow> current = new ArrayList<>();
        int currentSize = 0;
        Integer maxRowsPerChunk = options == null ? null : options.maxRowsPerChunk();
        for (TableRow row : rows) {
            int rowSize = estimateRowSize(headers, row);
            boolean rowLimitReached = maxRowsPerChunk != null && !current.isEmpty() && current.size() >= maxRowsPerChunk;
            boolean sizeLimitReached = !current.isEmpty() && currentSize + rowSize > chunkSize;
            if (rowLimitReached || sizeLimitReached) {
                groups.add(current);
                current = new ArrayList<>();
                currentSize = 0;
            }
            current.add(row);
            currentSize += rowSize;
        }
        if (!current.isEmpty()) {
            groups.add(current);
        }
        return groups;
    }

    private int estimateRowSize(List<String> headers, TableRow row) {
        int size = 0;
        for (int i = 0; i < row.cells().size(); i++) {
            String header = i < headers.size() ? headers.get(i) : "列" + (i + 1);
            String value = row.cells().get(i);
            size += header.length() + value.length() + 4;
        }
        return Math.max(size, 1);
    }

    private String renderKeyValue(List<String> headers, List<TableRow> rows) {
        StringBuilder builder = new StringBuilder();
        for (TableRow row : rows) {
            if (!builder.isEmpty()) {
                builder.append("\n");
            }
            for (int i = 0; i < row.cells().size(); i++) {
                String header = i < headers.size() && StringUtils.hasText(headers.get(i)) ? headers.get(i) : "列" + (i + 1);
                String value = row.cells().get(i);
                if (i > 0) {
                    builder.append("; ");
                }
                builder.append(header).append("：").append(value);
            }
        }
        return builder.toString();
    }

    private String renderHtmlTable(List<String> headers, List<TableRow> rows) {
        StringBuilder builder = new StringBuilder();
        builder.append("<table>\n<thead><tr>");
        for (String header : headers) {
            builder.append("<th>").append(escapeHtml(header)).append("</th>");
        }
        builder.append("</tr></thead>\n<tbody>\n");
        for (TableRow row : rows) {
            builder.append("<tr>");
            for (int i = 0; i < headers.size(); i++) {
                String value = i < row.cells().size() ? row.cells().get(i) : "";
                builder.append("<td>").append(escapeHtml(value)).append("</td>");
            }
            builder.append("</tr>\n");
        }
        builder.append("</tbody>\n</table>");
        return builder.toString();
    }

    private String escapeHtml(String text) {
        if (text == null) {
            return "";
        }
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#x27;");
    }

    private Map<String, Object> metadata(DocumentSplitContext context,
                                         TableSheet sheet,
                                         ExcelSplitMode mode,
                                         List<TableRow> rows,
                                         int chunkIndex) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("splitStrategy", strategy().name());
        metadata.put("fileType", context.fileType().name());
        metadata.put("mode", mode.name());
        metadata.put("sheetName", sheet.sheetName());
        metadata.put("startRow", rows.getFirst().rowNumber());
        metadata.put("endRow", rows.getLast().rowNumber());
        metadata.put("headers", sheet.headers());
        metadata.put("rowCount", rows.size());
        metadata.put("chunkIndex", chunkIndex);
        return metadata;
    }

    private static class TableReadListener implements ReadListener<Map<Integer, String>> {

        private final boolean firstRowAsHeader;
        private final Map<String, MutableSheet> sheets = new LinkedHashMap<>();

        private TableReadListener(boolean firstRowAsHeader) {
            this.firstRowAsHeader = firstRowAsHeader;
        }

        @Override
        public void invoke(Map<Integer, String> data, AnalysisContext context) {
            String sheetName = currentSheetName(context);
            MutableSheet sheet = sheets.computeIfAbsent(sheetName, MutableSheet::new);
            int rowNumber = context.readRowHolder().getRowIndex() + 1;
            List<String> row = toRow(data);
            if (row.stream().noneMatch(StringUtils::hasText)) {
                return;
            }
            if (firstRowAsHeader && sheet.headers().isEmpty()) {
                sheet.headers().addAll(row);
                return;
            }
            ensureHeaders(sheet.headers(), row.size());
            sheet.rows().add(new TableRow(rowNumber, row));
        }

        @Override
        public void doAfterAllAnalysed(AnalysisContext context) {
            // 1. FastExcel 回调结束后不需要额外处理
        }

        private List<TableSheet> sheets() {
            return sheets.values().stream()
                    .map(sheet -> new TableSheet(sheet.sheetName(), List.copyOf(sheet.headers()), List.copyOf(sheet.rows())))
                    .filter(sheet -> !sheet.rows().isEmpty())
                    .toList();
        }

        private String currentSheetName(AnalysisContext context) {
            if (context.readSheetHolder() != null && StringUtils.hasText(context.readSheetHolder().getSheetName())) {
                return context.readSheetHolder().getSheetName();
            }
            return "Sheet1";
        }

        private List<String> toRow(Map<Integer, String> data) {
            int maxIndex = data.keySet().stream().max(Integer::compareTo).orElse(-1);
            List<String> row = new ArrayList<>();
            for (int i = 0; i <= maxIndex; i++) {
                row.add(cleanCell(data.getOrDefault(i, "")));
            }
            return row;
        }

        private String cleanCell(String value) {
            return value == null ? "" : value.replaceAll("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F]", "").trim();
        }

        private void ensureHeaders(List<String> headers, int size) {
            while (headers.size() < size) {
                headers.add("列" + (headers.size() + 1));
            }
        }
    }

    private record MutableSheet(String sheetName, List<String> headers, List<TableRow> rows) {

        private MutableSheet(String sheetName) {
            this(sheetName, new ArrayList<>(), new ArrayList<>());
        }
    }

    private record TableSheet(String sheetName, List<String> headers, List<TableRow> rows) {
    }

    private record TableRow(int rowNumber, List<String> cells) {
    }
}
