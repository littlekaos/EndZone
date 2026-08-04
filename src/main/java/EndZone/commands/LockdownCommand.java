package EndZone.commands;

import EndZone.EndZone;
import EndZone.utils.PermissionUtils;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.entities.channel.concrete.Category;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.middleman.GuildChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;

import java.util.ArrayList;
import java.util.List;

public class LockdownCommand implements Command {

    private final EndZone bot;

    public LockdownCommand(EndZone bot) {
        this.bot = bot;
    }

    @Override
    public List<CommandData> getCommandDataList() {
        return List.of(
                Commands.slash("lockdown", "Manage server-wide or targeted lockdown")
                        .addSubcommands(
                                new SubcommandData("begin", "Lock channels or categories")
                                        .addOption(OptionType.CHANNEL, "channel", "Specific channel to lock")
                                        .addOption(OptionType.CHANNEL, "category", "Specific category to lock (all channels inside)"),
                                new SubcommandData("end", "Unlock channels or categories")
                                        .addOption(OptionType.CHANNEL, "channel", "Specific channel to unlock")
                                        .addOption(OptionType.CHANNEL, "category", "Specific category to unlock (all channels inside)")
                        )
        );
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        if (!PermissionUtils.isAlphaBetaOrHigher(event.getMember(), bot.getConfig())) {
            event.reply("❌ You do not have permission to use this command.").setEphemeral(true).queue();
            return;
        }

        String subcommand = event.getSubcommandName();
        if (subcommand == null) return;

        event.deferReply().setEphemeral(true).queue();

        boolean lock = subcommand.equals("begin");
        OptionMapping channelOpt = event.getOption("channel");
        OptionMapping categoryOpt = event.getOption("category");

        List<TextChannel> targets = new ArrayList<>();

        if (categoryOpt != null) {
            GuildChannel channel = categoryOpt.getAsChannel();
            if (channel.getType() == ChannelType.CATEGORY) {
                targets.addAll(((Category) channel).getTextChannels());
            } else {
                event.getHook().editOriginal("❌ The selected category is not a valid category.").queue();
                return;
            }
        }

        if (channelOpt != null) {
            GuildChannel channel = channelOpt.getAsChannel();
            if (channel.getType() == ChannelType.TEXT) {
                targets.add((TextChannel) channel);
            } else {
                event.getHook().editOriginal("❌ The selected channel is not a text channel.").queue();
                return;
            }
        }

        // Default to current channel if nothing specified
        if (categoryOpt == null && channelOpt == null) {
            targets.add(event.getChannel().asTextChannel());
        }

        Role everyoneRole = event.getGuild().getPublicRole();
        Role communityMemberRole = event.getGuild().getRoleById("790162629645303829");
        
        List<Role> rolesToLock = new ArrayList<>();
        rolesToLock.add(everyoneRole);
        if (communityMemberRole != null) {
            rolesToLock.add(communityMemberRole);
        }

        int successCount = 0;
        int failCount = 0;

        for (TextChannel channel : targets) {
            try {
                for (Role role : rolesToLock) {
                    if (lock) {
                        channel.upsertPermissionOverride(role)
                                .grant(Permission.VIEW_CHANNEL)
                                .deny(Permission.MESSAGE_SEND)
                                .queue();
                    } else {
                        channel.upsertPermissionOverride(role)
                                .clear(Permission.MESSAGE_SEND)
                                .queue();
                    }
                }
                successCount++;
            } catch (Exception e) {
                failCount++;
            }
        }

        String action = lock ? "Locked" : "Unlocked";
        String emoji = lock ? "🔒" : "🔓";
        
        String targetDesc;
        if (categoryOpt != null && channelOpt != null) targetDesc = "category and channel";
        else if (categoryOpt != null) targetDesc = "category";
        else if (channelOpt != null) targetDesc = "channel";
        else targetDesc = "current channel";

        if (failCount == 0) {
            event.getHook().editOriginal(emoji + " **Lockdown " + subcommand + " successful** for " + targetDesc + ". " + successCount + " channels updated.").queue();
        } else {
            event.getHook().editOriginal(emoji + " **Lockdown " + subcommand + " completed with some issues.**\nSuccess: " + successCount + "\nFailed: " + failCount).queue();
        }
    }
}
