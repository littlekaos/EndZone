package EndZone.services;

import EndZone.EndZone;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.stream.Collectors;

public class RestrictionService {
    private final DataService dataService;
    private final Set<DataService.RestrictionEntry> restrictions = new CopyOnWriteArraySet<>();

    public RestrictionService(DataService dataService) {
        this.dataService = dataService;
        loadRestrictions();
    }

    private void loadRestrictions() {
        restrictions.clear();
        restrictions.addAll(dataService.getAllChannelRestrictions());
    }

    public void addRestriction(String channelId, String type) {
        DataService.RestrictionEntry entry = new DataService.RestrictionEntry(channelId, type);
        restrictions.add(entry);
        dataService.addChannelRestriction(channelId, type);
    }

    public void removeRestriction(String channelId, String type) {
        DataService.RestrictionEntry entry = new DataService.RestrictionEntry(channelId, type);
        restrictions.remove(entry);
        dataService.removeChannelRestriction(channelId, type);
    }

    public Set<String> getMediaWithTextChannels() {
        return getChannelsByType("MEDIA_WITH_TEXT");
    }

    public Set<String> getMediaOnlyChannels() {
        return getChannelsByType("MEDIA_ONLY");
    }

    public Set<String> getScreenshotOnlyChannels() {
        return getChannelsByType("SCREENSHOT_ONLY");
    }

    public Set<String> getNoMessageChannels() {
        return getChannelsByType("NO_MESSAGE");
    }

    public Set<String> getTextOnlyChannels() {
        return getChannelsByType("TEXT_ONLY");
    }

    public Set<String> getNoMediaChannels() {
        return getChannelsByType("NO_MEDIA");
    }

    public Set<String> getNoContentChannels() {
        return getChannelsByType("NO_CONTENT");
    }

    private Set<String> getChannelsByType(String type) {
        return restrictions.stream()
                .filter(r -> r.type().equals(type))
                .map(DataService.RestrictionEntry::channelId)
                .collect(Collectors.toSet());
    }
}
