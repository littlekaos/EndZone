package EndZone.events;

import EndZone.EndZone;
import EndZone.services.ServiceManager;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.events.guild.GuildJoinEvent;
import net.dv8tion.jda.api.events.guild.member.GuildMemberJoinEvent;
import net.dv8tion.jda.api.events.session.ReadyEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

public class GuildEventListener extends ListenerAdapter {
    private final EndZone bot;

    public GuildEventListener(EndZone bot) {
        this.bot = bot;
    }

    @Override
    public void onReady(ReadyEvent event) {
        System.out.println("Bot is ready! Connected to " + event.getGuildTotalCount() + " guilds:");
        for (Guild guild : event.getJDA().getGuilds()) {
            System.out.println(" - " + guild.getName() + " (ID: " + guild.getId() + ")");
        }
    }

    @Override
    public void onGuildJoin(GuildJoinEvent event) {
        System.out.println("Joined new guild: " + event.getGuild().getName());
        CommandEventListener.registerCommands(event.getJDA(), bot.getConfig(), bot);
    }

    @Override
    public void onGuildMemberJoin(GuildMemberJoinEvent event) {
        System.out.println("New member joined: " + event.getUser().getName());
        ServiceManager.getDataService().cacheUser(event.getUser());

        // Check for active mute
        if (ServiceManager.getDataService().isMuted(event.getGuild().getId(), event.getUser().getId())) {
            String muteRoleId = ServiceManager.getDataService().getMuteRoleId(event.getGuild().getId());
            if (muteRoleId != null && !muteRoleId.isEmpty()) {
                net.dv8tion.jda.api.entities.Role muteRole = event.getGuild().getRoleById(muteRoleId);
                if (muteRole != null) {
                    event.getGuild().addRoleToMember(event.getMember(), muteRole)
                        .reason("Mute-on-rejoin: User has an active mute in the database.")
                        .queue(
                            success -> System.out.println("Re-applied mute to " + event.getUser().getName()),
                            error -> System.err.println("Failed to re-apply mute to " + event.getUser().getName() + ": " + error.getMessage())
                        );
                }
            }
        }

        sendWelcomeMessage(event.getGuild(), event.getMember());
    }

    private void sendWelcomeMessage(Guild guild, Member member) {
        String welcomeChannelId = ServiceManager.getConfig().getWelcomeChannelId();
        TextChannel welcomeChannel = guild.getTextChannelById(welcomeChannelId);

        if (welcomeChannel != null) {
            String welcomeMessage = "Welcome to the server, " + member.getAsMention() + "! 👋";
            welcomeChannel.sendMessage(welcomeMessage)
                    .queue(message -> {
                        message.addReaction(Emoji.fromUnicode("👋")).queue();
                    });
        } else {
            System.out.println("Welcome channel not found with ID: " + welcomeChannelId);
        }
    }
}
