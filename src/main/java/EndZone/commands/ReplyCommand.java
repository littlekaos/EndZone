package EndZone.commands;

import EndZone.config.BotConfig;
import EndZone.models.ModmailSession;
import EndZone.services.ModmailService;
import EndZone.services.ServiceManager;
import EndZone.utils.EmbedUtils;
import EndZone.utils.PermissionUtils;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;

import java.util.List;

public class ReplyCommand implements Command {

    @Override
    public List<CommandData> getCommandDataList() {
        return List.of(Commands.slash("reply", "Reply to a modmail thread or ticket as staff")
                .addOption(OptionType.STRING, "message", "The message to send", true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        if (event.getGuild() == null || event.getMember() == null) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed("This command can only be used in a server.")).setEphemeral(true).queue();
            return;
        }

        if (!canReply(event.getMember())) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed("You don't have permission to use /reply.")).setEphemeral(true).queue();
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

        String message = event.getOption("message").getAsString();
        event.deferReply(true).queue();
        modmail.staffReply(session, event.getMember(), message,
                () -> event.getHook().editOriginal("Reply sent.").queue(),
                error -> event.getHook().editOriginal("❌ " + error).queue()
        );
    }

    private boolean canReply(Member member) {
        if (PermissionUtils.isBotOwner(member)) {
            return true;
        }
        // EndZone staff
        boolean endzoneStaff = member.getRoles().stream().anyMatch(role ->
                role.getId().equals(BotConfig.ALPHAS_ROLE_ID)
                        || role.getId().equals(BotConfig.ALPHA_BETAS_ROLE_ID)
        );
        if (endzoneStaff) return true;

        // CourtZone staff (DM modmail lives in CourtZone Ticket Zone)
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
