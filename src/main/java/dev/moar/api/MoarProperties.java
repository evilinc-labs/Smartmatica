package dev.moar.api;

import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

// Loads and persists API/webhook settings from config/moar/moar.properties.
public final class MoarProperties {

    private static final Logger LOGGER = LoggerFactory.getLogger("MOAR/Config");

    private static final Path FILE = FabricLoader.getInstance()
            .getConfigDir()
            .resolve("moar")
            .resolve("moar.properties");

    // API server
    private boolean apiEnabled;
    private String apiBindAddress;
    private int apiPort;
    private String apiKey;

    // Webhook (n8n, etc.)
    private String webhookUrl;

    private MoarProperties() {}

    // Defaults
    private static final boolean API_ENABLED = false;
    private static final String API_BIND = "127.0.0.1";
    private static final int API_PORT = 8585;
    private static final String API_KEY = "";
    private static final String WEBHOOK_URL = "";

    public static MoarProperties load() {
        MoarProperties cfg = new MoarProperties();
        Properties props = new Properties();

        if (Files.exists(FILE)) {
            try (InputStream in = Files.newInputStream(FILE)) {
                props.load(in);
            } catch (IOException e) {
                LOGGER.warn("Failed to read {}, using defaults", FILE, e);
            }
        }

        cfg.apiEnabled = Boolean.parseBoolean(props.getProperty("api.enabled",
                String.valueOf(API_ENABLED)));
        cfg.apiBindAddress = props.getProperty("api.bind", API_BIND);
        cfg.apiPort = parsePort(props.getProperty("api.port",
                String.valueOf(API_PORT)));
        cfg.apiKey = props.getProperty("api.key", API_KEY);
        cfg.webhookUrl = props.getProperty("webhook.url", WEBHOOK_URL);

        // Write defaults on first run so users can see the available keys
        if (!Files.exists(FILE)) {
            cfg.save();
        }

        LOGGER.info("Loaded config from {}", FILE);
        return cfg;
    }

    public void save() {
        try {
            Files.createDirectories(FILE.getParent());
            Properties props = new Properties();
            props.setProperty("api.enabled", String.valueOf(apiEnabled));
            props.setProperty("api.bind", apiBindAddress);
            props.setProperty("api.port", String.valueOf(apiPort));
            props.setProperty("api.key", apiKey);
            props.setProperty("webhook.url", webhookUrl);

            try (OutputStream out = Files.newOutputStream(FILE)) {
                props.store(out, "MOAR API / webhook configuration");
            }
        } catch (IOException e) {
            LOGGER.error("Failed to save {}", FILE, e);
        }
    }

    // Getters

    public boolean isApiEnabled()    { return apiEnabled; }
    public String getApiBindAddress() { return apiBindAddress; }
    public int getApiPort()          { return apiPort; }
    public String getApiKey()        { return apiKey; }
    public String getWebhookUrl()    { return webhookUrl; }

    // Setters (mutate + persist)

    public void setApiEnabled(boolean v) { apiEnabled = v; save(); }
    public void setApiBindAddress(String v) { apiBindAddress = v; save(); }
    public void setApiPort(int v) { apiPort = v; save(); }
    public void setApiKey(String v) { apiKey = v; save(); }
    public void setWebhookUrl(String v) { webhookUrl = v; save(); }

    private static int parsePort(String s) {
        try {
            int p = Integer.parseInt(s);
            if (p > 0 && p <= 65535) return p;
        } catch (NumberFormatException ignored) {}
        return API_PORT;
    }
}
