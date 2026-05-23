package EndZone.commands;

import EndZone.EndZone;
import EndZone.config.BotConfig;
import EndZone.models.Strike;
import EndZone.services.DemotionService;
import EndZone.services.ServiceManager;
import EndZone.services.StrikeService;
import EndZone.utils.PermissionUtils;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;

import java.awt.Color;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

public class StrikeCommand implements Command {

    private final EndZone bot;
    private final StrikeService strikeService;
    private final DemotionService demotionService;
    private static final ZoneId EST_ZONE = ZoneId.of("America/New_York");

    public StrikeCommand(EndZone bot) {
        this.bot = bot;
        this.strikeService = ServiceManager.getStrikeService();
        this.demotionService = ServiceManager.getDemotionService();
    }

    @Override
    public List<CommandData> getCommandDataList() {
        return List.of(
                Commands.slash("strike", "Issue a strike to a user.")
                        .addOption(OptionType.USER, "user", "The user to strike", true)
                        .addOption(OptionType.STRING, "reason", "Reason for the strike", true),
                Commands.slash("strikes", "View strikes of a user.")
                        .addOption(OptionType.USER, "user", "The user to view", true),
                Commands.slash("removestrike", "Remove a specific strike from a user.")
                        .addOption(OptionType.USER, "user", "The user to remove a strike from", true)
                        .addOption(OptionType.INTEGER, "number", "Strike number to remove (1 = first strike)", true),
                Commands.slash("clearstrikes", "Clear all strikes from a user. (Admin Only)")
                        .addOption(OptionType.USER, "user", "The user to clear strikes from", true),
                Commands.slash("editstrike", "Edit the reason of a specific strike. (Admin Only)")
                        .addOption(OptionType.USER, "user", "The user whose strike to edit", true)
                        .addOption(OptionType.INTEGER, "number", "Strike number to edit (1 = first strike)", true)
                        .addOption(OptionType.STRING, "newreason", "New reason for the strike", true)
        );
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        String commandName = event.getName();

        switch (commandName) {
            case "strike" -> handleStrike(event);
            case "strikes" -> handleStrikes(event);
            case "removestrike" -> handleRemoveStrike(event);
            case "clearstrikes" -> handleClearStrikes(event);
            case "editstrike" -> handleEditStrike(event);
        }
    }

    private void handleStrike(SlashCommandInteractionEvent event) {
        if (!PermissionUtils.isAlphaBetaOrHigher(event.getMember(), ServiceManager.getConfig())) {
            event.reply("❌ You do not have permission to issue strikes. Only Alpha Beta+ can use this command.").setEphemeral(true).queue();
            return;
        }

        User user = Objects.requireNonNull(event.getOption("user")).getAsUser();
        if (user.getId().equals(event.getUser().getId())) {
            event.reply("❌ You cannot strike yourself.").setEphemeral(true).queue();
            return;
        }

        String reason = Objects.requireNonNull(event.getOption("reason")).getAsString();

        event.deferReply(true).queue();
        Member moderator = event.getMember();

        event.getGuild().retrieveMember(user).queue(
                targetMember -> {
                    if (cannotStrike(event, moderator, targetMember)) {
                        return;
                    }
                    proceedWithStrike(event, user, reason, moderator);
                },
                failure -> {
                    event.getHook().editOriginal("❌ Could not find the target user in this server.").queue();
                }
        );
    }

    private boolean cannotStrike(SlashCommandInteractionEvent event, Member moderator, Member targetMember) {
        if (!PermissionUtils.canModerate(moderator, targetMember)) {
            event.getHook().editOriginal("❌ You cannot strike this user due to role hierarchy.").queue();
            return true;
        }

        if (PermissionUtils.isAlphaBetaOrHigher(moderator, ServiceManager.getConfig())) {
            // Alpha Beta+ can strike anyone
        } else if (hasProtectedRole(targetMember)) {
            event.getHook().editOriginal("❌ Cannot strike users with protected roles.").queue();
            return true;
        }

        if (isAlphaBetaPlusStrikingAlphaBetaPlus(moderator, targetMember)) {
            event.getHook().editOriginal("❌ Alpha Beta+ cannot strike other Alpha Beta+.").queue();
            return true;
        }

        return false;
    }

    private boolean hasProtectedRole(Member member) {
        for (String roleId : BotConfig.PROTECTED_ROLE_IDS) {
            if (member.getRoles().stream().anyMatch(role -> role.getId().equals(roleId))) {
                return true;
            }
        }
        return false;
    }

    private boolean isAlphaBetaPlusStrikingAlphaBetaPlus(Member moderator, Member target) {
        boolean moderatorIsAlphaBetaPlus = PermissionUtils.isAlphaBetaOrHigher(moderator, ServiceManager.getConfig());
        boolean targetIsAlphaBetaPlus = PermissionUtils.isAlphaBetaOrHigher(target, ServiceManager.getConfig());
        boolean moderatorIsHighAdminPlus = moderator.getRoles().stream().anyMatch(role -> role.getId().equals(BotConfig.MASTER_ALPHA_ROLE_ID));

        return targetIsAlphaBetaPlus && moderatorIsAlphaBetaPlus && !moderatorIsHighAdminPlus;
    }

    private void proceedWithStrike(SlashCommandInteractionEvent event, User user, String reason, Member moderator) {
        int previousStrikeCount = strikeService.getStrikes(user.getId()).size();
        strikeService.issueStrike(user.getId(), reason, moderator.getId());
        int strikeCount = previousStrikeCount + 1;

        if (strikeCount == 2) {
            handleStrikeRoleAlphaBetaPlus(event, user, strikeCount);
        } else if (strikeCount >= 3) {
            handleStrikeRoleAlphaBetaPlus(event, user, strikeCount);
        }

        sendStaffStrikeLog(event, user, reason, strikeCount);
        event.getHook().editOriginal("✅ Strike issued successfully!").queue();
    }

    private void sendStaffStrikeLog(SlashCommandInteractionEvent event, User user, String reason, int strikeCount) {
        String strikeWord = strikeCount == 1 ? "strike" : "strikes";
        String actionId = generateActionId();
        ZonedDateTime now = ZonedDateTime.now(EST_ZONE);
        long epochSecond = now.toEpochSecond();
        String discordTimestamp = String.format("<t:%d:F>", epochSecond);
        String footerTimestamp = now.format(java.time.format.DateTimeFormatter.ofPattern("M/d/yyyy h:mm a"));
        
        EmbedBuilder detailedEmbed = new EmbedBuilder()
                .setTitle("⚠️ STRIKE ISSUED")
                .setColor(new Color(0xFF6B00))
                .setDescription("A strike has been issued to a server member")
                .addField("📋 Target User", String.format("%s\n`%s`", user.getAsMention(), user.getId()), false)
                .addField("🛡️ Moderator", String.format("%s\n`%s`", event.getMember().getAsMention(), event.getMember().getId()), false)
                .addField("📊 Strike Count", getStrikeCountEmojis(strikeCount), false)
                .addField("📝 Reason", reason, false)
                .addField("⏰ Timestamp", discordTimestamp, false)
                .addField("⚡ Action ID", "`" + actionId + "`", false)
                .setFooter("Server Moderation System • Strike Issued • " + footerTimestamp);
        
        if (user.getAvatarUrl() != null) {
            detailedEmbed.setThumbnail(user.getAvatarUrl());
        }

        EmbedBuilder simpleEmbed = new EmbedBuilder()
                .setTitle("⛔ Strike Issued")
                .setColor(new Color(0x00E1FF))
                .setDescription(String.format("<@%s> (%s) has been striked.", user.getId(), user.getName()))
                .addField("User ID", user.getId(), false)
                .addField("Reason", reason, false)
                .addField("Now has:", strikeCount + " " + strikeWord, false)
                .setFooter(String.format("Strike System • %s", footerTimestamp));

        TextChannel logChannel = event.getJDA().getTextChannelById(BotConfig.STAFF_STRIKES_CHANNEL_ID);
        if (logChannel != null) {
            logChannel.sendMessageEmbeds(detailedEmbed.build()).queue(
                success -> System.out.println("✅ Detailed strike log sent to: " + logChannel.getId()),
                error -> System.err.println("❌ Failed to send detailed strike log: " + error.getMessage())
            );
        } else {
            System.err.println("❌ Detailed strike log channel NOT FOUND: " + BotConfig.STAFF_STRIKES_CHANNEL_ID);
        }
        TextChannel simpleLogChannel = ServiceManager.getLoggingService().getLogChannel(event.getGuild(), "staff-strikes");
        if (simpleLogChannel != null) {
            simpleLogChannel.sendMessageEmbeds(simpleEmbed.build()).queue();
            simpleLogChannel.sendMessage(user.getAsMention())
                    .queue(msg -> msg.delete().queueAfter(5, TimeUnit.SECONDS));
        }
    }
    
    private String getStrikeCountEmojis(int strikeCount) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 3; i++) {
            if (i < strikeCount) {
                sb.append("🔴");
            } else {
                sb.append("⚪");
            }
        }
        return sb.toString();
    }
    
    private String generateActionId() {
        return "MOD-" + System.currentTimeMillis() + "-" + (int) (Math.random() * 1000);
    }

    private void handleStrikeRoleAlphaBetaPlus(SlashCommandInteractionEvent event, User user, int strikeCount) {
        System.out.println("DEBUG: handleStrikeRoleAlphaBetaPlus for user " + user.getId() + ", strikes: " + strikeCount);
        event.getGuild().retrieveMember(user).queue(
                member -> {
                    System.out.println("DEBUG: Member found, proceeding with demotion logic. Current roles: " + member.getRoles().size());
                    if (strikeCount == 2) {
                        handleTwoStrikeDemotion(event, member);
                    } else if (strikeCount >= 3) {
                        handleThreeStrikeDemotion(event, member);
                    }
                },
                failure -> {
                    System.err.println("DEBUG: Failed to retrieve member for user " + user.getId() + ": " + failure.getMessage());
                }
        );
    }

    private void handleTwoStrikeDemotion(SlashCommandInteractionEvent event, Member member) {
        List<String> removedRoleIds = new ArrayList<>();
        List<String> allRoleIds = new ArrayList<>();
        
        for (Role role : member.getRoles()) {
            if (!role.isManaged() && !role.isPublicRole()) {
                allRoleIds.add(role.getId());
                if (BotConfig.isStaffOrModRole(role.getId())) {
                    removedRoleIds.add(role.getId());
                    event.getGuild().removeRoleFromMember(member, role).queue();
                }
            }
        }

        // Save ALL roles to user_roles backup table before removal
        strikeService.updateUserRoles(member.getId(), allRoleIds);

        java.time.Instant restorationDate = java.time.Instant.now().plus(BotConfig.TEMP_DEMOTION_DAYS, java.time.temporal.ChronoUnit.DAYS);
        long unixTimestamp = restorationDate.getEpochSecond();
        String discordTimestamp = String.format("<t:%d:F>", unixTimestamp);

        System.out.println("DEBUG: Saving temporary demotion for " + member.getId() + ". Roles removed: " + removedRoleIds.size());
        demotionService.addTemporaryDemotion(member.getId(), removedRoleIds, restorationDate);
        demotionService.updateDemotionListMessage(event.getJDA());

        TextChannel staffChannel = event.getJDA().getTextChannelById(BotConfig.STAFF_NOTIFICATION_CHANNEL_ID);
        if (staffChannel != null) {
            String message = String.format("<@%s> (%s) 2 strikes, gets roles back %s.", member.getId(), member.getId(), discordTimestamp);
            staffChannel.sendMessage(message).queue();
        }
    }

    private void handleThreeStrikeDemotion(SlashCommandInteractionEvent event, Member member) {
        List<String> removedRoles = new ArrayList<>();
        for (Role role : member.getRoles()) {
            if (BotConfig.isStaffOrModRole(role.getId())) {
                removedRoles.add(role.getId());
                event.getGuild().removeRoleFromMember(member, role).queue();
            }
        }

        java.time.Instant farFuture = java.time.Instant.now().plus(3650, java.time.temporal.ChronoUnit.DAYS);
        System.out.println("DEBUG: Saving permanent demotion (3 strikes) for " + member.getId() + ". Roles removed: " + removedRoles.size());
        demotionService.addTemporaryDemotion(member.getId(), removedRoles, farFuture);

        demotionService.addPermanentDemotion(member.getId());
        demotionService.updateDemotionListMessage(event.getJDA());
        updateBlacklistMessage(event, member.getId());
    }

    private void updateBlacklistMessage(SlashCommandInteractionEvent event, String userId) {
        TextChannel blacklistChannel = event.getJDA().getTextChannelById(BotConfig.BLACKLIST_CHANNEL_ID);
        if (blacklistChannel != null) {
            blacklistChannel.retrieveMessageById(BotConfig.BLACKLIST_MESSAGE_ID).queue(
                    message -> {
                        String userMention = "<@" + userId + "> (" + userId + ") 3 strikes, permanently demoted.";
                        String updatedContent = message.getContentRaw() + "\n" + userMention;
                        message.editMessage(updatedContent).queue();
                    },
                    failure -> {}
            );
        }
    }

    private void handleStrikes(SlashCommandInteractionEvent event) {
        if (!PermissionUtils.isGfxOrHigher(event.getMember(), ServiceManager.getConfig())) {
            event.reply("❌ You do not have permission to view strikes. Only GFX/ Content Team & above can use this command.").setEphemeral(true).queue();
            return;
        }

        User user = Objects.requireNonNull(event.getOption("user")).getAsUser();
        List<Strike> strikes = strikeService.getAllStrikes(user.getId());
        ZonedDateTime now = ZonedDateTime.now(EST_ZONE);
        String footerTimestamp = now.format(java.time.format.DateTimeFormatter.ofPattern("M/d/yyyy h:mm a"));

        EmbedBuilder embed = new EmbedBuilder()
                .setTitle("📋 Strike History for " + user.getName())
                .setFooter("Strike System • " + footerTimestamp);

        if (strikes.isEmpty()) {
            embed.setColor(new Color(0x00E1FF))
                 .setDescription("✅ No strikes found for " + user.getAsMention());
        } else {
            embed.setColor(new Color(0x00E1FF));
            StringBuilder sb = new StringBuilder();
            
            // Get active strikes to compare and show status
            List<Strike> activeStrikes = strikeService.getStrikes(user.getId());
            java.util.Set<Integer> activeIds = activeStrikes.stream()
                    .map(Strike::getId)
                    .collect(java.util.stream.Collectors.toSet());

            for (int i = 0; i < strikes.size(); i++) {
                Strike strike = strikes.get(i);
                boolean isActive = activeIds.contains(strike.getId());
                String status = isActive ? "" : " *(APPEALED/REMOVED)*";
                
                sb.append(String.format("**Strike #%d**%s\n> **Reason:** %s\n> **Moderator:** <@%s>\n> **Date:** <t:%d:F>\n\n",
                        i + 1, status, strike.getReason(), strike.getModeratorId(), strike.getTimestamp().getTime() / 1000));
            }
            embed.setDescription(sb.toString());
        }

        event.replyEmbeds(embed.build()).setEphemeral(true).queue();
    }

    private void handleRemoveStrike(SlashCommandInteractionEvent event) {
        if (!PermissionUtils.isAlphaBetaOrHigher(event.getMember(), ServiceManager.getConfig())) {
            event.reply("❌ You do not have permission to remove strikes. Only Alpha Beta+ can use this command.").setEphemeral(true).queue();
            return;
        }

        User user = Objects.requireNonNull(event.getOption("user")).getAsUser();
        int strikeNumber = Objects.requireNonNull(event.getOption("number")).getAsInt();

        List<Strike> strikes = strikeService.getStrikes(user.getId());
        if (strikeNumber < 1 || strikeNumber > strikes.size()) {
            event.reply("❌ Invalid strike number.").setEphemeral(true).queue();
            return;
        }

        Strike strikeToRemove = strikes.get(strikeNumber - 1);
        strikeService.removeStrike(user.getId(), strikeToRemove.getTimestamp());

        event.reply("✅ Removed strike #" + strikeNumber + " from " + user.getAsMention()).setEphemeral(true).queue();
    }

    private void handleClearStrikes(SlashCommandInteractionEvent event) {
        if (!PermissionUtils.isAlphaBetaOrHigher(event.getMember(), ServiceManager.getConfig())) {
            event.reply("❌ You do not have permission to clear strikes. Only Alpha Beta+ can use this command.").setEphemeral(true).queue();
            return;
        }

        User user = Objects.requireNonNull(event.getOption("user")).getAsUser();
        System.out.println("DEBUG: handleClearStrikes command triggered for user: " + user.getId());
        strikeService.clearStrikes(user.getId());

        event.reply("✅ Cleared all strikes from " + user.getAsMention()).setEphemeral(true).queue();
    }

    private void handleEditStrike(SlashCommandInteractionEvent event) {
        if (!PermissionUtils.isAlphaBetaOrHigher(event.getMember(), ServiceManager.getConfig())) {
            event.reply("❌ You do not have permission to edit strikes. Only Alpha Beta+ can use this command.").setEphemeral(true).queue();
            return;
        }

        User user = Objects.requireNonNull(event.getOption("user")).getAsUser();
        int strikeNumber = Objects.requireNonNull(event.getOption("number")).getAsInt();
        String newReason = Objects.requireNonNull(event.getOption("newreason")).getAsString();

        List<Strike> strikes = strikeService.getStrikes(user.getId());
        if (strikeNumber < 1 || strikeNumber > strikes.size()) {
            event.reply("❌ Invalid strike number.").setEphemeral(true).queue();
            return;
        }

        Strike strikeToEdit = strikes.get(strikeNumber - 1);
        strikeService.editStrikeReason(user.getId(), strikeToEdit.getTimestamp(), newReason);

        event.reply("✅ Edited strike #" + strikeNumber + " for " + user.getAsMention()).setEphemeral(true).queue();
    }
}
