package EndZone.services;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Ticket Tool HTML files do not contain visible chat markup. The conversation is
 * a Base64 JSON blob ({@code let messages = "..."}) rendered by their website JS.
 * We decode that blob and build a Discord-like page EndZone can serve itself.
 */
final class TicketToolTranscriptRenderer {
    private static final ZoneId EASTERN = ZoneId.of("America/New_York");
    private static final DateTimeFormatter TIME_FMT =
            DateTimeFormatter.ofPattern("MMMM d, yyyy h:mm a").withZone(EASTERN);
    private static final Pattern MESSAGES = Pattern.compile(
            "let\\s+messages\\s*=\\s*[\"']([^\"']+)[\"']", Pattern.CASE_INSENSITIVE);
    private static final Pattern CHANNEL = Pattern.compile(
            "let\\s+channel\\s*=\\s*[\"']([^\"']+)[\"']", Pattern.CASE_INSENSITIVE);
    private static final Pattern SERVER = Pattern.compile(
            "let\\s+server\\s*=\\s*[\"']([^\"']+)[\"']", Pattern.CASE_INSENSITIVE);
    private static final Pattern MENTION = Pattern.compile("<@(!|&)?(\\d+)>");

    private TicketToolTranscriptRenderer() {
    }

    static boolean isTicketToolPayload(String html) {
        if (html == null) {
            return false;
        }
        String lower = html.toLowerCase();
        return lower.contains("tickettool.xyz")
                || lower.contains("window.convert")
                || lower.contains("<base-transcript>")
                || MESSAGES.matcher(html).find();
    }

    static String render(String html) {
        JsonArray messages = decodeArray(html, MESSAGES);
        if (messages == null || messages.isEmpty()) {
            return null;
        }
        JsonObject channel = decodeObject(html, CHANNEL);
        JsonObject server = decodeObject(html, SERVER);
        String channelName = jsonString(channel, "name", "ticket");
        String serverName = jsonString(server, "name", "CourtZone");

        Map<String, String> names = new LinkedHashMap<>();
        Map<String, JsonObject> byId = new LinkedHashMap<>();
        for (JsonElement el : messages) {
            if (!el.isJsonObject()) {
                continue;
            }
            JsonObject msg = el.getAsJsonObject();
            String msgId = jsonString(msg, "id", null);
            if (msgId != null) {
                byId.put(msgId, msg);
            }
            String userId = firstString(msg, "user_id", "userid");
            String name = firstString(msg, "nick", "username");
            if (userId != null && name != null) {
                names.putIfAbsent(userId, name);
            }
            absorbDiscordData(msg, names);
        }

        StringBuilder body = new StringBuilder();
        for (JsonElement el : messages) {
            if (!el.isJsonObject()) {
                continue;
            }
            String htmlMsg = renderMessage(el.getAsJsonObject(), names, byId);
            if (htmlMsg != null) {
                body.append(htmlMsg);
            }
        }
        if (body.isEmpty()) {
            return null;
        }

        return """
                <!DOCTYPE html>
                <html lang="en"><head><meta charset="utf-8"/>
                <meta name="viewport" content="width=device-width, initial-scale=1"/>
                <title>#%s — %s</title>
                <style>
                  html,body{margin:0;background:#313338;color:#dbdee1;font-family:"gg sans","Noto Sans",Whitney,"Helvetica Neue",Helvetica,Arial,sans-serif;font-size:16px;line-height:1.375}
                  header{display:flex;align-items:baseline;gap:10px;padding:16px 20px;background:#2b2d31;border-bottom:1px solid #1f2023;font-weight:600;font-size:16px}
                  header .hash{color:#80848e}
                  header .sub{color:#949ba4;font-weight:400;font-size:14px}
                  .log{padding:8px 0 48px}
                  .msg{display:flex;gap:16px;padding:2px 16px;margin-top:1.0625rem}
                  .msg:hover{background:#2e3035}
                  .av{width:40px;height:40px;border-radius:50%%;flex-shrink:0;background:#5865f2;object-fit:cover}
                  .av.fallback{opacity:.85}
                  .col{min-width:0;flex:1}
                  .head{margin-bottom:1px;line-height:1.375}
                  .name{font-weight:500;color:#fff;margin-right:.5rem}
                  .bot{display:inline-block;font-size:10px;font-weight:500;background:#5865f2;color:#fff;border-radius:3px;padding:0 4px;margin-right:.5rem;vertical-align:middle;line-height:15px;position:relative;top:-1px}
                  .time{font-size:.75rem;color:#949ba4;margin-left:.15rem}
                  .text{word-break:break-word;color:#dbdee1}
                  .mention{background:rgba(88,101,242,.3);color:#c9cdfb;border-radius:3px;padding:0 2px;font-weight:500}
                  .embed{margin-top:4px;max-width:520px;border-left:4px solid #1ec45c;background:#2b2d31;padding:8px 16px 8px 12px;border-radius:0 4px 4px 0}
                  .embed.yellow{border-left-color:#fbfe32}
                  .embed.gray{border-left-color:#4e5058}
                  .btns{display:flex;flex-wrap:wrap;gap:8px;margin-top:8px}
                  .btn{display:inline-flex;align-items:center;gap:6px;background:#4e5058;color:#fff;border-radius:3px;padding:2px 16px;font-size:14px;font-weight:500;min-height:32px}
                  .reply{display:flex;align-items:center;gap:4px;margin-bottom:4px;font-size:.8125rem;color:#b5bac1;max-width:100%%}
                  .reply .bar{width:2px;height:12px;background:#4e5058;border-radius:1px;margin-right:4px;flex-shrink:0}
                  .reply .rn{font-weight:500;color:#b5bac1;margin-right:4px}
                  .reply .rt{overflow:hidden;text-overflow:ellipsis;white-space:nowrap;opacity:.8}
                </style></head>
                <body>
                <header><span class="hash">#</span> %s <span class="sub">%s</span></header>
                <div class="log">
                %s
                </div></body></html>
                """.formatted(
                escape(channelName),
                escape(serverName),
                escape(channelName),
                escape(serverName),
                body
        );
    }

    private static String renderMessage(JsonObject msg, Map<String, String> names, Map<String, JsonObject> byId) {
        String name = firstString(msg, "nick", "username");
        if (name == null || name.isBlank()) {
            name = "Unknown";
        }
        boolean bot = msg.has("bot") && msg.get("bot").isJsonPrimitive() && msg.get("bot").getAsBoolean();
        long created = createdMillis(msg);
        String time = created > 0 ? TIME_FMT.format(Instant.ofEpochMilli(created)) : "";
        String content = msg.has("content") && !msg.get("content").isJsonNull()
                ? msg.get("content").getAsString() : "";

        String extra = renderEmbeds(msg, names);
        String buttons = renderButtons(msg);
        if (content.isBlank() && extra.isBlank() && buttons.isBlank()) {
            return null;
        }

        StringBuilder html = new StringBuilder();
        html.append("<div class=\"msg\">");
        String avatar = avatarUrl(msg);
        if (avatar != null) {
            html.append("<img class=\"av\" src=\"").append(escape(avatar)).append("\" alt=\"\"/>");
        } else {
            html.append("<div class=\"av fallback\"></div>");
        }
        html.append("<div class=\"col\">");
        html.append(renderReply(msg, names, byId));
        html.append("<div class=\"head\"><span class=\"name\">").append(escape(name)).append("</span>");
        if (bot) {
            html.append("<span class=\"bot\">BOT</span>");
        }
        if (!time.isBlank()) {
            html.append("<span class=\"time\">").append(escape(time)).append("</span>");
        }
        html.append("</div>");
        if (!content.isBlank()) {
            html.append("<div class=\"text\">").append(formatContent(content, names)).append("</div>");
        }
        html.append(extra);
        html.append(buttons);
        html.append("</div></div>\n");
        return html.toString();
    }

    private static String renderReply(JsonObject msg, Map<String, String> names, Map<String, JsonObject> byId) {
        if (!msg.has("reference") || !msg.get("reference").isJsonObject()) {
            return "";
        }
        String refId = jsonString(msg.getAsJsonObject("reference"), "message", null);
        if (refId == null) {
            return "";
        }
        JsonObject ref = byId.get(refId);
        String who = "Unknown";
        String snippet = "";
        if (ref != null) {
            who = firstString(ref, "nick", "username");
            if (who == null) {
                who = "Unknown";
            }
            snippet = ref.has("content") && !ref.get("content").isJsonNull()
                    ? ref.get("content").getAsString() : "";
            if (snippet.isBlank()) {
                snippet = firstEmbedDescription(ref);
            }
        }
        snippet = snippet.replace("\n", " ").trim();
        if (snippet.length() > 80) {
            snippet = snippet.substring(0, 80) + "…";
        }
        return "<div class=\"reply\"><span class=\"bar\"></span><span class=\"rn\">"
                + escape(who) + "</span><span class=\"rt\">"
                + escape(snippet) + "</span></div>";
    }

    private static String renderEmbeds(JsonObject msg, Map<String, String> names) {
        if (!msg.has("embeds") || !msg.get("embeds").isJsonArray()) {
            return "";
        }
        StringBuilder extra = new StringBuilder();
        for (JsonElement embEl : msg.getAsJsonArray("embeds")) {
            if (!embEl.isJsonObject()) {
                continue;
            }
            JsonObject emb = embEl.getAsJsonObject();
            String desc = jsonString(emb, "description", jsonString(emb, "title", ""));
            if (desc == null || desc.isBlank()) {
                continue;
            }
            String trimmed = desc.trim();
            if ("Transcript Saving".equalsIgnoreCase(trimmed) || "Transcript Saving".equalsIgnoreCase(trimmed)) {
                continue;
            }
            String color = jsonString(emb, "color", "");
            String cls = "";
            if ("#fbfe32".equalsIgnoreCase(color) || "#ffee58".equalsIgnoreCase(color)) {
                cls = " yellow";
            } else if ("#2f3136".equalsIgnoreCase(color)) {
                cls = " gray";
            }
            extra.append("<div class=\"embed").append(cls).append("\">")
                    .append(formatContent(desc, names))
                    .append("</div>");
        }
        return extra.toString();
    }

    private static String renderButtons(JsonObject msg) {
        if (!msg.has("components") || !msg.get("components").isJsonArray()) {
            return "";
        }
        StringBuilder buttons = new StringBuilder();
        for (JsonElement rowEl : msg.getAsJsonArray("components")) {
            if (!rowEl.isJsonObject()) {
                continue;
            }
            JsonObject row = rowEl.getAsJsonObject();
            if (!row.has("components") || !row.get("components").isJsonArray()) {
                continue;
            }
            for (JsonElement btnEl : row.getAsJsonArray("components")) {
                if (!btnEl.isJsonObject()) {
                    continue;
                }
                JsonObject btn = btnEl.getAsJsonObject();
                String label = jsonString(btn, "label", null);
                if (label == null || label.isBlank()) {
                    continue;
                }
                String emoji = "";
                if (btn.has("emoji") && btn.get("emoji").isJsonObject()) {
                    emoji = jsonString(btn.getAsJsonObject("emoji"), "name", "");
                }
                buttons.append("<span class=\"btn\">");
                if (emoji != null && !emoji.isBlank()) {
                    buttons.append(escape(emoji)).append(" ");
                }
                buttons.append(escape(label)).append("</span>");
            }
        }
        if (buttons.isEmpty()) {
            return "";
        }
        return "<div class=\"btns\">" + buttons + "</div>";
    }

    private static String firstEmbedDescription(JsonObject msg) {
        if (!msg.has("embeds") || !msg.get("embeds").isJsonArray() || msg.getAsJsonArray("embeds").isEmpty()) {
            return "";
        }
        JsonElement first = msg.getAsJsonArray("embeds").get(0);
        if (!first.isJsonObject()) {
            return "";
        }
        String desc = jsonString(first.getAsJsonObject(), "description", "");
        return desc == null ? "" : desc;
    }

    private static void absorbDiscordData(JsonObject msg, Map<String, String> names) {
        if (!msg.has("discordData") || !msg.get("discordData").isJsonObject()) {
            return;
        }
        for (var e : msg.getAsJsonObject("discordData").entrySet()) {
            if (!e.getValue().isJsonObject()) {
                continue;
            }
            JsonObject data = e.getValue().getAsJsonObject();
            String n = jsonString(data, "nick", jsonString(data, "name", null));
            if (n != null) {
                names.putIfAbsent(e.getKey(), n);
            }
        }
    }

    private static String formatContent(String content, Map<String, String> names) {
        if (content == null || content.isBlank()) {
            return "";
        }
        List<String> mentionHtml = new ArrayList<>();
        Matcher m = MENTION.matcher(content);
        StringBuilder withTokens = new StringBuilder();
        while (m.find()) {
            String id = m.group(2);
            String label = "@" + names.getOrDefault(id, id);
            mentionHtml.add("<span class=\"mention\">" + escape(label) + "</span>");
            m.appendReplacement(withTokens, Matcher.quoteReplacement("%%M" + (mentionHtml.size() - 1) + "%%"));
        }
        m.appendTail(withTokens);

        String s = escape(withTokens.toString());
        s = s.replaceAll("```([\\s\\S]*?)```", "$1");
        s = s.replaceAll("\\*\\*(.+?)\\*\\*", "<strong>$1</strong>");
        s = s.replaceAll("__(.+?)__", "<u>$1</u>");
        s = s.replaceAll("(?m)^##\\s+", "");
        s = s.replaceAll("`([^`]+)`", "<code>$1</code>");
        s = s.replace("\n", "<br/>");
        for (int i = 0; i < mentionHtml.size(); i++) {
            s = s.replace("%%M" + i + "%%", mentionHtml.get(i));
        }
        return s;
    }

    private static long createdMillis(JsonObject msg) {
        if (!msg.has("created") || !msg.get("created").isJsonPrimitive()) {
            return 0;
        }
        try {
            long created = msg.get("created").getAsLong();
            return created < 100_000_000_000L ? created * 1000 : created;
        } catch (Exception e) {
            return 0;
        }
    }

    private static JsonArray decodeArray(String html, Pattern pattern) {
        String decoded = decodeB64Field(html, pattern);
        if (decoded == null) {
            return null;
        }
        try {
            JsonElement el = JsonParser.parseString(decoded);
            return el.isJsonArray() ? el.getAsJsonArray() : null;
        } catch (Exception e) {
            return null;
        }
    }

    private static JsonObject decodeObject(String html, Pattern pattern) {
        String decoded = decodeB64Field(html, pattern);
        if (decoded == null) {
            return null;
        }
        try {
            JsonElement el = JsonParser.parseString(decoded);
            return el.isJsonObject() ? el.getAsJsonObject() : null;
        } catch (Exception e) {
            return null;
        }
    }

    private static String decodeB64Field(String html, Pattern pattern) {
        Matcher m = pattern.matcher(html);
        if (!m.find()) {
            return null;
        }
        String payload = m.group(1).replace("\\n", "").replace("\\r", "").replace(" ", "");
        try {
            byte[] bytes = Base64.getDecoder().decode(payload);
            return new String(bytes, StandardCharsets.UTF_8);
        } catch (Exception e) {
            try {
                byte[] bytes = Base64.getUrlDecoder().decode(payload);
                return new String(bytes, StandardCharsets.UTF_8);
            } catch (Exception ignored) {
                return null;
            }
        }
    }

    private static String avatarUrl(JsonObject msg) {
        String userId = firstString(msg, "user_id", "userid");
        String avatar = jsonString(msg, "avatar", null);
        if (userId == null || avatar == null || avatar.isBlank()) {
            return null;
        }
        String ext = avatar.startsWith("a_") ? "gif" : "png";
        return "https://cdn.discordapp.com/avatars/" + userId + "/" + avatar + "." + ext;
    }

    private static String firstString(JsonObject o, String... keys) {
        for (String key : keys) {
            String v = jsonString(o, key, null);
            if (v != null && !v.isBlank()) {
                return v;
            }
        }
        return null;
    }

    private static String jsonString(JsonObject o, String key, String fallback) {
        if (o == null || key == null || !o.has(key) || o.get(key).isJsonNull()) {
            return fallback;
        }
        try {
            String v = o.get(key).getAsString();
            return v == null || v.isBlank() ? fallback : v;
        } catch (Exception e) {
            return fallback;
        }
    }

    private static String escape(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }
}
