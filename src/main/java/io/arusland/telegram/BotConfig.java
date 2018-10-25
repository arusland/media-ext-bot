package io.arusland.telegram;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Validate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import java.util.stream.Collectors;

/**
 * Simple bot configuration
 */
public class BotConfig {
    private final Logger log = LoggerFactory.getLogger(BotConfig.class);
    private final Properties prop;
    private final File configFile;

    protected BotConfig(Properties prop, File configFile) {
        this.prop = Validate.notNull(prop, "prop");
        this.configFile = configFile;
    }

    public String getBotName() {
        return getProperty("bot.name");
    }

    public String getBotToken() {
        return getProperty("bot.token");
    }

    public String getFfMpegPath() {
        return getProperty("ffmpeg.path");
    }

    public String getFfProbePath() {
        return getProperty("ffprobe.path");
    }

    /**
     * Returns admin user's id.
     *
     * @return Admin User's id.
     */
    public int getAdminId() {
        List<Integer> selectedUsers = getAllowedUsersIds();

        return selectedUsers.isEmpty() ? 0 : selectedUsers.get(0);
    }

    /**
     * Returns allowed users ids.
     */
    public List<Integer> getAllowedUsersIds() {
        if (prop.containsKey("allowed.userids")) {
            String ids = getProperty("allowed.userids");
            try {
                return Arrays.stream(ids.split(","))
                        .filter(p -> !p.isEmpty())
                        .map(p -> Integer.parseInt(p))
                        .collect(Collectors.toList());
            } catch (NumberFormatException ex) {
                log.error(ex.getMessage(), ex);
            }
        }

        return Collections.emptyList();
    }

    private String getProperty(String key) {
        return Validate.notNull(prop.getProperty(key),
                "Configuration not found for key: " + key);
    }

    private String getProperty(String key, String defValue) {
        String val = prop.getProperty(key);

        return StringUtils.defaultString(val, defValue);
    }

    public static BotConfig load(String fileName) {
        Properties prop = new Properties();
        File file = null;

        try {
            file = new File(fileName).getCanonicalFile();
            try (InputStream input = new FileInputStream(fileName)) {
                prop.load(input);
            }
        } catch (FileNotFoundException e) {
            throw new RuntimeException(e);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        return new BotConfig(prop, file);
    }
}
