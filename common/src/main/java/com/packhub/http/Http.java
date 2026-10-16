package com.packhub.http;

import com.packhub.source.SourceException;
import com.packhub.util.Hashes;
import com.packhub.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Modrinth 与 CurseForge 共用的 HTTP 工具：超时、重试、429 限速等待、下载 + sha1 校验。
 * 纯 java.net 实现，不依赖 Minecraft。
 */
public final class Http {

    /** 一次请求的配置。 */
    public static final class Options {
        public int connectTimeoutSeconds = 15;
        public int readTimeoutSeconds = 60;
        public int retries = 3;
        public String userAgent = "packhub/1.0.2 (Minecraft mod)";
        public final Map<String, String> headers = new LinkedHashMap<>();

        public Options connectTimeout(int seconds) {
            this.connectTimeoutSeconds = seconds;
            return this;
        }

        public Options readTimeout(int seconds) {
            this.readTimeoutSeconds = seconds;
            return this;
        }

        public Options retries(int value) {
            this.retries = value;
            return this;
        }

        public Options userAgent(String value) {
            this.userAgent = value;
            return this;
        }

        public Options header(String name, String value) {
            if (name != null && value != null && !value.isBlank()) {
                headers.put(name, value);
            }
            return this;
        }

        public Options copy() {
            Options copy = new Options();
            copy.connectTimeoutSeconds = connectTimeoutSeconds;
            copy.readTimeoutSeconds = readTimeoutSeconds;
            copy.retries = retries;
            copy.userAgent = userAgent;
            copy.headers.putAll(headers);
            return copy;
        }

        public int attempts() {
            return Math.max(1, retries + 1);
        }
    }

    private Http() {
    }

    /** GET 一个文本响应，带重试与 429 等待。 */
    public static String get(String url, Options options) throws SourceException {
        return get(url, options, CancelToken.NONE);
    }

    /** GET 一个文本响应；token 被取消时立刻抛出，不再重试。 */
    public static String get(String url, Options options, CancelToken token) throws SourceException {
        SourceException lastError = null;
        for (int attempt = 1; attempt <= options.attempts(); attempt++) {
            HttpURLConnection connection = null;
            try {
                token.throwIfCancelled();
                connection = open(url, "GET", options);
                int code = connection.getResponseCode();
                String body = readBody(connection, code);
                if (code == 200) {
                    return body;
                }
                if (code == 429) {
                    long wait = rateLimitDelay(connection);
                    Log.warn("[PackHub] 触发限速 (429)，等待 %d ms 后重试 %s", wait, url);
                    sleep(wait);
                    lastError = new SourceException("触发限速 (429)", 429);
                    continue;
                }
                if (code >= 500) {
                    lastError = new SourceException("服务端错误 HTTP " + code + ": " + preview(body), code);
                    sleep(300L * attempt);
                    continue;
                }
                throw new SourceException("HTTP " + code + " " + url + " -> " + preview(body), code);
            } catch (CancelledException e) {
                throw new SourceException("已取消：" + url, 0, e);
            } catch (IOException e) {
                lastError = new SourceException("网络错误: " + e, 0, e);
                if (attempt < options.attempts()) {
                    sleep(300L * attempt);
                }
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }
        }
        throw lastError != null ? lastError : new SourceException("请求失败: " + url, 0);
    }

    /**
     * 下载到目标路径：先写 .part，校验 sha1，再原子替换。
     */
    public static Path download(String url, Path target, ProgressListener listener, long expectedSize,
                               String expectedSha1, Options options) throws SourceException {
        return download(url, target, listener, expectedSize, expectedSha1, options, CancelToken.NONE);
    }

    /**
     * 下载到目标路径，支持取消：被取消时删除半截的 .part 文件，并且不重试。
     */
    public static Path download(String url, Path target, ProgressListener listener, long expectedSize,
                               String expectedSha1, Options options, CancelToken token) throws SourceException {
        if (listener == null) {
            listener = ProgressListener.NONE;
        }
        Path part = target.resolveSibling(target.getFileName() + ".part");
        IOException lastError = null;
        for (int attempt = 1; attempt <= options.attempts(); attempt++) {
            HttpURLConnection connection = null;
            try {
                token.throwIfCancelled();
                Files.createDirectories(target.getParent());
                connection = open(url, "GET", options);
                int code = connection.getResponseCode();
                if (code >= 400) {
                    throw new IOException("HTTP " + code + " 下载 " + url);
                }
                long contentLength = connection.getContentLengthLong();
                long total = contentLength > 0 ? contentLength : expectedSize;
                long done = 0;
                long lastNotify = 0L;
                byte[] buffer = new byte[32768];
                try (InputStream in = connection.getInputStream();
                     OutputStream out = Files.newOutputStream(part)) {
                    int read;
                    while ((read = in.read(buffer)) > 0) {
                        token.throwIfCancelled();
                        out.write(buffer, 0, read);
                        done += read;
                        long now = System.currentTimeMillis();
                        if (now - lastNotify > 120L) {
                            lastNotify = now;
                            listener.onProgress(done, total);
                        }
                    }
                }
                listener.onProgress(done, total);
                if (expectedSha1 != null && !expectedSha1.isBlank()) {
                    String actual = Hashes.sha1(part);
                    if (!actual.equalsIgnoreCase(expectedSha1)) {
                        Files.deleteIfExists(part);
                        throw new IOException("sha1 校验失败: 期望 " + expectedSha1 + "，实际 " + actual);
                    }
                }
                Files.move(part, target, StandardCopyOption.REPLACE_EXISTING);
                Log.info("[PackHub] 已下载 %s (%d 字节)", target.getFileName(), done);
                return target;
            } catch (CancelledException e) {
                deleteQuietly(part);
                throw new SourceException("已取消: " + target.getFileName(), 0, e);
            } catch (IOException e) {
                lastError = e;
                Log.warn("[PackHub] 第 %d/%d 次下载 %s 失败: %s", attempt, options.attempts(), url, e.toString());
                if (attempt < options.attempts()) {
                    sleep(400L * attempt);
                }
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }
        }
        deleteQuietly(part);
        throw new SourceException("下载失败: " + url, 0, lastError);
    }

    private static void deleteQuietly(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
            // 忽略
        }
    }

    /** 解析 URL（语法错误按网络错误处理，交给上层重试/报错逻辑）。 */
    public static URL toUrl(String url) throws IOException {
        try {
            return URI.create(url).toURL();
        } catch (IllegalArgumentException | MalformedURLException e) {
            throw new IOException("非法的 URL: " + url, e);
        }
    }

    private static HttpURLConnection open(String url, String method, Options options) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) toUrl(url).openConnection();
        connection.setRequestMethod(method);
        connection.setConnectTimeout(Math.max(3, options.connectTimeoutSeconds) * 1000);
        connection.setReadTimeout(Math.max(5, options.readTimeoutSeconds) * 1000);
        connection.setInstanceFollowRedirects(true);
        connection.setRequestProperty("User-Agent", options.userAgent);
        connection.setRequestProperty("Accept", "application/json");
        for (Map.Entry<String, String> entry : options.headers.entrySet()) {
            connection.setRequestProperty(entry.getKey(), entry.getValue());
        }
        return connection;
    }

    private static long rateLimitDelay(HttpURLConnection connection) {
        String header = connection.getHeaderField("X-Ratelimit-Reset");
        if (header != null) {
            try {
                long resetEpoch = Long.parseLong(header.trim());
                long now = System.currentTimeMillis() / 1000L;
                long wait = (resetEpoch - now) * 1000L + 250L;
                return Math.max(500L, Math.min(wait, 20000L));
            } catch (NumberFormatException ignored) {
                // 落到默认值
            }
        }
        String retryAfter = connection.getHeaderField("Retry-After");
        if (retryAfter != null) {
            try {
                return Math.max(500L, Math.min(Long.parseLong(retryAfter.trim()) * 1000L, 20000L));
            } catch (NumberFormatException ignored) {
                // 落到默认值
            }
        }
        return 2000L;
    }

    private static String readBody(HttpURLConnection connection, int code) {
        try (InputStream in = code >= 400 ? connection.getErrorStream() : connection.getInputStream()) {
            if (in == null) {
                return "";
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return "";
        }
    }

    public static String preview(String body) {
        if (body == null) {
            return "";
        }
        String flat = body.replace('\n', ' ').trim();
        return flat.length() > 300 ? flat.substring(0, 300) + "..." : flat;
    }

    public static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
