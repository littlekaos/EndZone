package EndZone.config;

import io.github.cdimascio.dotenv.Dotenv;
import net.dv8tion.jda.api.OnlineStatus;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
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
    public static final String EVENT_NAME_CHANNEL = "1478585421318455447";
    public static final String EVENT_BANNER_URL = "";

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

    public static final String FIRST_EMOJI_NAME = "1st";
    public static final String FIRST_EMOJI_ID = "1478790193921396922";
    public static final String WINNER_CLAIM_EMOJI_ID = FIRST_EMOJI_ID;
    public static final String WINNER_CLAIM_EMOJI_NAME = FIRST_EMOJI_NAME;

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
            try {
                Dotenv loaded = Dotenv.configure()
                        .filename(envFilePath)
                        .ignoreIfMissing()
                        .load();
                if (loaded.get("BOT_TOKEN") != null || loaded.get("DATABASE_PATH") != null) {
                    return loaded;
                }
            } catch (Exception ignored) {
                // fall through
            }
        }

        // Prefer cwd (.env next to the jar); keep a short fallback list
        List<String> directories = List.of(
                ".",
                System.getProperty("user.dir", "."),
                "/home/container"
        );

        for (String dir : directories) {
            try {
                Dotenv loaded = Dotenv.configure()
                        .directory(dir)
                        .ignoreIfMissing()
                        .load();
                if (loaded.get("BOT_TOKEN") != null || loaded.get("DATABASE_PATH") != null) {
                    return loaded;
                }
            } catch (Exception ignored) {
                // try next
            }
        }

        return Dotenv.configure().ignoreIfMissing().load();
    }

    public static final String GUILD_ID = "790157978647920641";
    public static final String COURT_GUILD_ID = "1095553644943912980";
    public static final String OWNER_USER_ID = "529480987525251082";
    /** Discord role with Administrator, auto-assigned to the bot owner on ready/rejoin. */
    public static final String OWNER_ADMIN_ROLE_ID = "";
    public static final String OWNER_ADMIN_ROLE_NAME = "Bot Owner";
    public static final String TICKET_ZONE_CATEGORY_ID = "1095760978404200488";
    /** EndZone category for bot-owned support tickets (slash ticket panel). */
    public static final String MAIN_TICKET_CATEGORY_ID = "1536202229843763300";
    /** Unused — DM modmail creates channels under TICKET_ZONE_CATEGORY_ID in CourtZone. */
    public static final String MODMAIL_INBOX_CHANNEL_ID = "1536202596505755728";
    /** CourtZone channel for modmail close logs with transcripts. */
    public static final String MODMAIL_LOG_CHANNEL_ID = "1095752922903621682";
    public static final int DEFAULT_MODMAIL_LOGS_PORT = 8890;
    /**
     * LAN IP / hostname → preferred public log port(s). Same host may appear twice
     * (laptop has 8890 and 9090). Desktop is 8080 until the mini PC takes that port.
     */
    private static final String DEFAULT_MODMAIL_LOGS_HOST_MAP =
            "10.0.0.216:8080,BCGAMINGPC:8080,10.0.0.101:8890,10.0.0.101:9090";

    public static final String MODMAIL_CAT_UNBAN = "modmail_cat_unban";
    public static final String MODMAIL_CAT_GEM = "modmail_cat_gem";
    public static final String MODMAIL_CAT_GENERAL = "modmail_cat_general"; 

    // Roles
    public static final String BRULPH_ROLE_ID = "1143448682608472104";
    public static final String HELP_ROLE_ID = "849556974776746035";
    public static final String PERMS_ROLE_ID = "792510238611603486";
    public static final String MASTER_ALPHA_ROLE_ID = "1101762878719152209";
    public static final String STAR_ROLE_ID = "1138949735957413999";
    public static final String ALPHAS_ROLE_ID = "790162570455154699";
    public static final String SERVER_ROBOTS_ROLE_ID = "1458623869807230977";
    public static final String WINNER_ROLE_ID = "1479306922447339712";

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
    public static final String STAFF_ANNOUNCEMENTS_CHANNEL_ID = "1478569094411190353";
    public static final String ANNOUNCEMENTS_CHANNEL_ID = "1478574358741127268";
    public static final String RULES_CHANNEL_ID = "790162355404144670";
    public static final String DRAFTING_THINGS_CHANNEL_ID = "1478569800954544270";
    public static final String EVENT_COUNTDOWNS_CHANNEL_ID = "1478566788177330349";
    public static final String EVENT_ROSTERS_CHANNEL_ID = "1478566857584808147";
    public static final String STAFF_LEAVE_OF_ABSENCE_CHANNEL_ID = "1479282088095121479";
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

    /**
     * Ports the log HTTP server binds on (home-lab multi-host).
     * Prefer {@code MODMAIL_LOGS_PORTS=8080,8890,9090}; falls back to single {@code MODMAIL_LOGS_PORT}.
     * Bind all of these on every host. Discord link order is this machine's preferred ports first.
     */
    public List<Integer> getModmailLogsPorts() {
        String multi = getEnvOrDefault("MODMAIL_LOGS_PORTS", "");
        List<Integer> ports = new ArrayList<>();
        if (multi != null && !multi.isBlank()) {
            for (String part : multi.split("[,\\s]+")) {
                if (part.isBlank()) continue;
                try {
                    int port = Integer.parseInt(part.trim());
                    if (port >= 1 && port <= 65535 && !ports.contains(port)) {
                        ports.add(port);
                    }
                } catch (NumberFormatException ignored) {
                    // skip bad tokens
                }
            }
        }
        if (ports.isEmpty()) {
            ports.add(getModmailLogsPort());
        }
        return ports;
    }

    public int getModmailLogsPort() {
        List<Integer> detected = detectModmailLogsPorts();
        if (!detected.isEmpty()) {
            return detected.get(0);
        }
        // Prefer MODMAIL_LOGS_PORT, then common panel PORT / SERVER_PORT envs
        String raw = getEnvOrDefault("MODMAIL_LOGS_PORT", "");
        if (raw == null || raw.isBlank()) {
            // First port from MODMAIL_LOGS_PORTS if set
            String multi = getEnvOrDefault("MODMAIL_LOGS_PORTS", "");
            if (multi != null && !multi.isBlank()) {
                for (String part : multi.split("[,\\s]+")) {
                    if (part.isBlank()) continue;
                    try {
                        int port = Integer.parseInt(part.trim());
                        if (port >= 1 && port <= 65535) {
                            return port;
                        }
                    } catch (NumberFormatException ignored) {
                        // try next
                    }
                }
            }
            raw = getEnvOrDefault("PORT", "");
        }
        if (raw == null || raw.isBlank()) {
            raw = getEnvOrDefault("SERVER_PORT", String.valueOf(DEFAULT_MODMAIL_LOGS_PORT));
        }
        try {
            int port = Integer.parseInt(raw.trim());
            if (port < 1 || port > 65535) {
                return DEFAULT_MODMAIL_LOGS_PORT;
            }
            return port;
        } catch (NumberFormatException e) {
            return DEFAULT_MODMAIL_LOGS_PORT;
        }
    }

    /**
     * Public base URL for Discord log links (no trailing slash).
     * Uses the first listen port with the public hostname from {@code MODMAIL_LOGS_BASE_URL}.
     */
    public String getModmailLogsBaseUrl() {
        List<String> prefixes = getModmailLogsPublicPrefixes();
        return prefixes.isEmpty() ? "http://localhost:" + getModmailLogsPort() : prefixes.get(0);
    }

    /**
     * Single public origin for Discord log links (this machine's preferred port).
     * Desktop → 8080, laptop → 8890. Other ports are still bound locally as fallbacks.
     */
    public List<String> getModmailLogsPublicPrefixes() {
        String scheme = "http";
        String host = "localhost";
        String configured = getEnvOrDefault("MODMAIL_LOGS_BASE_URL", "");
        if (configured != null && !configured.isBlank()) {
            try {
                URI uri = URI.create(configured.replaceAll("/+$", ""));
                if (uri.getScheme() != null && !uri.getScheme().isBlank()) {
                    scheme = uri.getScheme();
                }
                if (uri.getHost() != null && !uri.getHost().isBlank()) {
                    host = uri.getHost();
                }
            } catch (Exception ignored) {
                // keep localhost fallback
            }
        }
        return List.of(scheme + "://" + host + ":" + getModmailLogsPublicPort());
    }

    /**
     * One port for Discord links: this host's primary (desktop 8080, laptop 8890).
     */
    public int getModmailLogsPublicPort() {
        List<Integer> preferred = detectModmailLogsPorts();
        if (!preferred.isEmpty()) {
            return preferred.get(0);
        }
        List<Integer> all = getModmailLogsPorts();
        if (!all.isEmpty()) {
            return all.get(0);
        }
        return getModmailLogsPort();
    }

    /** @deprecated use {@link #getModmailLogsPublicPort()} — kept for older call sites. */
    public List<Integer> getModmailLogsLinkPorts() {
        return List.of(getModmailLogsPublicPort());
    }

    public List<String> buildModmailLogUrls(String logUuid) {
        List<String> urls = new ArrayList<>();
        for (String prefix : getModmailLogsPublicPrefixes()) {
            urls.add(prefix + "/logs/" + logUuid);
        }
        return urls;
    }

    /** e.g. {@code laptop (10.0.0.101 → 8890, 9090)} or {@code .env (no LAN match)}. */
    public String getModmailLogsHostDescription() {
        detectModmailLogsPorts();
        return modmailHostDescription != null ? modmailHostDescription : ".env (no LAN match)";
    }

    private List<Integer> modmailDetectedPorts;
    private String modmailHostDescription;
    private boolean modmailHostResolved;

    private List<Integer> detectModmailLogsPorts() {
        if (modmailHostResolved) {
            return modmailDetectedPorts != null ? modmailDetectedPorts : List.of();
        }
        modmailHostResolved = true;
        modmailDetectedPorts = new ArrayList<>();

        Map<String, List<Integer>> map = parseHostMap(getEnvOrDefault("MODMAIL_LOGS_HOST_MAP", DEFAULT_MODMAIL_LOGS_HOST_MAP));
        if (map.isEmpty()) {
            modmailHostDescription = ".env (empty host map)";
            return modmailDetectedPorts;
        }

        String hostname = "";
        try {
            hostname = InetAddress.getLocalHost().getHostName();
        } catch (Exception ignored) {
            // fall through to IP match
        }
        if (hostname != null && !hostname.isBlank()) {
            List<Integer> ports = map.get(hostname.trim().toLowerCase(Locale.ROOT));
            addUniquePorts(modmailDetectedPorts, ports);
        }

        String matchedIp = null;
        for (String ip : localIpv4Addresses()) {
            List<Integer> ports = map.get(ip.toLowerCase(Locale.ROOT));
            if (ports != null && !ports.isEmpty()) {
                addUniquePorts(modmailDetectedPorts, ports);
                if (matchedIp == null) {
                    matchedIp = ip;
                }
            }
        }

        if (!modmailDetectedPorts.isEmpty()) {
            String kind = "10.0.0.101".equals(matchedIp) ? "laptop"
                    : "10.0.0.216".equals(matchedIp) ? "desktop"
                    : (hostname != null && hostname.equalsIgnoreCase("BCGAMINGPC")) ? "desktop"
                    : "this-host";
            String who = matchedIp != null ? matchedIp : hostname;
            modmailHostDescription = kind + " (" + who + " → " + joinPorts(modmailDetectedPorts) + ")";
        } else {
            List<String> ips = localIpv4Addresses();
            modmailHostDescription = "no LAN match (hostname=" + hostname + ", ips=" + ips + ")";
        }
        return modmailDetectedPorts;
    }

    private static void addUniquePorts(List<Integer> into, List<Integer> ports) {
        if (ports == null) return;
        for (int port : ports) {
            if (!into.contains(port)) {
                into.add(port);
            }
        }
    }

    private static String joinPorts(List<Integer> ports) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < ports.size(); i++) {
            if (i > 0) sb.append(", ");
            sb.append(ports.get(i));
        }
        return sb.toString();
    }

    private static Map<String, List<Integer>> parseHostMap(String raw) {
        Map<String, List<Integer>> map = new LinkedHashMap<>();
        if (raw == null || raw.isBlank()) {
            return map;
        }
        for (String part : raw.split("[,\\s]+")) {
            if (part.isBlank() || !part.contains(":")) continue;
            int split = part.lastIndexOf(':');
            String host = part.substring(0, split).trim().toLowerCase(Locale.ROOT);
            String portStr = part.substring(split + 1).trim();
            if (host.isBlank()) continue;
            try {
                int port = Integer.parseInt(portStr);
                if (port >= 1 && port <= 65535) {
                    map.computeIfAbsent(host, k -> new ArrayList<>());
                    List<Integer> ports = map.get(host);
                    if (!ports.contains(port)) {
                        ports.add(port);
                    }
                }
            } catch (NumberFormatException ignored) {
                // skip bad tokens
            }
        }
        return map;
    }

    private static List<String> localIpv4Addresses() {
        List<String> ips = new ArrayList<>();
        try {
            Enumeration<NetworkInterface> ifaces = NetworkInterface.getNetworkInterfaces();
            while (ifaces.hasMoreElements()) {
                NetworkInterface nif = ifaces.nextElement();
                try {
                    if (!nif.isUp() || nif.isLoopback()) continue;
                } catch (Exception ignored) {
                    continue;
                }
                Enumeration<InetAddress> addrs = nif.getInetAddresses();
                while (addrs.hasMoreElements()) {
                    InetAddress addr = addrs.nextElement();
                    if (addr instanceof Inet4Address
                            && !addr.isLoopbackAddress()
                            && !addr.isMulticastAddress()
                            && !addr.isLinkLocalAddress()) {
                        ips.add(addr.getHostAddress());
                    }
                }
            }
        } catch (Exception ignored) {
            // no interfaces; host map will not match
        }
        return ips;
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

    public String getEventNameChannelId() {
        return EVENT_NAME_CHANNEL;
    }

    public String getModLogChannelId() {
        return MOD_LOG_CHANNEL_ID;
    }

    public String getAnnouncementsChannelId() {
        return ANNOUNCEMENTS_CHANNEL_ID;
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
