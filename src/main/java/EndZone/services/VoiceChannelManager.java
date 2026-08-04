package EndZone.services;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.concrete.VoiceChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class VoiceChannelManager {
    private static final Logger logger = LoggerFactory.getLogger(VoiceChannelManager.class);
    private final Map<String, Long> userCreatedVoiceChannels = new ConcurrentHashMap<>();
    private final Set<Long> autoCreatedChannels = new HashSet<>();
    private final VoiceChannelService voiceService;

    private static final Pattern CREATE_VC_PATTERN = Pattern.compile(
            "Create (\\d+s?|Duo|Trio|Squad|Duos|Trios|Squads|6mans|Solo|Solos)( VC)?",
            Pattern.CASE_INSENSITIVE);

    private static final Map<String, Integer> NAME_TO_NUMBER = new HashMap<>();

    static {
        NAME_TO_NUMBER.put("solo", 1);
        NAME_TO_NUMBER.put("duo", 2);
        NAME_TO_NUMBER.put("trio", 3);
        NAME_TO_NUMBER.put("squad", 4);
        NAME_TO_NUMBER.put("6mans", 6);

        NAME_TO_NUMBER.put("solos", 1);
        NAME_TO_NUMBER.put("duos", 2);
        NAME_TO_NUMBER.put("trios", 3);
        NAME_TO_NUMBER.put("squads", 4);

        NAME_TO_NUMBER.put("1", 1);
        NAME_TO_NUMBER.put("2", 2);
        NAME_TO_NUMBER.put("3", 3);
        NAME_TO_NUMBER.put("4", 4);
        NAME_TO_NUMBER.put("6", 6);

        NAME_TO_NUMBER.put("1s", 1);
        NAME_TO_NUMBER.put("2s", 2);
        NAME_TO_NUMBER.put("3s", 3);
        NAME_TO_NUMBER.put("4s", 4);
        NAME_TO_NUMBER.put("6s", 6);
    }

    public VoiceChannelManager(VoiceChannelService voiceService) {
        this.voiceService = voiceService;
    }

    public boolean isCreateVcChannel(String channelName) {
        return CREATE_VC_PATTERN.matcher(channelName).matches();
    }

    public void createAutomaticVoiceChannel(Member member, String channelName) {
        Matcher matcher = CREATE_VC_PATTERN.matcher(channelName);
        if (matcher.matches()) {
            String userLimitStr = matcher.group(1);
            int userLimit;

            if (userLimitStr.matches("\\d+s")) {
                userLimitStr = userLimitStr.substring(0, userLimitStr.length() - 1);
            }

            if (userLimitStr.matches("\\d+")) {
                userLimit = Integer.parseInt(userLimitStr);
            } else {
                userLimit = NAME_TO_NUMBER.getOrDefault(userLimitStr.toLowerCase(), 0);
            }

            User user = member.getUser();
            Guild guild = member.getGuild();
            String guildId = guild.getId();

            String categoryId = voiceService.getCategoryId(guildId);
            if (categoryId == null) {
                // If not configured in DB, try to find a category named "VOICE CHANNELS" or similar
                for (net.dv8tion.jda.api.entities.channel.concrete.Category category : guild.getCategories()) {
                    String catName = category.getName().toLowerCase();
                    if (catName.contains("voice") || catName.contains("vc") || catName.equals("general voice") || catName.equals("vcs")) {
                        categoryId = category.getId();
                        break;
                    }
                }
            }
            
            if (categoryId == null) return;

            String newChannelName = member.getEffectiveName() + "'s VC";

            guild.createVoiceChannel(newChannelName)
                    .setParent(guild.getCategoryById(categoryId))
                    .setUserlimit(userLimit)
                    .queue(voiceChannel -> {
                        userCreatedVoiceChannels.put(user.getId(), voiceChannel.getIdLong());
                        autoCreatedChannels.add(voiceChannel.getIdLong());

                        voiceService.logChannelCreation(voiceChannel, user, "AUTOMATIC");

                        if (member.getVoiceState() != null && member.getVoiceState().inAudioChannel()) {
                            guild.moveVoiceMember(member, voiceChannel).queue();
                        }
                        logger.info("Created automatic voice channel: {} for member: {}", newChannelName, member.getEffectiveName());
                    }, error -> logger.error("Failed to create automatic voice channel: {}", error.getMessage()));
        }
    }

    public void createCustomVoiceChannel(Guild guild, User user, String name, int limit, Runnable onSuccess, java.util.function.Consumer<Throwable> onError) {
        String guildId = guild.getId();
        String categoryId = voiceService.getCategoryId(guildId);

        if (categoryId == null) {
            for (net.dv8tion.jda.api.entities.channel.concrete.Category category : guild.getCategories()) {
                String catName = category.getName().toLowerCase();
                if (catName.contains("voice") || catName.contains("vc") || catName.equals("general voice") || catName.equals("vcs")) {
                    categoryId = category.getId();
                    break;
                }
            }
        }

        if (categoryId == null && !guild.getCategories().isEmpty()) {
            categoryId = guild.getCategories().get(0).getId();
        }

        final String finalCategoryId = categoryId;

        if (finalCategoryId != null) {
            guild.createVoiceChannel(name)
                    .setParent(guild.getCategoryById(finalCategoryId))
                    .setUserlimit(limit)
                    .queue(voiceChannel -> {
                        userCreatedVoiceChannels.put(user.getId(), voiceChannel.getIdLong());
                        autoCreatedChannels.add(voiceChannel.getIdLong());

                        voiceService.logChannelCreation(voiceChannel, user, "CUSTOM");

                        if (onSuccess != null) onSuccess.run();
                        logger.info("Created custom voice channel: {} for user: {}", name, user.getName());
                    }, error -> {
                        if (onError != null) onError.accept(error);
                        logger.error("Failed to create custom voice channel: {}", error.getMessage());
                    });
        } else {
            guild.createVoiceChannel(name)
                    .setUserlimit(limit)
                    .queue(voiceChannel -> {
                        userCreatedVoiceChannels.put(user.getId(), voiceChannel.getIdLong());
                        autoCreatedChannels.add(voiceChannel.getIdLong());

                        voiceService.logChannelCreation(voiceChannel, user, "CUSTOM");

                        if (onSuccess != null) onSuccess.run();
                        logger.info("Created custom voice channel: {} for user: {} (no category)", name, user.getName());
                    }, error -> {
                        if (onError != null) onError.accept(error);
                        logger.error("Failed to create custom voice channel (no category): {}", error.getMessage());
                    });
        }
    }

    public void deleteEmptyVoiceChannel(VoiceChannel channel) {
        long channelId = channel.getIdLong();

        if (autoCreatedChannels.contains(channelId) && channel.getMembers().isEmpty()) {
            voiceService.markChannelDeleted(channel.getId());

            channel.delete().queue(success -> {
                autoCreatedChannels.remove(channelId);
                userCreatedVoiceChannels.values().removeIf(id -> id == channelId);
                logger.info("Deleted empty auto-created voice channel: {}", channel.getName());
            }, error -> logger.error("Failed to delete empty voice channel: {}", error.getMessage()));
        }
    }

    public boolean isVoiceChannelCreator(User user, VoiceChannel channel) {
        return userCreatedVoiceChannels.containsKey(user.getId()) &&
                userCreatedVoiceChannels.get(user.getId()) == channel.getIdLong();
    }

    public void deleteUserVoiceChannel(User user, VoiceChannel channel) {
        long channelId = channel.getIdLong();

        voiceService.markChannelDeleted(channel.getId());

        if (isVoiceChannelCreator(user, channel)) {
            userCreatedVoiceChannels.remove(user.getId());
        }
        autoCreatedChannels.remove(channelId);
    }

    public void addUserCreatedChannel(String userId, long channelId) {
        userCreatedVoiceChannels.put(userId, channelId);
        autoCreatedChannels.add(channelId);
    }

    public boolean isAutoCreatedChannel(long channelId) {
        return autoCreatedChannels.contains(channelId);
    }

    public boolean isManagedVoiceChannel(VoiceChannel channel) {
        if (channel == null) return false;
        
        // Check if it's an auto-created temporary channel
        if (isAutoCreatedChannel(channel.getIdLong())) return true;
        
        // Check if it belongs to a managed category from setup
        String guildId = channel.getGuild().getId();
        String managedCategoryId = voiceService.getCategoryId(guildId);
        
        if (managedCategoryId != null && channel.getParentCategory() != null) {
            if (channel.getParentCategory().getId().equals(managedCategoryId)) return true;
        }

        // Check explicit managed IDs
        String managedVcIds = voiceService.getManagedVcIds(guildId);
        if (managedVcIds != null && !managedVcIds.isEmpty()) {
            List<String> ids = Arrays.asList(managedVcIds.split(","));
            if (ids.contains(channel.getId())) return true;
        }

        // Check if category name contains "Winners" (common pattern for these zones)
        if (channel.getParentCategory() != null) {
            String catName = channel.getParentCategory().getName().toLowerCase();
            if (catName.contains("winners")) return true;
        }
        
        return false;
    }

    public VoiceChannelService getVoiceService() {
        return voiceService;
    }
}
