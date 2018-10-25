package io.arusland.telegram;

import io.arusland.twitter.TwitterHelper;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.Validate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.telegram.telegrambots.api.methods.send.SendDocument;
import org.telegram.telegrambots.api.methods.send.SendMessage;
import org.telegram.telegrambots.api.objects.Update;
import org.telegram.telegrambots.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.api.objects.replykeyboard.buttons.KeyboardRow;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.exceptions.TelegramApiException;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Ruslan Absalyamov
 * @since 1.0
 */
public class MediaExtTelegramBot extends TelegramLongPollingBot {
    private final Logger log = LoggerFactory.getLogger(getClass());
    private static final String UNKNOWN_COMMAND = "⚠️Unknown command⚠";
    private static final int TEXT_MESSAGE_MAX_LENGTH = 4096;
    private final BotConfig config;
    private final long adminChatId;
    private final TwitterHelper twitter;

    public MediaExtTelegramBot(BotConfig config) throws TelegramApiException {
        this.config = Validate.notNull(config, "config");
        this.adminChatId = (long) config.getAdminId();
        this.twitter = new TwitterHelper(new File("/tmp"), new File(config.getFfMpegPath()),
                new File(config.getFfMpegPath()));
        log.info(String.format("Media-ext bot started"));
        sendMarkdownMessage(adminChatId, "*Bot started!*");
    }

    public void onUpdateReceived(Update update) {
        log.info("got message: {}", update);

        if (update.hasMessage()) {
            long chatId = update.getMessage().getChatId();
            int userId = update.getMessage().getFrom().getId();

            if (adminChatId != userId) {
                try {
                    sendMarkdownMessage(chatId, "⚠️*Sorry, this bot only for his owner :)*️");
                } catch (TelegramApiException e) {
                    e.printStackTrace();
                    log.error(e.getMessage(), e);
                }
                return;
            }

            try {
                if (update.getMessage().hasText()) {
                    String command = update.getMessage().getText();

                    if (command.startsWith("/")) {
                        String cmd = parseCommand(command);
                        String arg = parseArg(command);
                        handleCommand(cmd, arg, chatId, userId);
                    } else {
                        handleUrl(command, chatId);
                    }
                } else {
                    sendMessage(chatId, UNKNOWN_COMMAND);
                }
            } catch (Exception e) {
                e.printStackTrace();
                log.error(e.getMessage(), e);
                try {
                    sendMessage(chatId, "ERROR: " + e.getMessage());
                } catch (TelegramApiException e1) {
                    e1.printStackTrace();
                    log.error(e.getMessage(), e);
                }
            }
        }
    }

    private void handleUrl(String command, long chatId) throws TelegramApiException, IOException {
        try {
            URL url = new URL(command);
            sendMarkdownMessage(chatId, "_Please, wait..._");
            // TODO: make async
            File file = twitter.downloadMediaFrom(url);

            if (file != null && file.exists()) {
                sendFile(chatId, file);
                FileUtils.deleteQuietly(file);
            } else {
                sendMarkdownMessage(chatId, "⛔*Media not found* \uD83D\uDE1E");
            }
        } catch (MalformedURLException e) {
            log.warn(e.getMessage(), e);
            sendMarkdownMessage(chatId, "⛔*Invalid url*");
        }
    }

    private void handleCommand(String command, String arg, long chatId, int userId) throws TelegramApiException, IOException, URISyntaxException {
        if ("help".equals(command) || "start".equals(command)) {
            handleHelpCommand(chatId);
        } else if ("kill".equals(command)) {
            sendMarkdownMessage(chatId, "*Bye bye*");
            System.exit(0);
        } else {
            sendMessage(chatId, UNKNOWN_COMMAND);
        }
    }

    private void handleHelpCommand(long chatId) throws TelegramApiException {
        sendMarkdownMessage(chatId, "*Please, send me some url*");
    }

    public void sendFile(Long chatId, File file) {
        SendDocument doc = new SendDocument();
        doc.setChatId(chatId.toString());
        doc.setNewDocument(file);

        try {
            log.info("Sending file: " + doc);
            sendDocument(doc);
        } catch (TelegramApiException e) {
            log.error(e.getMessage(), e);
        }
    }

    private String parseArg(String command) {
        int index = indexOfDelim(command);

        return index > 0 && (index + 1) < command.length() ? command.substring(index + 1) : "";
    }

    private String parseCommand(String command) {
        int index = indexOfDelim(command);

        return index > 0 ? command.substring(1, index) : command.substring(1);
    }

    private int indexOfDelim(String command) {
        int index1 = command.indexOf(" ");
        int index2 = command.indexOf("_");

        return minPositive(index1, index2);
    }

    private int minPositive(int index1, int index2) {
        if (index1 >= 0) {
            if (index2 >= 0) {
                return Math.min(index1, index2);
            }

            return index1;
        }

        return index2;
    }

    public String getBotUsername() {
        return config.getBotName();
    }

    public String getBotToken() {
        return config.getBotToken();
    }

    private void sendHtmlMessage(Long chatId, String message) throws TelegramApiException {
        sendMessage(chatId, message, false, true);
    }

    private void sendMarkdownMessage(Long chatId, String message) throws TelegramApiException {
        sendMessage(chatId, message, true, false);
    }

    private void sendMessage(Long chatId, String message) throws TelegramApiException {
        sendMessage(chatId, message, false, false);
    }

    private void sendMessage(Long chatId, String message, boolean markDown, boolean html) throws TelegramApiException {
        if (message.length() > TEXT_MESSAGE_MAX_LENGTH) {
            String part1 = message.substring(0, TEXT_MESSAGE_MAX_LENGTH);
            sendMessage(chatId, part1);
            String part2 = message.substring(TEXT_MESSAGE_MAX_LENGTH);
            sendMessage(chatId, part2);
        } else {
            SendMessage sendMessage = new SendMessage();

            if (markDown) {
                sendMessage.enableMarkdown(markDown);
            } else if (html) {
                sendMessage.enableHtml(html);
            }
            sendMessage.setChatId(chatId.toString());
            sendMessage.setText(message);

            applyKeyboard(sendMessage);

            log.info(String.format("send (length: %d): %s", message.length(), message));

            execute(sendMessage);
        }
    }

    private void applyKeyboard(SendMessage sendMessage) {
        ReplyKeyboardMarkup replyKeyboardMarkup = new ReplyKeyboardMarkup();
        sendMessage.setReplyMarkup(replyKeyboardMarkup);
        replyKeyboardMarkup.setSelective(true);
        replyKeyboardMarkup.setResizeKeyboard(true);

        List<KeyboardRow> keyboard = new ArrayList<>();

        KeyboardRow keyboardFirstRow = new KeyboardRow();
        keyboardFirstRow.add("/help");

        keyboard.add(keyboardFirstRow);
        replyKeyboardMarkup.setKeyboard(keyboard);
    }
}
