package com.zorme.browser;

import android.os.Handler;
import android.os.Looper;

import java.io.Serializable;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLHandshakeException;
import javax.net.ssl.SSLPeerUnverifiedException;

/**
 * 网站 SSL/TLS 证书信息获取。
 * 通过 HttpsURLConnection 与目标服务器完成一次 TLS 握手，取回完整证书链并解析。
 */
public class SslInfo implements Serializable {

    public String subject;        // 颁发给（CN）
    public String subjectRaw;     // 颁发给（完整 DN）
    public String issuer;         // 颁发者（CN）
    public String issuerRaw;      // 颁发者（完整 DN）
    public Date notBefore;        // 生效时间
    public Date notAfter;         // 过期时间
    public String serial;         // 序列号（十六进制）
    public String sigAlg;         // 签名算法
    public String sha256;         // SHA-256 指纹
    public String sha1;           // SHA-1 指纹
    public List<String> sans = new ArrayList<>();  // 备用域名
    public int chainSize;         // 证书链长度
    public long validDaysLeft;    // 剩余有效天数（负数表示已过期）

    /** ok：握手并解析成功；info 为 null 时 errorText 是原因 */
    public interface Callback {
        void onResult(boolean ok, String errorText, SslInfo info);
    }

    /** 后台发起证书获取，回调回主线程。url 必须是 https:// */
    public static void fetch(String urlStr, final Callback cb) {
        final Handler main = new Handler(Looper.getMainLooper());
        new Thread(() -> {
            SslInfo info = null;
            String error = null;
            try {
                URL url = new URL(urlStr);
                if (!"https".equals(url.getProtocol())) {
                    throw new IllegalArgumentException("NOT_HTTPS");
                }
                HttpsURLConnection conn = (HttpsURLConnection) url.openConnection();
                conn.setConnectTimeout(10000);
                conn.setReadTimeout(10000);
                conn.setInstanceFollowRedirects(false);
                conn.setRequestMethod("GET");
                conn.setRequestProperty("User-Agent",
                        "Mozilla/5.0 (Linux; Android) ZormeBrowser/" + BuildConfig.VERSION_NAME);
                conn.connect();
                Certificate[] certs = conn.getServerCertificates();
                List<X509Certificate> chain = new ArrayList<>();
                if (certs != null) {
                    for (Certificate c : certs) {
                        if (c instanceof X509Certificate) chain.add((X509Certificate) c);
                    }
                }
                conn.disconnect();
                if (chain.isEmpty()) {
                    error = "服务器未返回证书";
                } else {
                    info = fromCertificates(chain);
                }
            } catch (SSLPeerUnverifiedException e) {
                error = "证书验证失败：" + e.getMessage();
            } catch (SSLHandshakeException e) {
                error = "TLS 握手失败：" + e.getMessage();
            } catch (IllegalArgumentException e) {
                error = "仅支持 https:// 网站的证书";
            } catch (Exception e) {
                error = e.getMessage() == null ? e.toString() : e.getMessage();
            }
            final SslInfo fInfo = info;
            final String fError = error;
            main.post(() -> cb.onResult(fInfo != null, fError, fInfo));
        }, "zorme-ssl-fetch").start();
    }

    /** 从完整证书链解析信息。chain[0] 为站点证书 */
    public static SslInfo fromCertificates(List<X509Certificate> chain) throws Exception {
        SslInfo s = new SslInfo();
        X509Certificate c = chain.get(0);
        s.subjectRaw = c.getSubjectX500Principal().getName(javax.security.auth.x500.X500Principal.RFC2253);
        s.issuerRaw = c.getIssuerX500Principal().getName(javax.security.auth.x500.X500Principal.RFC2253);
        s.subject = cnOf(s.subjectRaw);
        s.issuer = cnOf(s.issuerRaw);
        s.notBefore = c.getNotBefore();
        s.notAfter = c.getNotAfter();
        s.serial = c.getSerialNumber().toString(16).toUpperCase(Locale.US);
        s.sigAlg = c.getSigAlgName();
        s.sha256 = fingerprint("SHA-256", c);
        s.sha1 = fingerprint("SHA-1", c);
        s.chainSize = chain.size();
        s.validDaysLeft = (c.getNotAfter().getTime() - System.currentTimeMillis()) / 86400000L;
        try {
            for (List<?> item : c.getSubjectAlternativeNames()) {
                if (item == null || item.size() < 2) continue;
                Integer type = (Integer) item.get(0);
                if (type != null && type == 2 && item.get(1) != null) {
                    s.sans.add(item.get(1).toString());
                }
            }
        } catch (Exception ignored) {
        }
        return s;
    }

    /** 从 DN 字符串提取 CN，没有 CN 时返回原串 */
    public static String cnOf(String dn) {
        if (dn == null || dn.isEmpty()) return "";
        for (String part : dn.split(",")) {
            part = part.trim();
            if (part.regionMatches(true, 0, "CN=", 0, 3)) {
                return part.length() > 3 ? part.substring(3) : "";
            }
        }
        return dn;
    }

    /** 计算证书指纹，冒号分隔的大写十六进制 */
    public static String fingerprint(String alg, X509Certificate c) throws Exception {
        byte[] digest = MessageDigest.getInstance(alg).digest(c.getEncoded());
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < digest.length; i++) {
            if (i > 0) sb.append(':');
            sb.append(String.format(Locale.US, "%02X", digest[i]));
        }
        return sb.toString();
    }

    /** 面向用户展示的摘要文本 */
    public String summary() {
        SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd", Locale.CHINA);
        StringBuilder sb = new StringBuilder();
        sb.append("颁发给：").append(subject).append('\n');
        sb.append("颁发者：").append(issuer).append('\n');
        sb.append("有效期：").append(fmt.format(notBefore)).append(" 至 ").append(fmt.format(notAfter));
        if (validDaysLeft >= 0) {
            sb.append("（剩余 ").append(validDaysLeft).append(" 天）");
        } else {
            sb.append("（已过期 ").append(-validDaysLeft).append(" 天）");
        }
        sb.append('\n');
        sb.append("签名算法：").append(sigAlg).append('\n');
        if (serial != null && serial.length() > 24) {
            sb.append("序列号：").append(serial.substring(0, 24)).append("…\n");
        } else {
            sb.append("序列号：").append(serial).append('\n');
        }
        sb.append("SHA-256 指纹：\n").append(sha256).append('\n');
        if (!sans.isEmpty()) {
            int shown = Math.min(6, sans.size());
            sb.append("备用域名：");
            for (int i = 0; i < shown; i++) {
                if (i > 0) sb.append("、");
                sb.append(sans.get(i));
            }
            if (sans.size() > shown) sb.append(" 等 ").append(sans.size()).append(" 个");
            sb.append('\n');
        }
        sb.append("证书链：").append(chainSize).append(" 张证书");
        return sb.toString();
    }
}
