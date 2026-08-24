package EndZone.services;

import EndZone.config.BotConfig;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.Message.Attachment;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Pulls Ticket Tool close posts (HTML transcript + owner embed) into {@code ez_modmail_logs}.
 */
public class TicketToolLogImporter {
    private static final Logger logger = LoggerFactory.getLogger(TicketToolLogImporter.class);
    private static final Pattern MENTION = Pattern.compile("<@!?(\\d{17,20})>");
    private static final Pattern SNOWFLAKE = Pattern.compile("(\\d{17,20})");
    private static final Pattern PARENS_ID = Pattern.compile("\\((\\d{17,20})\\)");
    private static final Pattern TAG = Pattern.compile("(?s)<[^>]+>");
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    public enum Result {
        IMPORTED, DUPLICATE, SKIPPED, FAILED
    }

    public static boolean isTicketTool(User author) {
        if (author == null) return false;
        String id = author.getId();
        return BotConfig.TICKET_TOOL_BOT_ID.equals(id)
                || BotConfig.TICKET_TOOL_BOT_ID_ALT.equals(id)
                || author.getName().toLowerCase(Locale.ROOT).contains("ticket tool");
    }

    public static boolean isLogChannel(String channelId) {
        return BotConfig.MODMAIL_LOG_CHANNEL_ID.equals(channelId);
    }

    public void importAsync(Message message) {
        if (message == null || !isLogChannel(message.getChannel().getId())) {
            return;
        }
        if (!isTicketTool(message.getAuthor()) || findHtmlAttachment(message) == null) {
            return;
        }
        CompletableFuture.runAsync(() -> {
            try {
                importIfTranscript(message);
            } catch (Exception e) {
                logger.warn("[TicketToolImport] Live import failed for {}: {}", message.getId(), e.getMessage());
            }
        });
    }

    public Result importIfTranscript(Message message) {
        if (message == null || !isTicketTool(message.getAuthor())) {
            return Result.SKIPPED;
        }
        String raw = message.getContentRaw() == null ? "" : message.getContentRaw().toLowerCase(Locale.ROOT);
        if (raw.contains("are you sure") && raw.contains("close")) {
            return Result.SKIPPED;
        }
        Attachment html = findHtmlAttachment(message);
        if (html == null) {
            return Result.SKIPPED;
        }

        String userId = extractOwnerId(message);
        if (userId == null) {
            logger.warn("[TicketToolImport] No ticket owner on message {}", message.getId());
            return Result.FAILED;
        }

        String ticketKey = extractTicketChannelId(message);
        if (ticketKey == null) {
            ticketKey = message.getId();
        }
        String logUuid = UUID.nameUUIDFromBytes(("ticket-tool:" + ticketKey).getBytes(StandardCharsets.UTF_8)).toString();

        ModmailService modmail = ServiceManager.getModmailService();
        if (modmail == null) {
            return Result.FAILED;
        }

        String transcript = downloadTranscriptText(html);
        if (transcript == null || transcript.isBlank()) {
            transcript = "(Could not download Ticket Tool HTML transcript)";
        }

        String category = nullTo(extractNamedField(message, "panel name", "panel"), "Ticket Tool");
        String ticketName = extractNamedField(message, "ticket name");
        if (ticketName != null && !ticketName.isBlank()) {
            category = category + " · " + ticketName.replaceAll("\\s*\\(\\d{17,20}\\)\\s*$", "").trim();
        }

        long closedAt = message.getTimeCreated().toInstant().toEpochMilli();
        boolean ok = modmail.insertImportedLog(
                logUuid,
                userId,
                BotConfig.TICKET_TOOL_BOT_ID,
                "Ticket Tool",
                category,
                transcript,
                closedAt,
                closedAt,
                html.getUrl()
        );
        if (ok) {
            logger.info("[TicketToolImport] Imported ticket {} for user {}", ticketKey, userId);
            return Result.IMPORTED;
        }
        return Result.FAILED;
    }

    private static Attachment findHtmlAttachment(Message message) {
        for (Attachment att : message.getAttachments()) {
            String name = att.getFileName() == null ? "" : att.getFileName().toLowerCase(Locale.ROOT);
            String type = att.getContentType() == null ? "" : att.getContentType().toLowerCase(Locale.ROOT);
            if (name.endsWith(".html") || name.endsWith(".htm") || type.contains("html")) {
                return att;
            }
        }
        return null;
    }

    private static String extractOwnerId(Message message) {
        String field = extractNamedField(message, "ticket owner", "owner");
        String fromField = firstSnowflake(field);
        if (fromField != null
                && !fromField.equals(BotConfig.TICKET_TOOL_BOT_ID)
                && !fromField.equals(BotConfig.TICKET_TOOL_BOT_ID_ALT)) {
            return fromField;
        }
        for (User user : message.getMentions().getUsers()) {
            if (!user.isBot()) {
                return user.getId();
            }
        }
        Matcher mention = MENTION.matcher(message.getContentRaw() == null ? "" : message.getContentRaw());
        return mention.find() ? mention.group(1) : null;
    }

    private static String extractTicketChannelId(Message message) {
        String channelField = extractNamedField(message, "channel", "ticket name");
        if (channelField == null) {
            return null;
        }
        Matcher parens = PARENS_ID.matcher(channelField);
        if (parens.find()) {
            return parens.group(1);
        }
        return firstSnowflake(channelField);
    }

    private static String extractNamedField(Message message, String... nameParts) {
        for (MessageEmbed embed : message.getEmbeds()) {
            for (MessageEmbed.Field field : embed.getFields()) {
                if (field.getName() == null) continue;
                String name = field.getName().toLowerCase(Locale.ROOT);
                for (String part : nameParts) {
                    if (name.equals(part) || name.contains(part)) {
                        return field.getValue();
                    }
                }
            }
        }
        return null;
    }

    private static String firstSnowflake(String text) {
        if (text == null) return null;
        Matcher mention = MENTION.matcher(text);
        if (mention.find()) {
            return mention.group(1);
        }
        Matcher id = SNOWFLAKE.matcher(text);
        return id.find() ? id.group(1) : null;
    }

    private static String downloadTranscriptText(Attachment html) {
        String raw = downloadRawHtml(html);
        if (raw == null || raw.isBlank()) {
            return null;
        }
        if (TicketToolTranscriptRenderer.isTicketToolPayload(raw)) {
            String page = TicketToolTranscriptRenderer.render(raw);
            if (page != null && !page.isBlank()) {
                logger.info("[TicketToolImport] Decoded Ticket Tool chat from {}", html.getFileName());
                return page;
            }
            logger.warn("[TicketToolImport] Ticket Tool file {} had no decodable messages — storing raw HTML",
                    html.getFileName());
            return raw;
        }
        String conversation = extractConversation(raw);
        if (conversation != null && conversation.length() >= 20) {
            return conversation;
        }
        if (isHtmlDocument(raw)) {
            return raw;
        }
        return htmlToText(raw);
    }

    private static String downloadRawHtml(Attachment html) {
        try (java.io.InputStream in = html.getProxy().download().get(30, java.util.concurrent.TimeUnit.SECONDS)) {
            if (in != null) {
                String body = new String(in.readAllBytes(), StandardCharsets.UTF_8);
                if (!body.isBlank()) {
                    return body;
                }
            }
        } catch (Exception e) {
            logger.warn("[TicketToolImport] JDA download failed for {}: {}", html.getFileName(), e.getMessage());
        }
        String url = html.getUrl();
        if (url == null || url.isBlank()) {
            url = html.getProxyUrl();
        }
        if (url == null || url.isBlank()) {
            return null;
        }
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(30))
                    .header("User-Agent", "Mozilla/5.0 (compatible; EndZoneBot/1.0; +https://discord.com)")
                    .header("Accept", "text/html,application/xhtml+xml,*/*")
                    .GET()
                    .build();
            HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                logger.warn("[TicketToolImport] HTML download HTTP {} for {}", response.statusCode(), html.getFileName());
                return null;
            }
            return response.body();
        } catch (Exception e) {
            logger.warn("[TicketToolImport] HTML download failed for {}: {}", html.getFileName(), e.getMessage());
            return null;
        }
    }

    /**
     * Pull author + message lines out of discord-html-transcripts / Ticket Tool markup.
     */
    static String extractConversation(String html) {
        if (html == null || html.isBlank()) {
            return "";
        }
        StringBuilder out = new StringBuilder();
        String lastAuthor = "Unknown";
        Matcher token = Pattern.compile(
                "chatlog__author-name\"[^>]*>([^<]+)<"
                        + "|chatlog__timestamp\"[^>]*>([^<]*)<"
                        + "|chatlog__markdown-preserve\"[^>]*>([\\s\\S]*?)</span>"
                        + "|chatlog__content\"[^>]*>([\\s\\S]*?)</div>",
                Pattern.CASE_INSENSITIVE
        ).matcher(html);
        String pendingTime = "";
        while (token.find()) {
            if (token.group(1) != null) {
                lastAuthor = decodeEntities(TAG.matcher(token.group(1)).replaceAll("")).trim();
            } else if (token.group(2) != null) {
                pendingTime = decodeEntities(token.group(2)).trim();
            } else {
                String bodyHtml = token.group(3) != null ? token.group(3) : token.group(4);
                String body = htmlToText(bodyHtml == null ? "" : bodyHtml);
                if (body.isBlank()) {
                    continue;
                }
                if (out.length() > 0) {
                    out.append("\n");
                }
                if (!pendingTime.isBlank()) {
                    out.append("[").append(pendingTime).append("] ");
                }
                out.append(lastAuthor).append(": ").append(body);
                pendingTime = "";
            }
        }
        if (out.length() >= 20) {
            return out.toString();
        }

        Matcher discordMsg = Pattern.compile(
                "(?is)<discord-message\\b([^>]*)>([\\s\\S]*?)</discord-message>"
        ).matcher(html);
        while (discordMsg.find()) {
            String attrs = discordMsg.group(1);
            String inner = htmlToText(discordMsg.group(2));
            String author = "Unknown";
            Matcher authorAttr = Pattern.compile("author=\"([^\"]+)\"").matcher(attrs);
            if (authorAttr.find()) {
                author = decodeEntities(authorAttr.group(1)).trim();
            }
            if (inner.isBlank()) {
                continue;
            }
            if (out.length() > 0) {
                out.append("\n");
            }
            out.append(author).append(": ").append(inner);
        }
        if (out.length() >= 20) {
            return out.toString();
        }

        Matcher classic = Pattern.compile(
                "(?is)class=\"[^\"]*username[^\"]*\"[^>]*>([^<]+)</[^>]+>[\\s\\S]{0,400}?"
                        + "class=\"[^\"]*(?:markup|message-content|text|content)[^\"]*\"[^>]*>([\\s\\S]*?)</div>"
        ).matcher(html);
        while (classic.find()) {
            String author = decodeEntities(TAG.matcher(classic.group(1)).replaceAll("")).trim();
            String body = htmlToText(classic.group(2));
            if (body.isBlank()) {
                continue;
            }
            if (out.length() > 0) {
                out.append("\n");
            }
            out.append(author).append(": ").append(body);
        }
        if (out.length() >= 20) {
            return out.toString();
        }

        Matcher jsonContent = Pattern.compile("\"content\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"").matcher(html);
        StringBuilder jsonOut = new StringBuilder();
        while (jsonContent.find()) {
            String line = unescapeJson(jsonContent.group(1)).trim();
            if (line.isBlank() || line.startsWith("http") && line.length() < 12) {
                continue;
            }
            if (jsonOut.length() > 0) {
                jsonOut.append("\n");
            }
            jsonOut.append(line);
        }
        return jsonOut.length() >= 20 ? jsonOut.toString() : "";
    }

    private static boolean isHtmlDocument(String raw) {
        String head = raw.substring(0, Math.min(500, raw.length())).toLowerCase(Locale.ROOT);
        return head.contains("<html") || head.contains("<!doctype html");
    }

    private static String unescapeJson(String s) {
        return s.replace("\\n", "\n")
                .replace("\\r", "")
                .replace("\\t", " ")
                .replace("\\\"", "\"")
                .replace("\\\\", "\\");
    }

    private static String decodeEntities(String s) {
        if (s == null) return "";
        return s.replace("&nbsp;", " ")
                .replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .replace("&#39;", "'")
                .replace("&apos;", "'");
    }

    static String htmlToText(String html) {
        if (html == null || html.isBlank()) {
            return "";
        }
        String s = html.replaceAll("(?is)<script[^>]*>.*?</script>", "")
                .replaceAll("(?is)<style[^>]*>.*?</style>", "")
                .replaceAll("(?i)<br\\s*/?>", "\n")
                .replaceAll("(?i)</(p|div|tr|h[1-6]|li|blockquote)>", "\n")
                .replaceAll("(?i)</td>", "  ");
        s = TAG.matcher(s).replaceAll("");
        s = decodeEntities(s);
        s = s.replaceAll("[ \\t]+", " ");
        s = s.replaceAll("\\n{3,}", "\n\n");
        return s.trim();
    }

    private static String nullTo(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }
}
