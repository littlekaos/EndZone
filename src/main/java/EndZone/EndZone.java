package EndZone;

import EndZone.config.BotConfig;
import EndZone.database.DatabaseService;
import EndZone.embeds.EmbedInitializer;
import EndZone.events.*;
import EndZone.forms.EndZoneForm;
import EndZone.listeners.EndZoneListener;
import EndZone.listeners.StaffPingListener;
import EndZone.schedulers.SchedulerManager;
import EndZone.repositories.SQLiteEventNameRepository;
import EndZone.services.*;
import io.github.cdimascio.dotenv.Dotenv;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.OnlineStatus;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.utils.ChunkingFilter;
import net.dv8tion.jda.api.utils.MemberCachePolicy;
import net.dv8tion.jda.api.utils.cache.CacheFlag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EndZone {
    private static final Logger logger = LoggerFactory.getLogger(EndZone.class);
    private static EndZone instance;

    public EndZone() {
        instance = this;
        ServiceManager.setConfig(new BotConfig());
    }

    public static void main(String[] args) throws Exception {
        new EndZone().run();
    }

    public void run() throws Exception {
        Dotenv dotenv = Dotenv.load();
        String databaseUrl = dotenv.get("DATABASE_PATH");
        if (databaseUrl == null || databaseUrl.isEmpty()) {
            databaseUrl = "jdbc:sqlite:endzone.db";
        }
        DatabaseService.initialize(databaseUrl);

        BotConfig config = ServiceManager.getConfig();

        ServiceManager.setLoggingService(new LoggingService());
        ServiceManager.setStrikeService(new StrikeService(config));
        ServiceManager.setDemotionService(new DemotionService(ServiceManager.getStrikeService(), config));
        ServiceManager.setAppealService(new AppealService());
        ServiceManager.setAppealScannerService(new AppealScannerService(ServiceManager.getAppealService(), ServiceManager.getStrikeService(), config));
        ServiceManager.setRoleRestorationService(new RoleRestorationService(ServiceManager.getStrikeService()));
        ServiceManager.setDataService(new DataService());
        ServiceManager.setVoiceChannelService(new VoiceChannelService(config));
        ServiceManager.setVoiceChannelManager(new VoiceChannelManager(ServiceManager.getVoiceChannelService()));
        ServiceManager.setEventNameRepository(new SQLiteEventNameRepository());
        ServiceManager.setBanService(new BanService());
        ServiceManager.setMuteService(new MuteService(ServiceManager.getDataService()));
        ServiceManager.setDemotionSyncService(new DemotionSyncService(ServiceManager.getDemotionService(), ServiceManager.getStrikeService().getDatabase(), config));
        ServiceManager.setEventsSetupManager(new EventsSetupManager(ServiceManager.getVoiceChannelService()));
        ServiceManager.setVoidCheckerService(new VoidCheckerService(ServiceManager.getEventNameRepository()));
        ServiceManager.setRestrictionService(new RestrictionService(ServiceManager.getDataService()));
        ServiceManager.setStrikeScannerService(new StrikeScannerService(ServiceManager.getStrikeService()));
        ServiceManager.setBlacklistService(new BlacklistService());
        ServiceManager.setAFKService(new AFKService());
        ServiceManager.setBanSyncService(new BanSyncService(ServiceManager.getDataService()));
        ServiceManager.setUserCache(new UserCache());
        ServiceManager.setMessageCache(new MessageCache());

        // Cross-inject dependencies
        ServiceManager.getStrikeService().setDemotionService(ServiceManager.getDemotionService());
        ServiceManager.getStrikeService().setRoleRestorationService(ServiceManager.getRoleRestorationService());
        ServiceManager.getAppealService().setDemotionService(ServiceManager.getDemotionService());
        ServiceManager.getAppealService().setRoleRestorationService(ServiceManager.getRoleRestorationService());
        ServiceManager.getDemotionService().setRoleRestorationService(ServiceManager.getRoleRestorationService());
        ServiceManager.getDataService().setUserCache(ServiceManager.getUserCache());

        String token = config.getToken();
        if (token == null || token.isEmpty()) {
            throw new IllegalStateException("Bot token not found in environment or .env file");
        }
        
        String botStatus = config.getStatusText();
        String activityUrl = config.getStatusUrl();
        OnlineStatus onlineStatus = config.getOnlineStatus();
        
        Activity activity = Activity.streaming(botStatus, activityUrl);

        JDA jda = JDABuilder.createDefault(token)
                .enableIntents(
                        GatewayIntent.GUILD_MEMBERS,
                        GatewayIntent.GUILD_MESSAGES,
                        GatewayIntent.MESSAGE_CONTENT,
                        GatewayIntent.GUILD_VOICE_STATES,
                        GatewayIntent.GUILD_MESSAGE_REACTIONS,
                        GatewayIntent.DIRECT_MESSAGES
                )
                .setMemberCachePolicy(MemberCachePolicy.ALL)
                .setChunkingFilter(ChunkingFilter.ALL)
                .enableCache(CacheFlag.VOICE_STATE, CacheFlag.ROLE_TAGS)
                .addEventListeners(
                        new EndZoneListener(), 
                        new CommandEventListener(this),
                        new ButtonEventListener(this),
                        new ModalEventListener(this),
                        new ReactionEventListener(this),
                        new SelectMenuEventListener(this),
                        new EventsSetupCommandListener(this),
                        new DemotionProtectionListener(ServiceManager.getDemotionService()),
                        new GuildEventListener(this),
                        new MessageEventListener(this),
                        new TicketEventListener(this),
                        new VoiceEventListener(ServiceManager.getVoiceChannelManager(), ServiceManager.getEventsSetupManager()),
                        new ServerLogEventListener(this),
                        new ReactionRoleListener(),
                        new StaffPingListener(),
                        ServiceManager.getRoleRestorationService()
                )
                .setActivity(activity)
                .setStatus(onlineStatus)
                .build();

        ServiceManager.setJda(jda);
        jda.awaitReady();

        ServiceManager.getStrikeService().setJda(jda);
        ServiceManager.getAppealService().setJda(jda);
        ServiceManager.getAppealScannerService().initialize(jda);
        ServiceManager.getUserCache().setJDA(jda);
        ServiceManager.getMuteService().setJda(jda);
        ServiceManager.getMuteService().start();
        ServiceManager.getDemotionSyncService().initialize(jda);
        ServiceManager.getBanSyncService().syncBans(jda);
        
        net.dv8tion.jda.api.entities.Guild guild = jda.getGuildById(config.getGuildId());
        if (guild != null) {
            ServiceManager.getLoggingService().initializeLogChannels(guild);
        }
        
        SchedulerManager.start(jda);
        CommandEventListener.registerCommands(jda, config, this);
        
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            logger.info("Bot shutting down...");
            EndZoneForm.exportAllFormStatesToDatabase();
            SchedulerManager.shutdown();
            DatabaseService.shutdown();
            jda.shutdown();
            logger.info("Bot shutdown complete");
        }));
        
        EmbedInitializer.initializeEmbeds(jda);
        logger.info("EndZone Bot is ready!");
    }

    public static EndZone getInstance() { return instance; }

    public JDA getJda() { return ServiceManager.getJda(); }
    public BotConfig getConfig() { return ServiceManager.getConfig(); }
    public StrikeService getStrikeService() { return ServiceManager.getStrikeService(); }
    public AppealService getAppealService() { return ServiceManager.getAppealService(); }
    public LoggingService getLoggingService() { return ServiceManager.getLoggingService(); }
    public DataService getDataService() { return ServiceManager.getDataService(); }
    public UserCache getUserCache() { return ServiceManager.getUserCache(); }
    public MessageCache getMessageCache() { return ServiceManager.getMessageCache(); }
    public VoiceChannelService getVoiceChannelService() { return ServiceManager.getVoiceChannelService(); }
    public VoiceChannelManager getVoiceChannelManager() { return ServiceManager.getVoiceChannelManager(); }
    public EventsSetupManager getEventsSetupManager() { return ServiceManager.getEventsSetupManager(); }
    public DemotionService getDemotionService() { return ServiceManager.getDemotionService(); }
    public BanService getBanService() { return ServiceManager.getBanService(); }
    public MuteService getMuteService() { return ServiceManager.getMuteService(); }
    public VoidCheckerService getVoidCheckerService() { return ServiceManager.getVoidCheckerService(); }
    public RestrictionService getRestrictionService() { return ServiceManager.getRestrictionService(); }
    public SQLiteEventNameRepository getEventNameRepository() { return ServiceManager.getEventNameRepository(); }
    public AppealScannerService getAppealScannerService() { return ServiceManager.getAppealScannerService(); }
    public RoleRestorationService getRoleRestorationService() { return ServiceManager.getRoleRestorationService(); }
    public DemotionSyncService getDemotionSyncService() { return ServiceManager.getDemotionSyncService(); }
}