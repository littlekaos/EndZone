package EndZone.services;

import EndZone.config.BotConfig;
import EndZone.repositories.SQLiteEventNameRepository;
import net.dv8tion.jda.api.JDA;

public class ServiceManager {
    private static JDA jda;
    private static BotConfig config;
    private static StrikeService strikeService;
    private static DemotionService demotionService;
    private static AppealService appealService;
    private static LoggingService loggingService;
    private static AppealScannerService appealScannerService;
    private static RoleRestorationService roleRestorationService;
    private static DataService dataService;
    private static VoiceChannelManager voiceChannelManager;
    private static SQLiteEventNameRepository eventNameRepository;
    private static BanService banService;
    private static MuteService muteService;
    private static DemotionSyncService demotionSyncService;
    private static EventsSetupManager eventsSetupManager;
    private static UserCache userCache;
    private static MessageCache messageCache;
    private static VoiceChannelService voiceChannelService;
    private static VoidCheckerService voidCheckerService;
    private static RestrictionService restrictionService;
    private static StrikeScannerService strikeScannerService;
    private static BlacklistService blacklistService;
    private static AFKService afkService;
    private static BanSyncService banSyncService;

    public static void setJda(JDA jdaInstance) { jda = jdaInstance; }
    public static void setConfig(BotConfig configInstance) { config = configInstance; }
    public static void setStrikeService(StrikeService service) { strikeService = service; }
    public static void setDemotionService(DemotionService service) { demotionService = service; }
    public static void setAppealService(AppealService service) { appealService = service; }
    public static void setLoggingService(LoggingService service) { loggingService = service; }
    public static void setAppealScannerService(AppealScannerService service) { appealScannerService = service; }
    public static void setRoleRestorationService(RoleRestorationService service) { roleRestorationService = service; }
    public static void setDataService(DataService service) { dataService = service; }
    public static void setVoiceChannelManager(VoiceChannelManager service) { voiceChannelManager = service; }
    public static void setEventNameRepository(SQLiteEventNameRepository repository) { eventNameRepository = repository; }
    public static void setBanService(BanService service) { banService = service; }
    public static void setMuteService(MuteService service) { muteService = service; }
    public static void setDemotionSyncService(DemotionSyncService service) { demotionSyncService = service; }
    public static void setEventsSetupManager(EventsSetupManager service) { eventsSetupManager = service; }
    public static void setUserCache(UserCache service) { userCache = service; }
    public static void setMessageCache(MessageCache service) { messageCache = service; }
    public static void setVoiceChannelService(VoiceChannelService service) { voiceChannelService = service; }
    public static void setVoidCheckerService(VoidCheckerService service) { voidCheckerService = service; }
    public static void setRestrictionService(RestrictionService service) { restrictionService = service; }
    public static void setStrikeScannerService(StrikeScannerService service) { strikeScannerService = service; }
    public static void setBlacklistService(BlacklistService service) { blacklistService = service; }
    public static void setAFKService(AFKService service) { afkService = service; }
    public static void setBanSyncService(BanSyncService service) { banSyncService = service; }

    public static JDA getJda() { return jda; }
    public static BotConfig getConfig() { return config; }
    public static StrikeService getStrikeService() { return strikeService; }
    public static DemotionService getDemotionService() { return demotionService; }
    public static AppealService getAppealService() { return appealService; }
    public static LoggingService getLoggingService() { return loggingService; }
    public static AppealScannerService getAppealScannerService() { return appealScannerService; }
    public static RoleRestorationService getRoleRestorationService() { return roleRestorationService; }
    public static DataService getDataService() { return dataService; }
    public static VoiceChannelManager getVoiceChannelManager() { return voiceChannelManager; }
    public static SQLiteEventNameRepository getEventNameRepository() { return eventNameRepository; }
    public static BanService getBanService() { return banService; }
    public static MuteService getMuteService() { return muteService; }
    public static DemotionSyncService getDemotionSyncService() { return demotionSyncService; }
    public static EventsSetupManager getEventsSetupManager() { return eventsSetupManager; }
    public static UserCache getUserCache() { return userCache; }
    public static MessageCache getMessageCache() { return messageCache; }
    public static BotConfig getBotConfig() { return config; }
    public static VoiceChannelService getVoiceChannelService() { return voiceChannelService; }
    public static VoidCheckerService getVoidCheckerService() { return voidCheckerService; }
    public static RestrictionService getRestrictionService() { return restrictionService; }
    public static StrikeScannerService getStrikeScannerService() { return strikeScannerService; }
    public static BlacklistService getBlacklistService() { return blacklistService; }
    public static AFKService getAFKService() { return afkService; }
    public static BanSyncService getBanSyncService() { return banSyncService; }
}
