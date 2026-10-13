package com.turktv.app;

import android.os.Handler;
import android.os.Looper;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Collections;

/**
 * Uzun liste linkini kumandayla yazmamak için: ayar ekranı açıkken TV, ev ağında küçük bir
 * sayfa sunar; telefondan bu sayfaya linki yapıştırınca TV'ye gelir. Ekran kapanınca durur.
 */
final class LinkServer {

    interface OnUrl {
        void got(String url);
    }

    private final Handler ui = new Handler(Looper.getMainLooper());
    private ServerSocket server;
    private volatile boolean running;

    boolean start(OnUrl cb) {
        try {
            server = new ServerSocket(8765);
        } catch (Exception e) {
            try {
                server = new ServerSocket(0);
            } catch (Exception e2) {
                return false;
            }
        }
        running = true;
        Thread th = new Thread(() -> {
            while (running) {
                try (Socket s = server.accept()) {
                    s.setSoTimeout(10000);
                    handle(s, cb);
                } catch (Exception ignored) {
                }
            }
        });
        th.setDaemon(true);
        th.start();
        return true;
    }

    int port() {
        return server == null ? 0 : server.getLocalPort();
    }

    void stop() {
        running = false;
        try {
            if (server != null) server.close();
        } catch (Exception ignored) {
        }
    }

    private void handle(Socket s, OnUrl cb) throws Exception {
        InputStream in = s.getInputStream();
        ByteArrayOutputStream head = new ByteArrayOutputStream();
        int b, state = 0;
        while ((b = in.read()) != -1 && head.size() < 16384) {
            head.write(b);
            state = (b == '\r' || b == '\n') ? state + 1 : 0;
            if (state == 4) break;
        }
        String headers = head.toString("UTF-8");
        String first = headers.split("\r\n", 2)[0];
        int len = 0;
        for (String h : headers.split("\r\n")) {
            if (h.toLowerCase().startsWith("content-length:")) {
                try {
                    len = Integer.parseInt(h.substring(15).trim());
                } catch (NumberFormatException ignored) {
                }
            }
        }
        String page;
        if (first.startsWith("POST") && len > 0 && len < 65536) {
            byte[] body = new byte[len];
            int read = 0;
            while (read < len) {
                int n = in.read(body, read, len - read);
                if (n < 0) break;
                read += n;
            }
            String form = new String(body, 0, read, StandardCharsets.UTF_8);
            String url = "";
            for (String kv : form.split("&")) {
                if (kv.startsWith("url=")) url = URLDecoder.decode(kv.substring(4), "UTF-8").trim();
            }
            if (url.startsWith("http://") || url.startsWith("https://")) {
                final String u = url;
                ui.post(() -> cb.got(u));
                page = page("Gönderildi ✓", "<p>Liste TV'ye ulaştı. Artık TV ekranına bakabilirsin.</p>");
            } else {
                page = page("Link geçersiz", "<p>Link http:// veya https:// ile başlamalı.</p>" + FORM);
            }
        } else {
            page = page("TV'ye liste gönder", "<p>IPTV aboneliğinin <b>M3U liste linkini</b> yapıştır.</p>" + FORM);
        }
        byte[] bytes = page.getBytes(StandardCharsets.UTF_8);
        OutputStream out = s.getOutputStream();
        out.write(("HTTP/1.1 200 OK\r\nContent-Type: text/html; charset=utf-8\r\nContent-Length: "
                + bytes.length + "\r\nConnection: close\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        out.write(bytes);
        out.flush();
    }

    private static final String FORM = "<form method='post'>"
            + "<textarea name='url' rows='5' placeholder='http://...' autofocus></textarea>"
            + "<button type='submit'>TV'ye gönder</button></form>";

    private static String page(String title, String body) {
        return "<!doctype html><html lang='tr'><head><meta charset='utf-8'>"
                + "<meta name='viewport' content='width=device-width,initial-scale=1'><title>TV</title><style>"
                + "body{margin:0;padding:28px 20px;background:#0B0D12;color:#F2F4F7;font:17px/1.5 -apple-system,system-ui,sans-serif}"
                + "h1{font-size:26px;margin:0 0 8px}p{color:#C9D0DB}"
                + "textarea{width:100%;box-sizing:border-box;margin:12px 0;padding:12px;border-radius:12px;border:1px solid #2E3542;"
                + "background:#151922;color:#F2F4F7;font:15px monospace}"
                + "button{width:100%;padding:16px;border:0;border-radius:14px;background:#E30A17;color:#fff;font-size:17px;font-weight:700}"
                + "</style></head><body><h1>" + title + "</h1>" + body + "</body></html>";
    }

    /** TV'nin ev ağındaki IP adresi (ör. 192.168.1.20), bulunamazsa null. */
    static String localIp() {
        try {
            for (NetworkInterface ni : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (!ni.isUp() || ni.isLoopback()) continue;
                for (InetAddress a : Collections.list(ni.getInetAddresses())) {
                    if (a instanceof Inet4Address && a.isSiteLocalAddress()) return a.getHostAddress();
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }
}
