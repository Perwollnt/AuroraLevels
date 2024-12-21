package gg.auroramc.levels.leveler;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.RemovalNotification;
import gg.auroramc.aurora.api.AuroraAPI;
import gg.auroramc.aurora.api.message.Placeholder;
import gg.auroramc.aurora.api.message.Text;
import gg.auroramc.aurora.api.user.AuroraUser;
import gg.auroramc.levels.AuroraLevels;
import gg.auroramc.levels.config.LevelConfig;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

public class CumulativeXPManager {

    private final Cache<UUID, Double> cumulativeXpCache;
    private final AuroraLevels plugin;

    public CumulativeXPManager(long timeoutSeconds, AuroraLevels plugin) {
        this.cumulativeXpCache = CacheBuilder.newBuilder()
                .expireAfterAccess(timeoutSeconds, TimeUnit.SECONDS)
                .removalListener(this::cacheLeave)
                .build();
        this.plugin = plugin;
    }

    private void cacheLeave(RemovalNotification<UUID, Double> notification) {
        AuroraUser user = AuroraAPI.getUser(notification.getKey());
        Player player = user.getPlayer();

        LevelConfig.XpGainMessage xpGainMessage = plugin.getConfigManager().getLevelConfig().getXpGainMessage();

        // if the level up message is enabled and batching is also enabled, we send the message here on cache leave.
        if(xpGainMessage.getEnabled() && xpGainMessage.getBatched()) {
            double xp = notification.getValue() == null ? 0 : notification.getValue();

            List<Placeholder<?>> placeholderList = new ArrayList<>();
            placeholderList.add(Placeholder.of("{camount}", AuroraAPI.formatNumber(xp)));

            int count = 0;
            var text = Component.text();
            for(String line : xpGainMessage.getMessage()) {
                count++;
                text.append(Text.component(player, line, placeholderList));
                if(count != xpGainMessage.getMessage().size()) text.append(Component.newline());
            }

            player.sendMessage(text);
        }
    }

    public void addCumulativeXP(UUID uuid, double xp) {
        cumulativeXpCache.put(uuid, getCumulativeXP(uuid) + xp);
    }

    public double getCumulativeXP(UUID uuid) {
        Double cumulativeXP = cumulativeXpCache.getIfPresent(uuid);
        return cumulativeXP == null ? 0 : cumulativeXP;
    }
}


/**
 * // if the message is enabled and IS NOT batched, we send it, if its batched we handle it on cache leave becase thats easy.
 *         if(plugin.getConfigManager().getLevelConfig().getXpGainMessage().getEnabled() && !plugin.getConfigManager().getLevelConfig().getXpGainMessage().getBatched()) {
 *
 *             List<String> xpGainMessage = plugin.getConfigManager().getLevelConfig().getXpGainMessage().getMessage();
 *             List<Placeholder<?>> placeholders = new ArrayList<>();
 *             placeholders.add(Placeholder.of("{amount}", AuroraAPI.formatNumber(xp)));
 *             placeholders.add(Placeholder.of("{camount}", AuroraAPI.formatNumber(cumulativeXPManager.getCumulativeXP(player.getUniqueId()))));
 *
 *             int count = 0;
 *             var text = Component.text();
 *             for(String line : xpGainMessage) {
 *                 count++;
 *                 text.append(Text.component(player, line, placeholders));
 *                 if(count != xpGainMessage.size()) text.append(Component.newline());
 *             }
 *
 *             Chat.sendMessage(player, text.build());
 *         }
 */