package EndZone.handlers;

import EndZone.services.ServiceManager;
import EndZone.database.DatabaseService;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;

public class EndZoneApprovalHandler {

    public static void handleApplicationApproval(ButtonInteractionEvent event) {
        String userId = event.getComponentId().replace("approve_", "");
        
        event.deferEdit().queue();
        
        ServiceManager.getJda().retrieveUserById(userId).queue(
            user -> {
                event.getGuild().addRoleToMember(user, event.getGuild().getRoleById("790173529273663498")).queue(
                    success -> {
                        DatabaseService.markApplicationSubmitted(userId);
                        event.getHook().editOriginal("")
                                .setEmbeds(updateApplicationEmbed(event.getMessage().getEmbeds().get(0), "approved", event.getUser()))
                                .setComponents()
                                .queue();
                    },
                    error -> event.getHook().editOriginal("Error assigning role.").queue()
                );
            },
            error -> event.getHook().editOriginal("Error retrieving user.").queue()
        );
    }

    public static void handleApplicationDenial(ButtonInteractionEvent event) {
        String userId = event.getComponentId().replace("deny_", "");
        
        event.deferEdit().queue();
        DatabaseService.markApplicationSubmitted(userId);
        
        event.getHook().editOriginal("")
                .setEmbeds(updateApplicationEmbed(event.getMessage().getEmbeds().get(0), "denied", event.getUser()))
                .setComponents()
                .queue();
    }

    private static net.dv8tion.jda.api.entities.MessageEmbed updateApplicationEmbed(net.dv8tion.jda.api.entities.MessageEmbed originalEmbed, String status, net.dv8tion.jda.api.entities.User reviewedBy) {
        String statusMessage = status.equals("approved") ? "✅ **APPROVED**" : "❌ **DENIED**";
        String newDescription = originalEmbed.getDescription() + "\n\n" + statusMessage + "\nReviewed by " + reviewedBy.getName() + " (ID: " + reviewedBy.getId() + ")";
        
        return new EmbedBuilder(originalEmbed)
                .setDescription(newDescription)
                .build();
    }
}
