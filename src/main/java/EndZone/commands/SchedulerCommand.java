package EndZone.commands;

import EndZone.EndZone;
import EndZone.schedulers.SchedulerManager;
import EndZone.services.ServiceManager;
import EndZone.utils.PermissionUtils;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;

import java.util.List;

public class SchedulerCommand implements Command {

    private final EndZone bot;

    public SchedulerCommand(EndZone bot) {
        this.bot = bot;
    }

    @Override
    public List<CommandData> getCommandDataList() {
        return List.of(
                Commands.slash("scheduler", "Enable or disable scheduled bot events.")
                        .addOptions(
                                new OptionData(OptionType.STRING, "event", "The scheduled event to toggle", true)
                                        .addChoice("Event Countdown", "countdown")
                                        .addChoice("Staff Announcements", "staff_announcements")
                                        .addChoice("Weekly Announcement", "weekly_announcement")
                                        .addChoice("Monday Announcement", "monday_announcement")
                                        .addChoice("All Schedulers", "all"),
                                new OptionData(OptionType.BOOLEAN, "enabled", "Whether the event should be enabled", true)
                        )
        );
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        if (!PermissionUtils.isAlphaBetaOrHigher(event.getMember(), bot.getConfig())) {
            event.reply("❌ You do not have permission to use this command.").setEphemeral(true).queue();
            return;
        }

        String eventName = event.getOption("event").getAsString();
        boolean enabled = event.getOption("enabled").getAsBoolean();
        String status = enabled ? "enabled" : "disabled";
        String displayName;

        switch (eventName) {
            case "all":
                ServiceManager.getDataService().setEventCountdownEnabled(enabled);
                ServiceManager.getDataService().setStaffAnnouncementsEnabled(enabled);
                ServiceManager.getDataService().setWeeklyAnnouncementEnabled(enabled);
                ServiceManager.getDataService().setMondayAnnouncementEnabled(enabled);
                displayName = "All scheduled bot events";
                if (!enabled) {
                    SchedulerManager.deleteScheduledEvents(event.getJDA());
                } else {
                    SchedulerManager.recreateMainEvent(event.getJDA());
                }
                break;
            case "countdown":
                ServiceManager.getDataService().setEventCountdownEnabled(enabled);
                displayName = "Automatic weekly event countdown ping";
                break;
            case "staff_announcements":
                ServiceManager.getDataService().setStaffAnnouncementsEnabled(enabled);
                displayName = "Daily staff announcements";
                break;
            case "weekly_announcement":
                ServiceManager.getDataService().setWeeklyAnnouncementEnabled(enabled);
                displayName = "Weekly event announcement";
                if (!enabled) {
                    SchedulerManager.deleteScheduledEvents(event.getJDA());
                } else {
                    SchedulerManager.recreateMainEvent(event.getJDA());
                }
                break;
            case "monday_announcement":
                ServiceManager.getDataService().setMondayAnnouncementEnabled(enabled);
                displayName = "Monday event roster announcement";
                if (!enabled) {
                    SchedulerManager.deleteScheduledEvents(event.getJDA());
                } else {
                    SchedulerManager.recreateMainEvent(event.getJDA());
                }
                break;
            default:
                event.reply("❌ Invalid event type.").setEphemeral(true).queue();
                return;
        }

        event.reply("✅ **" + displayName + "** has been **" + status + "**.").setEphemeral(true).queue();
    }
}
