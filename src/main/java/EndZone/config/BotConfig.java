package EndZone.config;

import io.github.cdimascio.dotenv.Dotenv;
import net.dv8tion.jda.api.OnlineStatus;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class BotConfig {

    private static Dotenv dotenv;

    static {
        dotenv = loadDotenv();
    }

    public static final String MEMBER_ROLE_ID = "790162629645303829";
    public static final String WELCOME_CHANNEL_ID = "1099483814377562192";
    public static final String GENERAL_CHAT_CHANNEL_ID = "1099483814377562192";
    public static final String ACCESS_HELP_CHANNEL_ID = "1478861498632769683";
    public static final String PROMO_MESSAGE = "If you like the EZ Bot, you should add the Server Moderator Bot! Click here to add it: https://discord.com/api/oauth2/authorize?client_id=1342357132372213843&permissions=8&scope=bot%20applications.commands";
    public static final String ACCESS_HELP_PROMO = "This is for help if you cannot verify in <#1478541170048241724>. Please do not spam ping <@&1483691965601157201> or <@&1483685546080469033> to give you roles! It won't go any faster, and you can be warned, muted, or timed out if you keep pinging <@&1483691965601157201> & <@&1483685546080469033>.";
    public static final String NAME_LOG_CHANNEL = "1478561789062021202";
    public static final String EVENT_NAME_CHANNEL = "1478585421318455447";

    private static final List<String> AUTO_REACTION_CHANNELS = Arrays.asList(
            "1269416717994426528",
            "1261562170190332004",
            "1197710900426190910",
            "1099482369884434613",
            "1099698123623895102",
            "1442559445422047232",
            "1099662653196083210",
            "1099666617580916767",
            "1310054737097523220"
    );

    public static final String EZ_EMOJI_NAME = "EZ_new";
    public static final String EZ_EMOJI_ID = "1478805339011809350";
    public static final String EZ_EMOJI_MENTION = "<:EZ_new:1478805339011809350>";

    private static final Map<String, EmojiConfig> CHANNEL_EMOJI_MAP = createChannelEmojiMap();

    private static Map<String, EmojiConfig> createChannelEmojiMap() {
        Map<String, EmojiConfig> map = new HashMap<>();

        map.put("1099482369884434613", new EmojiConfig("👍", null, null));
        map.put("1099698123623895102", new EmojiConfig(null, "EZ_new", "1478805339011809350"));
        map.put("1442559445422047232", new EmojiConfig(null, "EZ_new", "1478805339011809350"));
        map.put("1099662653196083210", new EmojiConfig(null, "EZ_new", "1478805339011809350"));
        map.put("1099666617580916767", new EmojiConfig(null, "JSE", "839166137396363265"));
        map.put("1310054737097523220", new EmojiConfig(null, "ZRU", "995054086405754911"));

        map.put("1269416717994426528", new EmojiConfig(null, "EZ_new", "1478805339011809350"));
        map.put("1261562170190332004", new EmojiConfig(null, "EZ_new", "1478805339011809350"));
        map.put("1197710900426190910", new EmojiConfig(null, "EZ_new", "1478805339011809350"));

        return map;
    }

    public static class EmojiConfig {
        public final String unicodeEmoji;
        public final String customEmojiName;
        public final String customEmojiId;

        public EmojiConfig(String unicodeEmoji, String customEmojiName, String customEmojiId) {
            this.unicodeEmoji = unicodeEmoji;
            this.customEmojiName = customEmojiName;
            this.customEmojiId = customEmojiId;
        }
    }

    private static Dotenv loadDotenv() {
        String envFilePath = System.getenv("ENV_FILE_PATH");
        if (envFilePath != null && !envFilePath.isEmpty()) {
            System.out.println("Trying ENV_FILE_PATH: " + envFilePath);
            try {
                Dotenv loaded = Dotenv.configure()
                        .filename(envFilePath)
                        .ignoreIfMissing()
                        .load();
                if (loaded.get("BOT_TOKEN") != null || loaded.get("DATABASE_PATH") != null) {
                    System.out.println("Loaded .env from ENV_FILE_PATH");
                    return loaded;
                }
            } catch (Exception e) {
                System.out.println("Error loading from ENV_FILE_PATH: " + e.getMessage());
            }
        }

        List<String> directories = new ArrayList<>();
        directories.add(".");
        directories.add("..");
        directories.add("../");
        directories.add("../../");
        directories.add("/");
        directories.add("/home");
        directories.add("/home/container");
        directories.add("/app");
        directories.add("/bot");
        directories.add("/srv");
        directories.add(System.getProperty("user.home"));
        directories.add(System.getProperty("user.dir"));
        directories.add(System.getProperty("java.io.tmpdir"));
        directories.add("./src/main/java/EndZone");
        directories.add("./src/main/java/endzone");
        directories.add("./src/main/resources");

        System.out.println("Searching for .env file in " + directories.size() + " directories...");
        
        for (String dir : directories) {
            try {
                System.out.println("  Checking: " + dir);
                Dotenv loaded = Dotenv.configure()
                        .directory(dir)
                        .ignoreIfMissing()
                        .load();

                if (loaded.get("BOT_TOKEN") != null || loaded.get("DATABASE_PATH") != null) {
                    System.out.println("Found .env file at: " + dir);
                    return loaded;
                }
            } catch (Exception e) {
                System.out.println("    Error: " + e.getMessage());
            }
        }

        System.out.println("WARNING: Could not find .env file in any directory!");
        return Dotenv.configure().ignoreIfMissing().load();
    }

    public static final String GUILD_ID = "790157978647920641";
    public static final String COURT_GUILD_ID = "1095553644943912980";
    public static final String OWNER_USER_ID = "689519709988585648";
    public static final String CREATE_APPEAL_HERE_CHANNEL_ID = "1095760087487873154";
    public static final String TICKET_ZONE_CATEGORY_ID = "1095760978404200488"; 

    // Roles
    public static final String BRULPH_ROLE_ID = "1143448682608472104";
    public static final String HELP_ROLE_ID = "849556974776746035";
    public static final String PERMS_ROLE_ID = "792510238611603486";
    public static final String MASTER_ALPHA_ROLE_ID = "1101762878719152209";
    public static final String STAR_ROLE_ID = "1138949735957413999";
    public static final String ALPHAS_ROLE_ID = "790162570455154699";
    public static final String SERVER_ROBOTS_ROLE_ID = "1458623869807230977";

    public static final String ALPHA_BETAS_ROLE_ID = "810285573985796147";
    public static final String OTHER_GAMES_MANAGER_ROLE_ID = "1477822335745654847";
    public static final String SENIOR_SENTINELS_ROLE_ID = "1478569243359580344";

    public static final String TRIAL_SENTINELS_ROLE_ID = "1478568776969748481";

    // CourtZone Roles
    public static final String COURT_THE_JUDGE_EZ_ROLE_ID = "1095752007404494878";
    public static final String COURT_THE_DISTRICT_ATTORNIES_EZ_ROLE_ID = "1478511233224409190";
    public static final String COURT_THE_JUDGE_ZRE_ROLE_ID = "1469428334231552153";
    public static final String COURT_SPECIAL_PEOPLE_ROLE_ID = "1457811997126168797";

    // Jury Roles
    public static final String COURT_THE_JURY_EZ_ROLE_ID = "1095752242100977676";
    public static final String COURT_THE_JURY_ZRE_ROLE_ID = "1447399748267933716";

    public static final String STREAMER_HOSTS_ROLE_ID = "791240455899447316";
    public static final String GFX_CONTENT_TEAM_ROLE_ID = "790174939419639828";
    public static final String ENDZONE_HOST_ROLE_ID = "1475368370672107722";
    public static final String ENDZONE_CHAT_MODERATOR_ROLE_ID = "1475368281899536416";
    public static final String GAME_A_MODERATOR_ROLE_ID = "1475368078614462526";
    public static final String GAME_B_MODERATOR_ROLE_ID = "1475368187557056602";
    public static final String ENDZONE_VOICE_CHAT_MODERATOR_ROLE_ID = "1476761382044307539";

    public static final String MUTE_ROLE_ID = "793529310887149619";

    // Channels
    public static final String ENDZONE_LOG_CHANNEL_ID = "1478577625730388109";
    public static final String MOD_LOG_CHANNEL_ID = "1478866155723690145";
    public static final String VOICE_LOG_CHANNEL_ID = "1092426362016514129";
    public static final String NAME_LOG_CHANNEL_ID = "1478561789062021202";
    public static final String EVENT_NAME_LOG_ID = "1478585421318455447";
    public static final String JOIN_LEAVE_LOG_CHANNEL_ID = "790178136527863838";
    public static final String MESSAGE_LOG_CHANNEL_ID = "1478568628537262283";
    public static final String STAFF_NOTIFICATION_CHANNEL_ID = "1478853936441200690";
    public static final String APPLICATION_CHANNEL_ID = "1478855549553479813";
    public static final String MANAGER_CHAT_CHANNEL_ID = "1099663917308973157";
    public static final String BLACKLIST_CHANNEL_ID = "1478853936441200690";
    public static final String STAFF_CHAT_CHANNEL_ID = "1477039155392282935";
    public static final String STAFF_APPEALS_CHANNEL_ID = "1478854140334575666";
    public static final String STAFF_STRIKES_CHANNEL_ID = "1478591786833281125";
    public static final String STAFF_STRIKE_LOG_CHANNEL_ID = "1478591744357568594";
    public static final String STAFF_VERIFY_CHANNEL_ID = "1197710900426190910";
    public static final String RULES_CHANNEL_ID = "790162355404144670";
    public static final String DRAFTING_THINGS_CHANNEL_ID = "1478569800954544270";
    public static final String EVENT_COUNTDOWNS_CHANNEL_ID = "1478566788177330349";
    public static final String EZ_PERM_BAN_LIST_CHANNEL_ID = "1099474903499026493";
    public static final String EZ_UNBAN_LIST_CHANNEL_ID = "1095756470508855306";

    // Message IDs
    public static final String BLACKLIST_MESSAGE_ID = "1439847570942853252";
    public static final String STAFF_VERIFY_MESSAGE_ID = "1412960883986010122";
    public static final String VERIFICATION_REACTION_ROLES_MESSAGE_ID = "1466274041563447430";

    public static final int TEMP_DEMOTION_DAYS = 9;

    private static final List<String> MOD_ROLES = Arrays.asList(
            BRULPH_ROLE_ID,
            HELP_ROLE_ID,
            PERMS_ROLE_ID,
            MASTER_ALPHA_ROLE_ID,
            STAR_ROLE_ID,
            ALPHAS_ROLE_ID,
            SERVER_ROBOTS_ROLE_ID,
            ALPHA_BETAS_ROLE_ID,
            OTHER_GAMES_MANAGER_ROLE_ID,
            SENIOR_SENTINELS_ROLE_ID,
            COURT_THE_JUDGE_EZ_ROLE_ID,
            COURT_THE_DISTRICT_ATTORNIES_EZ_ROLE_ID,
            COURT_THE_JUDGE_ZRE_ROLE_ID,
            COURT_SPECIAL_PEOPLE_ROLE_ID,
            COURT_THE_JURY_EZ_ROLE_ID,
            COURT_THE_JURY_ZRE_ROLE_ID
    );

    public static final List<String> ADMIN_ROLES = Arrays.asList(
            BRULPH_ROLE_ID,
            HELP_ROLE_ID,
            PERMS_ROLE_ID,
            MASTER_ALPHA_ROLE_ID,
            STAR_ROLE_ID,
            ALPHAS_ROLE_ID,
            ALPHA_BETAS_ROLE_ID,
            OTHER_GAMES_MANAGER_ROLE_ID,
            SERVER_ROBOTS_ROLE_ID,
            COURT_THE_JUDGE_EZ_ROLE_ID
    );

    private static final List<String> COURT_MOD_ONLY_ROLES = Arrays.asList(
            COURT_THE_JUDGE_ZRE_ROLE_ID,
            COURT_SPECIAL_PEOPLE_ROLE_ID,
            COURT_THE_JURY_EZ_ROLE_ID,
            COURT_THE_JURY_ZRE_ROLE_ID
    );

    public static final List<String> SEMI_MOD_ROLES = Arrays.asList(
            TRIAL_SENTINELS_ROLE_ID
    );

    public static final List<String> STAFF_ROLE_IDS = Arrays.asList(
            BRULPH_ROLE_ID,
            HELP_ROLE_ID,
            PERMS_ROLE_ID,
            MASTER_ALPHA_ROLE_ID,
            STAR_ROLE_ID,
            ALPHAS_ROLE_ID,
            SERVER_ROBOTS_ROLE_ID,
            ALPHA_BETAS_ROLE_ID,
            OTHER_GAMES_MANAGER_ROLE_ID,
            SENIOR_SENTINELS_ROLE_ID,
            TRIAL_SENTINELS_ROLE_ID,
            STREAMER_HOSTS_ROLE_ID,
            GFX_CONTENT_TEAM_ROLE_ID,
            ENDZONE_HOST_ROLE_ID,
            ENDZONE_CHAT_MODERATOR_ROLE_ID,
            GAME_A_MODERATOR_ROLE_ID,
            GAME_B_MODERATOR_ROLE_ID,
            ENDZONE_VOICE_CHAT_MODERATOR_ROLE_ID,
            COURT_THE_JURY_EZ_ROLE_ID,
            COURT_THE_JURY_ZRE_ROLE_ID
    );

    public static final List<String> PROTECTED_ROLE_IDS = Arrays.asList(
            BRULPH_ROLE_ID,
            HELP_ROLE_ID,
            PERMS_ROLE_ID,
            MASTER_ALPHA_ROLE_ID,
            STAR_ROLE_ID,
            ALPHAS_ROLE_ID,
            OTHER_GAMES_MANAGER_ROLE_ID,
            SERVER_ROBOTS_ROLE_ID,
            COURT_THE_JUDGE_EZ_ROLE_ID,
            COURT_THE_JUDGE_ZRE_ROLE_ID,
            COURT_SPECIAL_PEOPLE_ROLE_ID
    );

    private static final List<String> JURY_ROLES = Arrays.asList(
            COURT_THE_JURY_EZ_ROLE_ID,
            COURT_THE_JURY_ZRE_ROLE_ID
    );

    public static boolean isStaffOrModRole(String roleId) {
        return STAFF_ROLE_IDS.contains(roleId) 
            || MOD_ROLES.contains(roleId) 
            || ADMIN_ROLES.contains(roleId)
            || SEMI_MOD_ROLES.contains(roleId);
    }

    public static final String STAFF_STRIKES_ROLE_ID = BRULPH_ROLE_ID;

    private String loadFromEnvFile(String key) {
        if (dotenv != null) {
            String value = dotenv.get(key);
            if (value != null && !value.isEmpty()) {
                return value;
            }
        }
        return null;
    }

    public List<String> getJuryRoles() {
        return JURY_ROLES;
    }

    public List<String> getCourtModOnlyRoles() {
        return COURT_MOD_ONLY_ROLES;
    }

    public String getToken() {
        String token = System.getenv("BOT_TOKEN");
        if (token == null || token.isEmpty()) {
            token = System.getenv("TOKEN");
        }

        if (token != null && !token.isEmpty()) {
            System.out.println("Found token in environment variables");
            return token;
        }

        System.out.println("Working Directory = " + System.getProperty("user.dir"));

        token = loadFromEnvFile("BOT_TOKEN");
        if (token != null && !token.isEmpty()) {
            System.out.println("Found token in .env file");
            return token;
        }

        return token;
    }

    public String getEnvOrDefault(String key, String defaultValue) {
        String value = System.getenv(key);
        if (value != null && !value.isEmpty()) {
            return value;
        }

        value = loadFromEnvFile(key);
        if (value != null && !value.isEmpty()) {
            return value;
        }

        return defaultValue;
    }

    public String getGuildId() {
        return getEnvOrDefault("GUILD_ID", GUILD_ID);
    }

    public String getStatusText() {
        return getEnvOrDefault("BOT_STATUS", "🌍 Watching EZ!");
    }

    public String getStatusUrl() {
        return getEnvOrDefault("BOT_STATUS_URL", "https://www.twitch.tv/mrjawesomeyt");
    }

    public OnlineStatus getOnlineStatus() {
        String statusTypeStr = getEnvOrDefault("BOT_ONLINE_STATUS", "ONLINE");
        return OnlineStatus.valueOf(statusTypeStr);
    }

    public String getMemberRoleId() {
        return MEMBER_ROLE_ID;
    }

    public String getWelcomeChannelId() {
        return WELCOME_CHANNEL_ID;
    }

    public String getGeneralChatChannelId() {
        return GENERAL_CHAT_CHANNEL_ID;
    }

    public String getPromoMessage() {
        return PROMO_MESSAGE;
    }

    public String getAccessHelpChannelId() {
        return getEnvOrDefault("ACCESS_HELP_CHANNEL_ID", ACCESS_HELP_CHANNEL_ID);
    }

    public String getAccessHelpPromo() {
        return ACCESS_HELP_PROMO;
    }

    public String getNameLogChannelId() {
        return NAME_LOG_CHANNEL;
    }

    public String getEventNameChannelId() {
        return EVENT_NAME_CHANNEL;
    }

    public String getModLogChannelId() {
        return MOD_LOG_CHANNEL_ID;
    }

    public List<String> getAutoReactionChannels() {
        return AUTO_REACTION_CHANNELS;
    }

    public String getEzEmojiName() {
        return EZ_EMOJI_NAME;
    }

    public String getEzEmojiId() {
        return EZ_EMOJI_ID;
    }

    public List<String> getModRoles() {
        return MOD_ROLES;
    }

    public List<String> getAdminRoles() {
        return ADMIN_ROLES;
    }

    public List<String> getSemiModRoles() {
        return SEMI_MOD_ROLES;
    }

    public String getStaffStrikesRoleId() {
        return STAFF_STRIKES_ROLE_ID;
    }

    public EmojiConfig getChannelEmojiConfig(String channelId) {
        return CHANNEL_EMOJI_MAP.get(channelId);
    }
}
