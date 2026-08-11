package EndZone.commands;

import EndZone.models.ModmailSession;
import EndZone.services.ModmailService;
import EndZone.services.ServiceManager;
import EndZone.utils.EmbedUtils;
import EndZone.utils.PermissionUtils;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class CloseCommand implements Command {
    private static final Pattern DURATION = Pattern.compile("^(\\d+)(s|m|h|d)$", Pattern.CASE_INSENSITIVE);

    @Override
    public List<CommandData> getCommandDataList() {
        return List.of(Commands.slash("close", "Close this modmail ticket now, on a timer, or cancel a timed close")
                .addOption(OptionType.STRING, "time", "Delay before close, e.g. 2d, 1h, 30m (omit to close now)", false)
                .addOption(OptionType.BOOLEAN, "cancel", "Cancel a scheduled timed close", false));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        if (event.getGuild() == null) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed("This command can only be used in a server.")).setEphemeral(true).queue();
            return;
        }

        ModmailService modmail = ServiceManager.getModmailService();
        if (modmail == null) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed("Modmail is not ready.")).setEphemeral(true).queue();
            return;
        }

        ModmailSession session = modmail.findByChannel(event.getChannel().getId());
        if (session == null || !session.isOpen()) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed("This channel is not an open modmail thread or ticket.")).setEphemeral(true).queue();
            return;
        }

        boolean isOpener = event.getUser().getId().equals(session.getUserId());
        boolean isStaff = PermissionUtils.isModerator(event.getMember(), ServiceManager.getConfig())
                || PermissionUtils.isAlphaBetaOrHigher(event.getMember(), ServiceManager.getConfig())
                || PermissionUtils.isJury(event.getMember(), ServiceManager.getConfig());

        if (!isOpener && !isStaff) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed("You don't have permission to close this.")).setEphemeral(true).queue();
            return;
        }

        boolean cancel = event.getOption("cancel") != null && event.getOption("cancel").getAsBoolean();
        if (cancel) {
            if (modmail.cancelScheduledClose(session.getChannelId())) {
                event.reply("Scheduled close has been cancelled.").queue();
            } else {
                event.replyEmbeds(EmbedUtils.createErrorEmbed("There is no scheduled close for this ticket.")).setEphemeral(true).queue();
            }
            return;
        }

        String timeRaw = event.getOption("time") != null ? event.getOption("time").getAsString().trim() : null;

        if (timeRaw != null && !timeRaw.isBlank()) {
            ParsedDuration parsed = parseDuration(timeRaw);
            if (parsed == null) {
                event.replyEmbeds(EmbedUtils.createErrorEmbed(
                        "Invalid time. Use a number plus `s`, `m`, `h`, or `d` — e.g. `30m`, `2h`, `2d`.")).setEphemeral(true).queue();
                return;
            }

            boolean scheduled = modmail.scheduleClose(session, parsed.millis, null, event.getUser(), parsed.display);
            if (!scheduled) {
                event.replyEmbeds(EmbedUtils.createErrorEmbed("Could not schedule close (ticket may already be closing).")).setEphemeral(true).queue();
                return;
            }

            event.reply("Thread is now scheduled to be closed in " + parsed.display
                    + ". Use `/close cancel:True` to cancel.").queue();
            return;
        }

        event.reply("Closing...").setEphemeral(true).queue();
        modmail.cancelScheduledClose(session.getChannelId());
        modmail.closeSession(session, null, event.getUser());
    }

    private ParsedDuration parseDuration(String input) {
        Matcher matcher = DURATION.matcher(input.trim());
        if (!matcher.matches()) return null;

        long amount = Long.parseLong(matcher.group(1));
        if (amount <= 0) return null;

        String unit = matcher.group(2).toLowerCase();
        long millis;
        String display;
        switch (unit) {
            case "s" -> {
                millis = TimeUnit.SECONDS.toMillis(amount);
                display = amount + (amount == 1 ? " second" : " seconds");
            }
            case "m" -> {
                millis = TimeUnit.MINUTES.toMillis(amount);
                display = amount + (amount == 1 ? " minute" : " minutes");
            }
            case "h" -> {
                millis = TimeUnit.HOURS.toMillis(amount);
                display = amount + (amount == 1 ? " hour" : " hours");
            }
            case "d" -> {
                millis = TimeUnit.DAYS.toMillis(amount);
                display = amount + (amount == 1 ? " day" : " days");
            }
            default -> {
                return null;
            }
        }

        // Cap at 30 days
        if (millis > TimeUnit.DAYS.toMillis(30)) {
            return null;
        }
        return new ParsedDuration(millis, display);
    }

    private record ParsedDuration(long millis, String display) {}
}
