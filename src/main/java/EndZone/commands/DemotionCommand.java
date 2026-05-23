package EndZone.commands;

import EndZone.EndZone;
import EndZone.config.BotConfig;
import EndZone.services.DemotionService;
import EndZone.services.ServiceManager;
import EndZone.utils.PermissionUtils;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class DemotionCommand implements Command {

    private final EndZone bot;
    private final DemotionService demotionService;

    public DemotionCommand(EndZone bot) {
        this.bot = bot;
        this.demotionService = ServiceManager.getDemotionService();
    }

    @Override
    public List<CommandData> getCommandDataList() {
        return List.of(
                Commands.slash("initdemotionlist", "Initialize the demotion list message (Alpha Beta+)"),
                Commands.slash("adddemotion", "Add a single user to demotion list (Alpha Beta+)")
                        .addOption(OptionType.USER, "user", "User to add to demotion list", true)
                        .addOption(OptionType.STRING, "type", "temp or perm", true),
                Commands.slash("bulkadddemotions", "Bulk add users to demotion list (Alpha Beta+)")
                        .addOption(OptionType.STRING, "userids", "Comma-separated user IDs", true)
                        .addOption(OptionType.STRING, "type", "temp or perm", true),
                Commands.slash("removedemotion", "Remove a user from the demotion list (Alpha Beta+)")
                        .addOption(OptionType.USER, "user", "User to remove from demotion list", true),
                Commands.slash("bulkremovedemotion", "Bulk remove users from demotion list (Alpha Beta+)")
                        .addOption(OptionType.STRING, "userids", "Comma-separated user IDs", true),
                Commands.slash("updatedemotionlist", "Manually update the demotion list message (Alpha Beta+)"),
                Commands.slash("bulkupdatedemotions", "Add days to all current temporary demotions (Alpha Beta+)")
                        .addOption(OptionType.INTEGER, "days", "Number of days to add", true)
        );
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        if (!PermissionUtils.isAlphaBetaOrHigher(event.getMember(), ServiceManager.getConfig())) {
            event.reply("❌ You do not have permission to manage demotions. Only Alpha Beta+ can use this command.").setEphemeral(true).queue();
            return;
        }

        String commandName = event.getName();

        switch (commandName) {
            case "initdemotionlist" -> handleInit(event);
            case "adddemotion" -> handleAdd(event);
            case "bulkadddemotions" -> handleBulkAdd(event);
            case "removedemotion" -> handleRemove(event);
            case "bulkremovedemotion" -> handleBulkRemove(event);
            case "updatedemotionlist" -> handleUpdate(event);
            case "bulkupdatedemotions" -> handleBulkUpdate(event);
        }
    }

    private void handleInit(SlashCommandInteractionEvent event) {
        event.deferReply(true).queue();
        TextChannel channel = event.getChannel().asTextChannel();
        
        List<MessageEmbed> pages = demotionService.buildPages();
        net.dv8tion.jda.api.interactions.components.ActionRow actionRow = demotionService.buildActionRow(1, pages.size());

        if (actionRow != null) {
            channel.sendMessageEmbeds(pages.get(0))
                    .setComponents(actionRow)
                    .queue(message -> {
                        ServiceManager.getStrikeService().getDatabase().setDemotionListMessageId(message.getId());
                        event.getHook().editOriginal("✅ Demotion list initialized! Message ID: " + message.getId()).queue();
                    });
        } else {
            channel.sendMessageEmbeds(pages.get(0))
                    .queue(message -> {
                        ServiceManager.getStrikeService().getDatabase().setDemotionListMessageId(message.getId());
                        event.getHook().editOriginal("✅ Demotion list initialized! Message ID: " + message.getId()).queue();
                    });
        }
    }

    private void handleAdd(SlashCommandInteractionEvent event) {
        event.deferReply(true).queue();
        User user = Objects.requireNonNull(event.getOption("user")).getAsUser();
        String type = Objects.requireNonNull(event.getOption("type")).getAsString().toLowerCase();

        if (type.equals("temp")) {
            event.getGuild().retrieveMember(user).queue(member -> {
                if (!PermissionUtils.canModerate(event.getMember(), member)) {
                    event.getHook().editOriginal("❌ You cannot demote this user due to role hierarchy.").queue();
                    return;
                }

                List<String> removedRoles = removeStaffRoles(member);
                Instant restoreDate = Instant.now().plus(BotConfig.TEMP_DEMOTION_DAYS, ChronoUnit.DAYS);
                demotionService.addTemporaryDemotion(user.getId(), removedRoles, restoreDate);
                demotionService.updateDemotionListMessage(event.getJDA());

                event.getHook().editOriginal("✅ Added " + user.getAsMention() + " to temporary demotions and removed their staff roles.").queue();
            }, error -> {
                // If member not in guild, still allow temp demotion tracking
                demotionService.addTemporaryDemotion(user.getId(), new ArrayList<>(), Instant.now().plus(BotConfig.TEMP_DEMOTION_DAYS, ChronoUnit.DAYS));
                demotionService.updateDemotionListMessage(event.getJDA());
                event.getHook().editOriginal("✅ Added " + user.getAsMention() + " (not in server) to temporary demotions.").queue();
            });
        } else if (type.equals("perm")) {
            event.getGuild().retrieveMember(user).queue(member -> {
                if (!PermissionUtils.canModerate(event.getMember(), member)) {
                    event.getHook().editOriginal("❌ You cannot demote this user due to role hierarchy.").queue();
                    return;
                }
                
                removeStaffRoles(member);
                demotionService.addPermanentDemotion(user.getId());
                demotionService.updateDemotionListMessage(event.getJDA());
                event.getHook().editOriginal("✅ Added " + user.getAsMention() + " to permanent demotions and removed their staff roles.").queue();
            }, error -> {
                // If member not in guild, still allow perm demotion (blacklist)
                demotionService.addPermanentDemotion(user.getId());
                demotionService.updateDemotionListMessage(event.getJDA());
                event.getHook().editOriginal("✅ Added " + user.getAsMention() + " to permanent demotions.").queue();
            });
        } else {
            event.getHook().editOriginal("❌ Invalid type. Use 'temp' or 'perm'.").queue();
        }
    }

    private List<String> removeStaffRoles(net.dv8tion.jda.api.entities.Member member) {
        List<String> removedRoleIds = new ArrayList<>();
        for (Role role : member.getRoles()) {
            if (BotConfig.isStaffOrModRole(role.getId())) {
                removedRoleIds.add(role.getId());
                member.getGuild().removeRoleFromMember(member, role).queue();
            }
        }
        return removedRoleIds;
    }

    private void handleBulkAdd(SlashCommandInteractionEvent event) {
        event.deferReply(true).queue();
        String userIds = Objects.requireNonNull(event.getOption("userids")).getAsString();
        String type = Objects.requireNonNull(event.getOption("type")).getAsString().toLowerCase();

        if (!type.equals("temp") && !type.equals("perm")) {
            event.getHook().editOriginal("❌ Invalid type. Use 'temp' or 'perm'.").queue();
            return;
        }

        String[] ids = userIds.split(",");
        for (String id : ids) {
            id = id.trim();
            final String finalId = id;
            if (type.equals("temp")) {
                Instant restoreDate = Instant.now().plus(BotConfig.TEMP_DEMOTION_DAYS, ChronoUnit.DAYS);
                
                event.getGuild().retrieveMemberById(finalId).queue(
                    member -> {
                        List<String> removedRoles = removeStaffRoles(member);
                        demotionService.addTemporaryDemotion(finalId, removedRoles, restoreDate);
                    },
                    error -> demotionService.addTemporaryDemotion(finalId, new ArrayList<>(), restoreDate)
                );
            } else {
                event.getGuild().retrieveMemberById(finalId).queue(
                    this::removeStaffRoles,
                    error -> {} // Ignore if not in guild
                );
                demotionService.addPermanentDemotion(finalId);
            }
        }
        demotionService.updateDemotionListMessage(event.getJDA());
        event.getHook().editOriginal("✅ Bulk added " + ids.length + " users to " + type + " demotions.").queue();
    }

    private void handleRemove(SlashCommandInteractionEvent event) {
        event.deferReply(true).queue();
        User user = Objects.requireNonNull(event.getOption("user")).getAsUser();
        demotionService.removeDemotion(user.getId(), event.getJDA());
        event.getHook().editOriginal("✅ Removed " + user.getAsMention() + " from demotions.").queue();
    }

    private void handleBulkRemove(SlashCommandInteractionEvent event) {
        event.deferReply(true).queue();
        String userIds = Objects.requireNonNull(event.getOption("userids")).getAsString();
        String[] ids = userIds.split(",");
        for (String id : ids) {
            id = id.trim();
            demotionService.removeDemotion(id, event.getJDA());
        }
        event.getHook().editOriginal("✅ Bulk removed " + ids.length + " users from demotions.").queue();
    }

    private void handleUpdate(SlashCommandInteractionEvent event) {
        event.deferReply(true).queue();
        demotionService.updateDemotionListMessage(event.getJDA());
        event.getHook().editOriginal("✅ Demotion list update triggered.").queue();
    }

    private void handleBulkUpdate(SlashCommandInteractionEvent event) {
        event.deferReply(true).queue();
        int days = Objects.requireNonNull(event.getOption("days")).getAsInt();
        demotionService.updateExistingDemotions(days);
        demotionService.updateDemotionListMessage(event.getJDA());
        event.getHook().editOriginal("✅ Added " + days + " days to all current temporary demotions.").queue();
    }
}
