package EndZone.events;

import EndZone.services.EventsSetupManager;
import EndZone.services.VoiceChannelManager;
import net.dv8tion.jda.api.entities.channel.concrete.VoiceChannel;
import net.dv8tion.jda.api.events.guild.voice.GuildVoiceUpdateEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class VoiceEventListener extends ListenerAdapter {
    private final VoiceChannelManager channelManager;
    private final EventsSetupManager eventsSetupManager;

    public VoiceEventListener(VoiceChannelManager channelManager, EventsSetupManager eventsSetupManager) {
        this.channelManager = channelManager;
        this.eventsSetupManager = eventsSetupManager;
    }

    @Override
    public void onGuildVoiceUpdate(@NotNull GuildVoiceUpdateEvent event) {
        if (event.getMember().getUser().isBot()) return;
        // Handle voice channel join
        if (event.getChannelJoined() != null) {
            handleVoiceJoin(event);
        }

        // Handle voice channel leave
        if (event.getChannelLeft() != null) {
            handleVoiceLeave(event);
        }
    }

    private void handleVoiceJoin(GuildVoiceUpdateEvent event) {
        if (!(event.getChannelJoined() instanceof VoiceChannel joinedChannel)) return;
        
        String channelName = joinedChannel.getName();
        String channelId = joinedChannel.getId();
        String guildId = event.getGuild().getId();

        // Check if server is configured
        if (!eventsSetupManager.hasServerSetup(guildId)) {
            return;
        }

        // Check if it's a managed channel
        List<String> managedIds = eventsSetupManager.getManagedVoiceChannels(guildId);
        if (!managedIds.contains(channelId)) {
            return;
        }

        // Log voice activity to database
        channelManager.getVoiceService().logUserJoinVoice(joinedChannel, event.getMember());

        if (channelManager.isCreateVcChannel(channelName)) {
            channelManager.createAutomaticVoiceChannel(event.getMember(), channelName);
        }
    }

    private void handleVoiceLeave(GuildVoiceUpdateEvent event) {
        if (!(event.getChannelLeft() instanceof VoiceChannel leftChannel)) return;

        // Log voice activity to database
        channelManager.getVoiceService().logUserLeaveVoice(leftChannel, event.getMember());

        // Only delete auto-created channels when empty
        if (channelManager.isAutoCreatedChannel(leftChannel.getIdLong())) {
            channelManager.deleteEmptyVoiceChannel(leftChannel);
        }
    }
}
