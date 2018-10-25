package io.arusland.telegram;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.telegram.telegrambots.ApiContextInitializer;
import org.telegram.telegrambots.TelegramBotsApi;
import org.telegram.telegrambots.exceptions.TelegramApiException;

import java.net.Authenticator;
import java.net.PasswordAuthentication;

/**
 * @author Ruslan Absalyamov
 * @since 1.0
 */
public class MainApp {
    private static final Logger log = LoggerFactory.getLogger(MainApp.class);

    public static void main(String[] args) {
         /*
    System.setProperty("socksProxyHost", "188.166.XXX.XXX")
    System.setProperty("socksProxyPort", "1080")
    System.setProperty("java.net.socks.username", "XXX")
    System.setProperty("java.net.socks.password", "XXX")
    */

        String socksUsername = System.getProperty("java.net.socks.username");
        String socksPassword = System.getProperty("java.net.socks.password");

        if (StringUtils.isNotBlank(socksUsername) &&
                StringUtils.isNotBlank(socksPassword)) {
            log.warn("using SOCKS: socksUsername: " + socksUsername);
            Authenticator.setDefault(new ProxyAuth(socksUsername, socksPassword));
        }

        ApiContextInitializer.init();
        TelegramBotsApi telegramBotsApi = new TelegramBotsApi();
        try {
            BotConfig config = BotConfig.load("application.properties");
            telegramBotsApi.registerBot(new MediaExtTelegramBot(config));
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }

    private static class ProxyAuth extends Authenticator {
        private final PasswordAuthentication auth;

        public ProxyAuth(String socksUsername, String socksPassword) {
            auth = new PasswordAuthentication(socksUsername, socksPassword.toCharArray());
        }

        @Override
        protected PasswordAuthentication getPasswordAuthentication() {
            return auth;
        }
    }
}
