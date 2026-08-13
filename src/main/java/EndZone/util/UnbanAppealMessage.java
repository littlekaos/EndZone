package EndZone.util;

import EndZone.config.BotConfig;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;

import java.util.Objects;

/** Shared CourtZone unban-ticket instructions (Ticket Tool + modmail Unban Request). */
public final class UnbanAppealMessage {
    private UnbanAppealMessage() {}

    public static String build(String userMention, boolean isEndzone) {
        String judgeRoleId;
        String juryRoleId;
        String extraRoleId = null;

        if (isEndzone) {
            judgeRoleId = BotConfig.COURT_THE_JUDGE_EZ_ROLE_ID;
            juryRoleId = BotConfig.COURT_THE_DISTRICT_ATTORNIES_EZ_ROLE_ID;
            extraRoleId = BotConfig.COURT_THE_JURY_EZ_ROLE_ID;
        } else {
            judgeRoleId = BotConfig.COURT_THE_JUDGE_ZRE_ROLE_ID;
            juryRoleId = BotConfig.COURT_THE_JURY_ZRE_ROLE_ID;
        }

        StringBuilder greeting = new StringBuilder("Hello ");
        greeting.append(Objects.requireNonNullElse(userMention, "there"));
        greeting.append("! A <@&").append(judgeRoleId).append("> or a <@&").append(juryRoleId).append(">");

        if (extraRoleId != null) {
            greeting.append(" or a <@&").append(extraRoleId).append(">");
        }

        greeting.append(" will be with you soon!\n\n");

        return greeting + formBody();
    }

    /** DM version — no guild role mentions (they show as @unknown-role in DMs). */
    public static String buildForDm(String userMention) {
        return "Hello " + Objects.requireNonNullElse(userMention, "there")
                + "! Management will be with you soon.\n\n"
                + formBody();
    }

    private static String formBody() {
        return "## **__This is what you need to fill a ticket to get unbanned, anything else may end up in a deny.__**\n\n"
                + "➜ **__Discord Account ID:__** e.g. 435303327450791936\n\n"
                + "**How to get your Discord Account ID on Computer or mobile:**\n"
                + "**PC:** Settings (Gear Icon) > Scroll to Advanced > Enable Developer Mode > Right click on your profile picture and go down and click Copy ID.\n"
                + "**Mobile:** Click your profile in the lower right hand side > Settings (Gear Icon) > Scroll down to Advanced > Enable Developer Mode > Click your profile picture > 3 dots in the right hand side > Click Copy User ID\n\n"
                + "➜ **__Reason For Ban:__**\n\n"
                + "➜ **__Reason For Unban:__**\n\n"
                + "## **__Short answers about your reason for ban and unban WILL be an auto deny.__**";
    }

    public static void send(MessageChannel channel, String userMention, boolean isEndzone) {
        channel.sendMessage(build(userMention, isEndzone)).queue();
    }
}
