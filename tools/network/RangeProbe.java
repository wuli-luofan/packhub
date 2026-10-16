import java.io.*;
import java.net.*;

/** 诊断：分段请求 + 带进度的完整 GET，判断大文件是被限速还是直接卡死。 */
public class RangeProbe {
    public static void main(String[] args) throws Exception {
        String url = args[0];
        probeRange(url, 0, 65535);
        probeRange(url, 65536, 131071);
        probeFull(url);
    }

    static void probeRange(String url, long start, long end) {
        long t0 = System.currentTimeMillis();
        try {
            HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
            c.setConnectTimeout(15000);
            c.setReadTimeout(20000);
            c.setRequestProperty("Range", "bytes=" + start + "-" + end);
            c.setRequestProperty("User-Agent", "PackHub-Probe/1.0");
            int code = c.getResponseCode();
            byte[] data = c.getInputStream().readAllBytes();
            System.out.println("RANGE " + start + "-" + end + " -> HTTP " + code + " bytes=" + data.length
                    + " in " + (System.currentTimeMillis() - t0) + " ms");
        } catch (Throwable t) {
            System.out.println("RANGE " + start + "-" + end + " FAIL after " + (System.currentTimeMillis() - t0)
                    + " ms -> " + t);
        }
    }

    static void probeFull(String url) {
        long t0 = System.currentTimeMillis();
        try {
            HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
            c.setConnectTimeout(15000);
            c.setReadTimeout(20000);
            c.setRequestProperty("User-Agent", "PackHub-Probe/1.0");
            int code = c.getResponseCode();
            long length = c.getContentLengthLong();
            System.out.println("FULL HTTP " + code + " contentLength=" + length);
            long total = 0;
            long lastPrint = t0;
            byte[] buffer = new byte[32768];
            try (InputStream in = c.getInputStream()) {
                int read;
                while ((read = in.read(buffer)) > 0) {
                    total += read;
                    long now = System.currentTimeMillis();
                    if (now - lastPrint > 2000) {
                        lastPrint = now;
                        System.out.println("  ... " + total + " bytes, " + (now - t0) + " ms");
                    }
                }
            }
            System.out.println("FULL done " + total + " bytes in " + (System.currentTimeMillis() - t0) + " ms");
        } catch (Throwable t) {
            System.out.println("FULL FAIL after " + (System.currentTimeMillis() - t0) + " ms -> " + t);
        }
    }
}
