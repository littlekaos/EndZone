package EndZone.listeners;

import EndZone.embeds.EndZoneEmbed;
import EndZone.handlers.EndZoneApprovalHandler;
import EndZone.forms.EndZoneForm;
import EndZone.schedulers.SchedulerManager;
import EndZone.utils.PermissionUtils;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

public class EndZoneListener extends ListenerAdapter {

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        // Handled by CommandEventListener
    }

    @Override
    public void onButtonInteraction(ButtonInteractionEvent event) {
        if (PermissionUtils.isBlacklisted(event.getUser())) {
            event.reply("❌ You are blacklisted from using this bot.").setEphemeral(true).queue();
            return;
        }

        String componentId = event.getComponentId();

        if (componentId.equals("endzone_button")) {
            EndZoneForm.startApplication(event.getUser(), event);
        } else if (componentId.equals("dm_ready_button")) {
            event.deferEdit().queue();
            EndZoneForm.handleDMReady(event.getUser());
        } else if (componentId.startsWith("q") && (componentId.contains("_yes") || componentId.contains("_no"))) {
            EndZoneForm.handleQuestionAnswer(event);
        } else if (componentId.endsWith("_answer_button")) {
            EndZoneForm.handleModalButton(event);
        } else if (componentId.equals("q_continue")) {
            EndZoneForm.handleNextButton(event);
        } else if (componentId.startsWith("approve_")) {
            EndZoneApprovalHandler.handleApplicationApproval(event);
        } else if (componentId.startsWith("deny_")) {
            EndZoneApprovalHandler.handleApplicationDenial(event);
        }
    }

    @Override
    public void onStringSelectInteraction(StringSelectInteractionEvent event) {
        if (PermissionUtils.isBlacklisted(event.getUser())) {
            event.reply("❌ You are blacklisted from using this bot.").setEphemeral(true).queue();
            return;
        }

        String componentId = event.getComponentId();

        if (componentId.startsWith("q") && componentId.contains("_select")) {
            EndZoneForm.handleSelectAnswer(event);
        }
    }

    @Override
    public void onModalInteraction(ModalInteractionEvent event) {
        if (PermissionUtils.isBlacklisted(event.getUser())) {
            event.reply("❌ You are blacklisted from using this bot.").setEphemeral(true).queue();
            return;
        }

        String modalId = event.getModalId();

        if (modalId.startsWith("q") && modalId.endsWith("_modal")) {
            EndZoneForm.handleModalResponse(event);
        }
    }
}
