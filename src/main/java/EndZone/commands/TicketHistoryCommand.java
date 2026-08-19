package EndZone.commands;

import EndZone.config.BotConfig;
import EndZone.models.ModmailLog;
import EndZone.services.ModmailService;
import EndZone.services.ServiceManager;
import EndZone.utils.EmbedUtils;
import EndZone.utils.PermissionUtils;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;

import java.awt.Color;
import java.time.Instant;
import java.util.List;

public class TicketHistoryCommand implements Command {

    @Override
    public List<CommandData> getCommandDataList() {
        return List.of(Commands.slash("logs", "List all past modmail tickets for a user")
                .addOption(OptionType.USER, "user", "The user whose ticket history to view", true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        if (event.getGuild() == null || event.getMember() == null) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed("This command can only be used in a server.")).setEphemeral(true).queue();
            return;
        }

        if (!canView(event.getMember())) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed("You don't have permission to view ticket history.")).setEphemeral(true).queue();
            return;
        }

        ModmailService modmail = ServiceManager.getModmailService();
        if (modmail == null) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed("Modmail is not ready.")).setEphemeral(true).queue();
            return;
        }

        User target = event.getOption("user").getAsUser();
        List<ModmailLog> logs = modmail.findLogsByUserId(target.getId());
        if (logs.isEmpty()) {
            event.replyEmbeds(EmbedUtils.createInfoEmbed("No past tickets found for **" + target.getName() + "** (`" + target.getId() + "`)."))
                    .setEphemeral(true).queue();
            return;
        }

        EmbedBuilder embed = new EmbedBuilder()
                .setTitle("Ticket history — " + target.getName())
                .setColor(new Color(0x5865F2))
                .setFooter(logs.size() + " ticket" + (logs.size() == 1 ? "" : "s") + " · ID " + target.getId())
                .setTimestamp(Instant.now());

        StringBuilder desc = new StringBuilder();
        int shown = 0;
        for (ModmailLog log : logs) {
            String category = log.getCategory() != null && !log.getCategory().isBlank()
                    ? log.getCategory()
                    : "Ticket";
            String closer = log.getClosedByName() != null ? log.getClosedByName() : "staff";
            String urls = modmail.buildLogUrlBlock(log);
            String line = "<t:" + (log.getClosedAt() / 1000) + ":d> — **" + category + "** · closed by "
                    + closer + "\n" + urls + "\n\n";
            if (desc.length() + line.length() > 3900) {
                break;
            }
            desc.append(line);
            shown++;
        }

        if (shown < logs.size()) {
            desc.append("_…and ").append(logs.size() - shown).append(" more (showing newest)._");
        }

        embed.setDescription(desc.toString().trim());
        event.replyEmbeds(embed.build()).setEphemeral(true).queue();
    }

    private boolean canView(Member member) {
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
