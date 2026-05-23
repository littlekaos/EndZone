package EndZone.commands;

import EndZone.EndZone;
import EndZone.config.BotConfig;
import EndZone.services.ServiceManager;
import EndZone.utils.EmbedUtils;
import EndZone.utils.PermissionUtils;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;

import java.awt.Color;
import java.time.Instant;
import java.util.List;

public class RoleCommand implements Command {
    private final EndZone bot;

    private static final SubcommandData ROLE_ADD = new SubcommandData(
            "add", "Add a role to a user"
    )
            .addOption(OptionType.USER, "user", "The user to add the role to", true)
            .addOption(OptionType.ROLE, "role", "The role to add", true);

    private static final SubcommandData ROLE_REMOVE = new SubcommandData(
            "remove", "Remove a role from a user"
    )
            .addOption(OptionType.USER, "user", "The user to remove the role from", true)
            .addOption(OptionType.ROLE, "role", "The role to remove", true);

    public RoleCommand(EndZone bot) {
        this.bot = bot;
    }

    @Override
    public List<CommandData> getCommandDataList() {
        return List.of(Commands.slash("role", "Role Alpha Beta+")
                .addSubcommands(ROLE_ADD, ROLE_REMOVE));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        boolean hasPermission = hasRoleManagerPermission(event.getMember());

        if (!hasPermission) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                    "You don't have permission to use this command."
            )).setEphemeral(true).queue();
            return;
        }

        String subcommandName = event.getSubcommandName();
        if (subcommandName == null) {
            event.reply("Invalid subcommand!").setEphemeral(true).queue();
            return;
        }

        Guild guild = event.getGuild();
        if (guild == null) {
            event.reply("This command can only be used in a server.").setEphemeral(true).queue();
            return;
        }

        switch (subcommandName) {
            case "add":
                handleRoleAdd(event, guild);
                break;
            case "remove":
                handleRoleRemove(event, guild);
                break;
            default:
                event.reply("Unknown subcommand: " + subcommandName).setEphemeral(true).queue();
        }
    }

    private void handleRoleAdd(SlashCommandInteractionEvent event, Guild guild) {
        User targetUser = event.getOption("user").getAsUser();
        Role targetRole = event.getOption("role").getAsRole();
        Member moderator = event.getMember();

        // Prevent self-assignment of roles
        if (targetUser.getId().equals(moderator.getId())) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                    "You cannot assign roles to yourself."
            )).setEphemeral(true).queue();
            return;
        }

        // Block giving Community Role to anyone
        if (targetRole.getId().equals(BotConfig.MEMBER_ROLE_ID) || targetRole.getName().equalsIgnoreCase("Community Role")) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                    "You cannot give the Community role via the bot."
            )).setEphemeral(true).queue();
            return;
        }

        if (!canManageRole(moderator, targetRole, guild)) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                    "You cannot add this role because it's equal to or higher than your highest role."
            )).setEphemeral(true).queue();
            return;
        }

        event.deferReply().setEphemeral(true).queue();
        
        guild.retrieveMemberById(targetUser.getId()).queue(targetMember -> {
            doRoleManual(event, targetMember, targetRole, "add");
        }, error -> event.getHook().editOriginalEmbeds(EmbedUtils.createErrorEmbed("Could not find that user in this server.")).queue());
    }

    private void handleRoleRemove(SlashCommandInteractionEvent event, Guild guild) {
        User targetUser = event.getOption("user").getAsUser();
        Role targetRole = event.getOption("role").getAsRole();
        Member moderator = event.getMember();

        if (!canManageRole(moderator, targetRole, guild)) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                    "You cannot remove this role because it's equal to or higher than your highest role."
            )).setEphemeral(true).queue();
            return;
        }

        event.deferReply().setEphemeral(true).queue();
        
        guild.retrieveMemberById(targetUser.getId()).queue(targetMember -> {
            doRoleManual(event, targetMember, targetRole, "remove");
        }, error -> event.getHook().editOriginalEmbeds(EmbedUtils.createErrorEmbed("Could not find that user in this server.")).queue());
    }

    private void doRoleManual(SlashCommandInteractionEvent event, Member targetMember, Role targetRole, String action) {
        Guild guild = event.getGuild();
        Member moderator = event.getMember();
        String targetName = targetMember.getUser().getName();
        String roleName = targetRole.getName();
        String roleId = targetRole.getId();
        String targetId = targetMember.getId();

        if (action.equals("add")) {
            // 1. Check for demotion protection
            boolean isPermDemoted = ServiceManager.getDemotionService().isPermanentlyDemoted(targetId);
            boolean isTempDemoted = ServiceManager.getDemotionService().isTemporarilyDemoted(targetId);

            if (isPermDemoted || isTempDemoted) {
                String type = isPermDemoted ? "permanently" : "temporarily";
                event.getHook().editOriginalEmbeds(EmbedUtils.createErrorEmbed(
                        "Cannot add roles to " + targetName + " because they are " + type + " demoted."
                )).queue();
                return;
            }

            // 2. Check for Staff Prerequisite
            boolean isStaffOrMod = BotConfig.isStaffOrModRole(roleId);
            boolean isTrialRole = roleId.equals(BotConfig.TRIAL_SENTINELS_ROLE_ID);
            boolean isSeniorRole = roleId.equals(BotConfig.SENIOR_SENTINELS_ROLE_ID);
            boolean isBrulphRole = roleId.equals(BotConfig.BRULPH_ROLE_ID);

            // If giving a staff-related role, and they don't have trial yet, only allow giving Trial Sentinels
            // UNLESS they are being promoted specifically to Senior Sentinels or Brulph
            if (isStaffOrMod && !isTrialRole && !isSeniorRole && !isBrulphRole) {
                boolean hasRequiredRole = targetMember.getRoles().stream()
                        .anyMatch(role -> role.getId().equals(BotConfig.TRIAL_SENTINELS_ROLE_ID) 
                                       || role.getId().equals(BotConfig.STREAMER_HOSTS_ROLE_ID));

                if (!hasRequiredRole) {
                    event.getHook().editOriginalEmbeds(EmbedUtils.createErrorEmbed(
                            "You must give the Trial Sentinels role (<@&" + BotConfig.TRIAL_SENTINELS_ROLE_ID + ">) first before assigning other staff roles."
                    )).queue();
                    return;
                }
            }

            if (targetMember.getRoles().contains(targetRole)) {
                event.getHook().editOriginalEmbeds(EmbedUtils.createWarningEmbed(
                        targetName + " already has the role " + roleName + "."
                )).queue();
                return;
            }

            guild.addRoleToMember(targetMember, targetRole).queue(
                    success -> {
                        logRoleAction(guild, "added", moderator.getUser(), targetMember.getUser(), targetRole);
                        event.getHook().editOriginal("✅ Successfully added role **" + targetRole.getName() + "** to " + targetMember.getAsMention()).queue();
                    },
                    error -> {
                        event.getHook().editOriginalEmbeds(EmbedUtils.createErrorEmbed(
                                "Failed to add role: " + error.getMessage()
                        )).queue();
                    }
            );
        } else {
            if (!targetMember.getRoles().contains(targetRole)) {
                event.getHook().editOriginalEmbeds(EmbedUtils.createWarningEmbed(
                        targetName + " doesn't have the role " + roleName + "."
                )).queue();
                return;
            }

            guild.removeRoleFromMember(targetMember, targetRole).queue(
                    success -> {
                        logRoleAction(guild, "removed", moderator.getUser(), targetMember.getUser(), targetRole);
                        event.getHook().editOriginal("✅ Successfully removed role **" + targetRole.getName() + "** from " + targetMember.getAsMention()).queue();
                    },
                    error -> {
                        event.getHook().editOriginalEmbeds(EmbedUtils.createErrorEmbed(
                                "Failed to remove role: " + error.getMessage()
                        )).queue();
                    }
            );
        }
    }

    private void logRoleAction(Guild guild, String action, User moderator, User target, Role role) {
        boolean added = action.equalsIgnoreCase("added");
        String symbol = added ? "🔹" : "🔸";
        String title = symbol + " Role " + (added ? "Added" : "Removed");
        
        EmbedBuilder logEmbed = new EmbedBuilder()
                .setTitle(title)
                .setDescription(String.format("A role has been %s.", added ? "added" : "removed"))
                .addField("Target User", String.format("%s\n( `%s` )", target.getName(), target.getId()), false)
                .addField("Role", String.format("%s\n( `%s` )", role.getName(), role.getId()), false)
                .addField("Moderator", String.format("%s\n( `%s` )", moderator.getName(), moderator.getId()), false)
                .setColor(added ? Color.GREEN : Color.ORANGE)
                .setFooter("Role " + (added ? "Added" : "Removed"))
                .setTimestamp(Instant.now());

        if (target.getAvatarUrl() != null) {
            logEmbed.setThumbnail(target.getAvatarUrl());
        }

        ServiceManager.getLoggingService().logAction(guild, "moderation-logs", logEmbed.build());
    }

    private boolean hasRoleManagerPermission(Member member) {
        if (member == null) return false;
        return PermissionUtils.isAdmin(member, ServiceManager.getConfig()) || 
               PermissionUtils.isModerator(member, ServiceManager.getConfig());
    }

    private boolean canManageRole(Member moderator, Role targetRole, Guild guild) {
        if (moderator.isOwner()) return true;

        List<Role> modRoles = moderator.getRoles();
        if (modRoles.isEmpty()) return false;

        Role highestModRole = modRoles.get(0);
        return highestModRole.getPosition() > targetRole.getPosition();
    }
}
