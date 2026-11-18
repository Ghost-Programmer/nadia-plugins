package com.nadia.plugin.web.server;

import com.nadia.plugin.web.config.WebInterfaceConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Manages TLS {@link SSLContext} initialization for HTTPS mode in {@link WebInterfaceServer}.
 *
 * <p>Supports two modes:</p>
 * <ul>
 *   <li><b>Custom Keystore</b>: Loads a user-provided PKCS#12 ({@code .p12}, {@code .pfx}) or
 *       Java KeyStore ({@code .jks}) file configured in the Web Interface Settings tab.</li>
 *   <li><b>Automatic Self-Signed Keystore</b>: When HTTPS is enabled and no custom keystore path is
 *       specified, automatically generates and caches a 2048-bit RSA PKCS#12 certificate
 *       ({@code nadia-web-selfsigned.p12}) inside the plugin's data directory with {@code localhost},
 *       {@code 127.0.0.1}, and active LAN IPv4 Subject Alternative Names (SANs) via the JDK's
 *       bundled {@code keytool} utility.</li>
 * </ul>
 */
public final class TlsKeystoreManager {

    /** Filename of the auto-generated self-signed PKCS#12 keystore inside {@code <pluginDataDir>/tls/}. */
    public static final String SELF_SIGNED_KEYSTORE_FILENAME = "nadia-web-selfsigned.p12";

    private static final Logger logger = LoggerFactory.getLogger(TlsKeystoreManager.class);
    private static final char[] SELF_SIGNED_STORE_PASS = "nadia-web-selfsigned-tls".toCharArray();

    private TlsKeystoreManager() {
    }

    /**
     * Builds an initialized {@link SSLContext} according to the active {@link WebInterfaceConfig}.
     *
     * @param config        active Web Interface configuration
     * @param pluginDataDir plugin persistent data directory ({@code ~/.nadia/plugins/data/web-interface})
     * @return initialized TLS {@link SSLContext}
     * @throws Exception if keystore generation, loading, or TLS initialization fails
     */
    public static SSLContext buildSslContext(WebInterfaceConfig config, Path pluginDataDir) throws Exception {
        Path keystoreFile;
        char[] password;

        if (config != null && config.hasCustomKeystore()) {
            keystoreFile = Path.of(config.keystorePath().trim()).toAbsolutePath().normalize();
            if (!Files.isRegularFile(keystoreFile)) {
                throw new IOException("Configured SSL keystore file does not exist: " + keystoreFile);
            }
            String rawPass = config.keystorePassword() != null ? config.keystorePassword() : "";
            password = rawPass.toCharArray();
        } else {
            keystoreFile = ensureSelfSignedKeystore(pluginDataDir, false);
            password = SELF_SIGNED_STORE_PASS;
        }

        KeyStore ks = loadKeyStoreWithFallback(keystoreFile, password);
        KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        kmf.init(ks, password);

        TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        tmf.init(ks);

        SSLContext sslContext = SSLContext.getInstance("TLS");
        sslContext.init(kmf.getKeyManagers(), tmf.getTrustManagers(), new SecureRandom());
        return sslContext;
    }

    /**
     * Resolves the path to the auto-generated self-signed PKCS#12 keystore, creating or
     * regenerating it via the running JDK's {@code keytool} when requested.
     *
     * @param pluginDataDir plugin persistent data directory
     * @param forceRegen    {@code true} to delete any existing self-signed keystore and mint a fresh certificate
     * @return path to the generated {@code .p12} file
     * @throws IOException if certificate generation fails
     */
    public static synchronized Path ensureSelfSignedKeystore(Path pluginDataDir, boolean forceRegen) throws IOException {
        Path tlsDir = pluginDataDir.resolve("tls");
        Files.createDirectories(tlsDir);
        Path targetP12 = tlsDir.resolve(SELF_SIGNED_KEYSTORE_FILENAME);

        if (forceRegen) {
            Files.deleteIfExists(targetP12);
        }
        if (Files.isRegularFile(targetP12) && Files.size(targetP12) > 0) {
            return targetP12;
        }

        String keytoolExe = resolveKeytoolExecutable();
        String sanExtension = buildSanExtensionValue();
        String passStr = new String(SELF_SIGNED_STORE_PASS);

        List<String> cmd = List.of(
                keytoolExe,
                "-genkeypair",
                "-alias", "nadia-web",
                "-keyalg", "RSA",
                "-keysize", "2048",
                "-sigalg", "SHA256withRSA",
                "-validity", "3650",
                "-storetype", "PKCS12",
                "-keystore", targetP12.toAbsolutePath().toString(),
                "-storepass", passStr,
                "-keypass", passStr,
                "-dname", "CN=Nadia Web Interface, OU=Nadia AI, O=Miller Software Solutions LLC",
                "-ext", sanExtension
        );

        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.redirectErrorStream(true);
        try {
            Process proc = pb.start();
            String output = new String(proc.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            boolean finished = proc.waitFor(20, TimeUnit.SECONDS);
            if (!finished) {
                proc.destroyForcibly();
                throw new IOException("Timed out generating self-signed HTTPS keystore via keytool.");
            }
            if (proc.exitValue() != 0 || !Files.isRegularFile(targetP12)) {
                throw new IOException("keytool failed (exit " + proc.exitValue() + "): " + output.trim());
            }
            logger.info("[WebInterface] Generated self-signed TLS PKCS#12 keystore at {} ({})", targetP12, sanExtension);
            return targetP12;
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while generating self-signed HTTPS certificate.", ie);
        }
    }

    private static KeyStore loadKeyStoreWithFallback(Path keystoreFile, char[] password) throws Exception {
        String fileName = keystoreFile.getFileName().toString().toLowerCase();
        String primaryType = fileName.endsWith(".jks") ? "JKS" : "PKCS12";
        String secondaryType = "PKCS12".equals(primaryType) ? "JKS" : "PKCS12";

        try {
            return loadKeyStoreOfType(keystoreFile, password, primaryType);
        } catch (Exception primaryEx) {
            try {
                return loadKeyStoreOfType(keystoreFile, password, secondaryType);
            } catch (Exception ignored) {
                throw primaryEx;
            }
        }
    }

    private static KeyStore loadKeyStoreOfType(Path keystoreFile, char[] password, String type) throws Exception {
        KeyStore ks = KeyStore.getInstance(type);
        try (InputStream in = Files.newInputStream(keystoreFile)) {
            ks.load(in, password);
        }
        return ks;
    }

    private static String resolveKeytoolExecutable() {
        String javaHome = System.getProperty("java.home", "");
        if (!javaHome.isBlank()) {
            boolean isWin = System.getProperty("os.name", "").toLowerCase().contains("win");
            File candidate = new File(new File(javaHome, "bin"), isWin ? "keytool.exe" : "keytool");
            if (candidate.isFile()) {
                return candidate.getAbsolutePath();
            }
        }
        return "keytool";
    }

    private static String buildSanExtensionValue() {
        Set<String> entries = new LinkedHashSet<>();
        entries.add("dns:localhost");
        entries.add("ip:127.0.0.1");
        try {
            String hostName = InetAddress.getLocalHost().getHostName();
            if (hostName != null && !hostName.isBlank() && hostName.matches("^[a-zA-Z0-9.-]+$")) {
                entries.add("dns:" + hostName);
            }
        } catch (Exception ignored) {
        }
        try {
            Enumeration<NetworkInterface> nets = NetworkInterface.getNetworkInterfaces();
            while (nets != null && nets.hasMoreElements()) {
                NetworkInterface nif = nets.nextElement();
                if (!nif.isUp() || nif.isLoopback() || nif.isVirtual()) {
                    continue;
                }
                Enumeration<InetAddress> addrs = nif.getInetAddresses();
                while (addrs.hasMoreElements()) {
                    InetAddress addr = addrs.nextElement();
                    if (!addr.isLoopbackAddress() && addr.getAddress().length == 4) {
                        entries.add("ip:" + addr.getHostAddress());
                    }
                }
            }
        } catch (Exception ignored) {
        }
        List<String> list = new ArrayList<>(entries);
        return "SAN=" + String.join(",", list);
    }
}

