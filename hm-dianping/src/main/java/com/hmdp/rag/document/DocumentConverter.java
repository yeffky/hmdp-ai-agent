package com.hmdp.rag.document;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 文档转换器 — 通过 ProcessBuilder 调用 markitdown CLI 将文档转为 Markdown。
 *
 * <p>依赖本地安装的 markitdown: pip install "markitdown[all]"
 */
public class DocumentConverter {

    private static final Logger log = LoggerFactory.getLogger(DocumentConverter.class);

    private final List<String> command;
    private final int timeoutSeconds;

    public DocumentConverter(String command, int timeoutSeconds) {
        // "python -m markitdown" 拆分为 List，ProcessBuilder 正确传递参数
        this.command = List.of(command.split("\\s+"));
        this.timeoutSeconds = timeoutSeconds;
        log.info("DocumentConverter 初始化: command={}, timeout={}s", this.command, timeoutSeconds);
    }

    /**
     * 调用 markitdown CLI 转换文档为 Markdown。
     *
     * @param filePath 文档绝对路径
     * @return 转换后的 Markdown 文本
     * @throws IOException          进程启动失败
     * @throws InterruptedException 等待超时
     * @throws ConversionException  转换失败（退出码非0或结果为空）
     */
    public String convert(Path filePath) throws IOException, InterruptedException, ConversionException {
        List<String> fullCmd = new java.util.ArrayList<>(command);
        fullCmd.add(filePath.toAbsolutePath().toString());

        ProcessBuilder pb = new ProcessBuilder(fullCmd);
        pb.redirectErrorStream(false);
        // 强制 Python 子进程输出 UTF-8，避免 Windows 下 GBK 乱码
        pb.environment().put("PYTHONIOENCODING", "utf-8");
        pb.environment().put("PYTHONUTF8", "1");

        log.info("执行转换: {}", String.join(" ", fullCmd));
        Process process = pb.start();

        try {
            // 启动两个线程分别读取 stdout 和 stderr，避免管道阻塞
            ByteArrayOutputStream stdout = new ByteArrayOutputStream();
            ByteArrayOutputStream stderr = new ByteArrayOutputStream();
            Thread outThread = startReader(process.getInputStream(), stdout);
            Thread errThread = startReader(process.getErrorStream(), stderr);

            boolean finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
            if (!finished) {
                // 先读取已累积的输出再 kill
                outThread.join(500);
                errThread.join(500);
                process.destroyForcibly();
                String errText = stderr.toString(StandardCharsets.UTF_8);
                log.error("转换超时 stderr: {}", errText);
                throw new ConversionException(filePath.getFileName()
                        + " 转换超时（>" + timeoutSeconds + "s）"
                        + (errText.isEmpty() ? "" : "，stderr: " + truncate(errText, 300)));
            }

            outThread.join(timeoutSeconds * 1000L);
            errThread.join(5000);
            String outText = stdout.toString(StandardCharsets.UTF_8);
            String errText = stderr.toString(StandardCharsets.UTF_8);

            if (process.exitValue() != 0) {
                log.error("转换失败 stderr: {}", errText);
                throw new ConversionException(filePath.getFileName()
                        + " 转换失败（exit=" + process.exitValue() + "）: "
                        + truncate(errText.isEmpty() ? outText : errText, 300));
            }

            if (outText.isBlank()) {
                log.error("转换结果为空，stderr: {}", errText);
                throw new ConversionException(filePath.getFileName() + " 转换结果为空"
                        + (errText.isEmpty() ? "" : "，stderr: " + truncate(errText, 200)));
            }

            log.debug("转换完成: {} → {} chars", filePath.getFileName(), outText.length());
            return outText;
        } finally {
            if (process.isAlive()) {
                process.destroyForcibly();
            }
        }
    }

    private static Thread startReader(InputStream in, ByteArrayOutputStream out) {
        Thread t = new Thread(() -> {
            try {
                in.transferTo(out);
            } catch (IOException ignored) {}
        });
        t.setDaemon(true);
        t.start();
        return t;
    }

    private static String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max) + "...";
    }

    /** 文档转换失败异常 */
    public static class ConversionException extends Exception {
        public ConversionException(String message) {
            super(message);
        }
    }
}
