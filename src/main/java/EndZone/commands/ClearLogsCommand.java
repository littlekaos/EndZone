package EndZone.commands;

import EndZone.config.BotConfig;
import EndZone.services.ModmailService;
import EndZone.services.ServiceManager;
import EndZone.utils.EmbedUtils;
import EndZone.utils.PermissionUtils;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;

import java.util.List;

public class ClearLogsCommand implements Command {

    @Override
    public List<CommandData> getCommandDataList() {
        return List.of(Commands.slash("clear-logs", "Delete stored modmail ticket history for a user")
                .addOption(OptionType.USER, "user", "User whose log history to clear (defaults to you)", false));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        if (event.getGuild() == null || event.getMember() == null) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed("This command can only be used in a server.")).setEphemeral(true).queue();
            return;
        }

        if (!canClear(event.getMember())) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed("You don't have permission to clear ticket logs.")).setEphemeral(true).queue();
            return;
        }

        ModmailService modmail = ServiceManager.getModmailService();
        if (modmail == null) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed("Modmail is not ready.")).setEphemeral(true).queue();
            return;
        }

        User target = event.getOption("user") != null
                ? event.getOption("user").getAsUser()
                : event.getUser();

        int deleted = modmail.deleteLogsByUserId(target.getId());
        if (deleted == 0) {
            event.replyEmbeds(EmbedUtils.createInfoEmbed(
                    "No ticket logs found for **" + target.getName() + "**."
            )).setEphemeral(true).queue();
            return;
        }

        event.replyEmbeds(EmbedUtils.createSuccessEmbed(
                "Deleted **" + deleted + "** ticket log"
                        + (deleted == 1 ? "" : "s")
                        + " for **" + target.getName() + "**."
        )).setEphemeral(true).queue();
    }

    private boolean canClear(Member member) {
        if (PermissionUtils.isBotOwner(member)) {
            return true;
        }
        boolean endzoneStaff = member.getRoles().stream().anyMatch(role ->
                role.getId().equals(BotConfig.ALPHAS_ROLE_ID)
                        || role.getId().equals(BotConfig.ALPHA_BETAS_ROLE_ID)
        );
        if (endzoneStaff) return true;

        return member.getRoles().stream().anyMatch(role ->
                role.getId().equals(BotConfig.COURT_THE_JUDGE_EZ_ROLE_ID)
                        || role.getId().equals(BotConfig.COURT_THE_DISTRICT_ATTORNIES_EZ_ROLE_ID)
                        || role.getId().equals(BotConfig.COURT_THE_JUDGE_ZRE_ROLE_ID)
                        || role.getId().equals(BotConfig.COURT_SPECIAL_PEOPLE_ROLE_ID)
                        || role.getId().equals(BotConfig.COURT_THE_JURY_EZ_ROLE_ID)
                        || role.getId().equals(BotConfig.COURT_THE_JURY_ZRE_ROLE_ID)
        );
    }
}
