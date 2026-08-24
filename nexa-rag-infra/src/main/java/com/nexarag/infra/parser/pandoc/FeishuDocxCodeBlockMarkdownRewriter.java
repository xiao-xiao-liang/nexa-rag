package com.nexarag.infra.parser.pandoc;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * 恢复飞书 DOCX 导出文件中被 Pandoc 转换为单列表格的代码块。
 *
 * <p>飞书代码块在 DOCX 中是浅灰单元格表格，语言名称和正文以换行分隔。该处理器逐表读取 OOXML，
 * 将原始代码写入临时文件后再替换 Pandoc 表格输出，避免全量加载 DOCX 或飞书 Block。</p>
 */
@Component
public class FeishuDocxCodeBlockMarkdownRewriter {

    private static final String DOCUMENT_XML_ENTRY = "word/document.xml";
    private static final String FEISHU_CODE_CELL_FILL = "f5f6f7";
    private static final Map<String, String> LANGUAGE_ALIASES = Map.ofEntries(
            Map.entry("python", "python"), Map.entry("java", "java"),
            Map.entry("javascript", "javascript"), Map.entry("typescript", "typescript"),
            Map.entry("sql", "sql"), Map.entry("json", "json"), Map.entry("yaml", "yaml"),
            Map.entry("yml", "yaml"), Map.entry("bash", "bash"), Map.entry("shell", "bash"),
            Map.entry("powershell", "powershell"), Map.entry("html", "html"), Map.entry("css", "css"),
            Map.entry("go", "go"), Map.entry("rust", "rust"), Map.entry("c", "c"),
            Map.entry("c++", "cpp"), Map.entry("c#", "csharp"), Map.entry("plain text", "text"),
            Map.entry("text", "text"), Map.entry("纯文本", "text"));

    /**
     * 将 Pandoc 输出中与飞书代码表格匹配的内容替换为围栏代码块。
     *
     * @param docxPath     飞书导出的 DOCX 文件
     * @param markdownPath Pandoc 生成的 Markdown 文件
     * @throws IOException 读取或替换文件失败时抛出
     */
    public void rewrite(Path docxPath, Path markdownPath) throws IOException {
        if (!Files.isRegularFile(docxPath) || !Files.isRegularFile(markdownPath)) {
            return;
        }
        Path directory = markdownPath.toAbsolutePath().getParent();
        Path rewrittenPath = Files.createTempFile(directory, "feishu-code-rewritten-", ".md");
        try (CodeBlockIterator codeBlocks = new CodeBlockIterator(docxPath, directory);
             BufferedReader reader = Files.newBufferedReader(markdownPath, StandardCharsets.UTF_8);
             BufferedWriter writer = Files.newBufferedWriter(rewrittenPath, StandardCharsets.UTF_8)) {
            rewriteMarkdown(reader, writer, codeBlocks, directory);
        } catch (XMLStreamException exception) {
            throw new IOException("读取DOCX代码表格失败", exception);
        } catch (IOException exception) {
            Files.deleteIfExists(rewrittenPath);
            throw exception;
        }
        Files.move(rewrittenPath, markdownPath, StandardCopyOption.REPLACE_EXISTING);
    }

    private void rewriteMarkdown(BufferedReader reader, BufferedWriter writer, CodeBlockIterator codeBlocks,
                                 Path directory) throws IOException, XMLStreamException {
        String line;
        while ((line = reader.readLine()) != null) {
            if (!isHorizontalRule(line)) {
                writeLine(writer, line);
                continue;
            }
            CodeBlock expected = codeBlocks.peek();
            String languageLine = reader.readLine();
            if (expected == null || languageLine == null || !expected.language().equals(resolveLanguage(languageLine))) {
                writeLine(writer, line);
                if (languageLine != null) {
                    writeLine(writer, languageLine);
                }
                continue;
            }
            MarkdownTable table = readMarkdownTable(reader, line, languageLine, directory);
            if (!expected.firstCodeLine().equals(normalizePandocTableLine(table.firstCodeLine()))) {
                copyFile(writer, table.path());
                Files.deleteIfExists(table.path());
                continue;
            }
            writeCodeFence(writer, codeBlocks.take());
            Files.deleteIfExists(table.path());
        }
    }

    private MarkdownTable readMarkdownTable(BufferedReader reader, String openingLine, String languageLine,
                                            Path directory) throws IOException {
        Path tablePath = Files.createTempFile(directory, "feishu-pandoc-table-", ".md");
        String firstCodeLine = "";
        try (BufferedWriter tableWriter = Files.newBufferedWriter(tablePath, StandardCharsets.UTF_8)) {
            writeLine(tableWriter, openingLine);
            writeLine(tableWriter, languageLine);
            String line;
            while ((line = reader.readLine()) != null) {
                writeLine(tableWriter, line);
                if (firstCodeLine.isEmpty()) {
                    firstCodeLine = line;
                }
                if (isHorizontalRule(line)) {
                    break;
                }
            }
        }
        return new MarkdownTable(tablePath, firstCodeLine);
    }

    private void writeCodeFence(BufferedWriter writer, CodeBlock codeBlock) throws IOException {
        String fence = "`".repeat(resolveFenceLength(codeBlock.path()));
        writer.write(fence);
        writer.write(codeBlock.language());
        writer.newLine();
        try (BufferedReader codeReader = Files.newBufferedReader(codeBlock.path(), StandardCharsets.UTF_8)) {
            codeReader.readLine();
            String codeLine;
            while ((codeLine = codeReader.readLine()) != null) {
                writeLine(writer, codeLine);
            }
        }
        writer.write(fence);
        writer.newLine();
    }

    private int resolveFenceLength(Path codePath) throws IOException {
        int longestRun = 0;
        int currentRun = 0;
        try (BufferedReader reader = Files.newBufferedReader(codePath, StandardCharsets.UTF_8)) {
            reader.readLine();
            int character;
            while ((character = reader.read()) >= 0) {
                currentRun = character == '`' ? currentRun + 1 : 0;
                longestRun = Math.max(longestRun, currentRun);
            }
        }
        return Math.max(3, longestRun + 1);
    }

    private void copyFile(BufferedWriter writer, Path sourcePath) throws IOException {
        try (BufferedReader reader = Files.newBufferedReader(sourcePath, StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                writeLine(writer, line);
            }
        }
    }

    private boolean isHorizontalRule(String line) {
        String value = line.trim();
        return value.length() >= 3 && value.chars().allMatch(character -> character == '-');
    }

    private static String resolveLanguage(String languageLine) {
        return LANGUAGE_ALIASES.get(normalizeTableLine(languageLine).toLowerCase(Locale.ROOT));
    }

    private static String normalizeTableLine(String line) {
        String normalized = line == null ? "" : line.strip();
        return normalized.endsWith("\\") ? normalized.substring(0, normalized.length() - 1).stripTrailing() : normalized;
    }

    /** 将 Pandoc 在表格文本中插入的 Markdown 转义还原为原始代码首行。 */
    private static String normalizePandocTableLine(String line) {
        String normalized = normalizeTableLine(line);
        StringBuilder restored = new StringBuilder(normalized.length());
        for (int index = 0; index < normalized.length(); index++) {
            char character = normalized.charAt(index);
            if (character == '\\' && index + 1 < normalized.length()) {
                restored.append(normalized.charAt(++index));
                continue;
            }
            restored.append(character);
        }
        return restored.toString().strip();
    }

    private void writeLine(BufferedWriter writer, String line) throws IOException {
        writer.write(line);
        writer.newLine();
    }

    private record MarkdownTable(Path path, String firstCodeLine) {
    }

    private record CodeBlock(Path path, String language, String firstCodeLine) {
    }

    /** 逐个读取 DOCX 表格，仅保留满足飞书代码表格特征的临时文件。 */
    private static final class CodeBlockIterator implements AutoCloseable {

        private final ZipFile zipFile;
        private final InputStream documentStream;
        private final XMLStreamReader reader;
        private final Path temporaryDirectory;
        private CodeBlock nextCodeBlock;

        private CodeBlockIterator(Path docxPath, Path temporaryDirectory) throws IOException, XMLStreamException {
            this.zipFile = new ZipFile(docxPath.toFile());
            ZipEntry documentEntry = zipFile.getEntry(DOCUMENT_XML_ENTRY);
            if (documentEntry == null) {
                close();
                throw new IOException("DOCX缺少主文档XML");
            }
            this.documentStream = zipFile.getInputStream(documentEntry);
            this.reader = createSecureXmlReader(documentStream);
            this.temporaryDirectory = temporaryDirectory;
        }

        private CodeBlock peek() throws IOException, XMLStreamException {
            if (nextCodeBlock == null) {
                nextCodeBlock = findNextCodeBlock();
            }
            return nextCodeBlock;
        }

        private CodeBlock take() throws IOException, XMLStreamException {
            CodeBlock codeBlock = peek();
            nextCodeBlock = null;
            return codeBlock;
        }

        private CodeBlock findNextCodeBlock() throws IOException, XMLStreamException {
            while (reader.hasNext()) {
                if (reader.next() == XMLStreamConstants.START_ELEMENT && "tbl".equals(reader.getLocalName())) {
                    CodeBlock codeBlock = readTable();
                    if (codeBlock != null) {
                        return codeBlock;
                    }
                }
            }
            return null;
        }

        private CodeBlock readTable() throws IOException, XMLStreamException {
            Path textPath = Files.createTempFile(temporaryDirectory, "feishu-docx-code-", ".txt");
            int tableDepth = 1;
            int rowCount = 0;
            int cellCount = 0;
            boolean hasCodeFill = false;
            try (BufferedWriter textWriter = Files.newBufferedWriter(textPath, StandardCharsets.UTF_8)) {
                while (reader.hasNext() && tableDepth > 0) {
                    int eventType = reader.next();
                    if (eventType == XMLStreamConstants.START_ELEMENT) {
                        String element = reader.getLocalName();
                        if ("tbl".equals(element)) {
                            tableDepth++;
                        } else if ("tr".equals(element)) {
                            rowCount++;
                        } else if ("tc".equals(element)) {
                            cellCount++;
                        } else if ("shd".equals(element) && FEISHU_CODE_CELL_FILL.equalsIgnoreCase(
                                reader.getAttributeValue("http://schemas.openxmlformats.org/wordprocessingml/2006/main", "fill"))) {
                            hasCodeFill = true;
                        } else if ("t".equals(element)) {
                            textWriter.write(reader.getElementText());
                        } else if ("br".equals(element)) {
                            textWriter.newLine();
                        }
                    } else if (eventType == XMLStreamConstants.END_ELEMENT && "tbl".equals(reader.getLocalName())) {
                        tableDepth--;
                    }
                }
            } catch (Exception exception) {
                Files.deleteIfExists(textPath);
                throw exception;
            }
            CodeBlock codeBlock = toCodeBlock(textPath, rowCount, cellCount, hasCodeFill);
            if (codeBlock == null) {
                Files.deleteIfExists(textPath);
            }
            return codeBlock;
        }

        private CodeBlock toCodeBlock(Path textPath, int rowCount, int cellCount, boolean hasCodeFill) throws IOException {
            if (rowCount != 1 || cellCount != 1 || !hasCodeFill) {
                return null;
            }
            try (BufferedReader textReader = Files.newBufferedReader(textPath, StandardCharsets.UTF_8)) {
                String language = resolveLanguage(textReader.readLine());
                String firstCodeLine = textReader.readLine();
                return StringUtils.hasText(language) && firstCodeLine != null
                        ? new CodeBlock(textPath, language, firstCodeLine.strip()) : null;
            }
        }

        @Override
        public void close() throws IOException {
            IOException closeException = null;
            if (nextCodeBlock != null) {
                try {
                    Files.deleteIfExists(nextCodeBlock.path());
                } catch (IOException exception) {
                    closeException = exception;
                }
            }
            try {
                reader.close();
            } catch (XMLStreamException exception) {
                if (closeException == null) {
                    closeException = new IOException("关闭DOCX XML读取器失败", exception);
                }
            }
            try {
                documentStream.close();
            } catch (IOException exception) {
                if (closeException == null) {
                    closeException = exception;
                }
            }
            try {
                zipFile.close();
            } catch (IOException exception) {
                if (closeException == null) {
                    closeException = exception;
                }
            }
            if (closeException != null) {
                throw closeException;
            }
        }

        private static XMLStreamReader createSecureXmlReader(InputStream inputStream) throws XMLStreamException {
            XMLInputFactory factory = XMLInputFactory.newFactory();
            factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
            factory.setProperty("javax.xml.stream.isSupportingExternalEntities", false);
            return factory.createXMLStreamReader(inputStream, StandardCharsets.UTF_8.name());
        }
    }
}
