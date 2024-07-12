package gg.auroramc.levels.menu;

import gg.auroramc.aurora.api.AuroraAPI;
import gg.auroramc.aurora.api.config.premade.ItemConfig;
import gg.auroramc.aurora.api.levels.ConcreteMatcher;
import gg.auroramc.aurora.api.levels.IntervalMatcher;
import gg.auroramc.aurora.api.menu.AuroraMenu;
import gg.auroramc.aurora.api.menu.ItemBuilder;
import gg.auroramc.aurora.api.message.Placeholder;
import gg.auroramc.aurora.api.message.Text;
import gg.auroramc.aurora.api.util.NamespacedId;
import gg.auroramc.levels.AuroraLevels;
import lombok.Getter;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class LevelMenu {
    @Getter
    private final static NamespacedId menuId = NamespacedId.fromDefault("level_menu");

    private final Player player;
    private final AuroraLevels plugin;

    public LevelMenu(AuroraLevels plugin, Player player) {
        this.plugin = plugin;
        this.player = player;
    }

    public void open() {
        createMenu().open();
    }

    private AuroraMenu createMenu() {
        var leveler = plugin.getLeveler();
        var cfg = plugin.getConfigManager();
        var menuConfig = cfg.getLevelMenuConfig();

        var menu = new AuroraMenu(player, menuConfig.getTitle(), 54, false, menuId);

        if (menuConfig.getItems().getFiller().getEnabled()) {
            menu.addFiller(ItemBuilder.of(menuConfig.getItems().getFiller().getItem()).slot(0).build(player).getItemStack());
        } else {
            menu.addFiller(ItemBuilder.filler(Material.AIR));
        }

        int level = leveler.getUserData(player).getLevel();
        var lbm = AuroraAPI.getLeaderboards();

        var lb = AuroraAPI.getUser(player.getUniqueId()).getLeaderboardEntries().get("levels");
        var lbPositionPlaceholder = Placeholder.of("{lb_position}", lb == null ? lbm.getEmptyPlaceholder() : AuroraAPI.formatNumber(lb.getPosition()));
        var lbPositionPercentPlaceholder = Placeholder.of("{lb_position_percent}",
                lb == null ? lbm.getEmptyPlaceholder() : AuroraAPI.formatNumber(
                        Math.min(((double) lb.getPosition() / Math.max(1, AuroraAPI.getLeaderboards().getBoardSize("levels"))) * 100, 100)
                )
        );
        var lbBoardSizePlaceholder = Placeholder.of("{lb_size}", AuroraAPI.formatNumber(AuroraAPI.getLeaderboards().getBoardSize("levels")));
        var totalCurrentXP = leveler.getXpForLevel(leveler.getUserData(player).getLevel()) + leveler.getUserData(player).getCurrentXP();

        for (var customItem : menuConfig.getCustomItems().values()) {
            menu.addItem(ItemBuilder.of(customItem)
                    .placeholder(Placeholder.of("{level}", level))
                    .placeholder(lbPositionPlaceholder)
                    .placeholder(lbPositionPercentPlaceholder)
                    .placeholder(lbBoardSizePlaceholder)
                    .placeholder(Placeholder.of("{current}", AuroraAPI.formatNumber(totalCurrentXP)))
                    .placeholder(Placeholder.of("{current_short}", AuroraAPI.formatNumberShort(totalCurrentXP)))
                    .build(player));
        }

        int iteratorLevel = level;

        for (var slot : menuConfig.getLevelTrack()) {
            var matcher = leveler.getLevelMatcher().getBestMatcher(iteratorLevel);
            var rewards = matcher.computeRewards(iteratorLevel);

            Map<String, ItemConfig> overrideItems = Map.of();

            if (matcher instanceof IntervalMatcher intervalMatcher) {
                overrideItems = intervalMatcher.getConfig().getItem();
            } else if (matcher instanceof ConcreteMatcher concreteMatcher) {
                overrideItems = concreteMatcher.getConfig().getItem();
            }

            ItemConfig itemConfig;
            Material defaultMaterial;

            if (iteratorLevel == level) {
                itemConfig = menuConfig.getItems().getCompletedLevel().merge(overrideItems.get("completed-level"));
                defaultMaterial = Material.LIME_STAINED_GLASS_PANE;
            } else if (iteratorLevel - 1 == level) {
                itemConfig = menuConfig.getItems().getNextLevel().merge(overrideItems.get("next-level"));
                defaultMaterial = Material.YELLOW_STAINED_GLASS_PANE;
            } else {
                itemConfig = menuConfig.getItems().getLockedLevel().merge(overrideItems.get("locked-level"));
                defaultMaterial = Material.RED_STAINED_GLASS_PANE;
            }

            List<Placeholder<?>> placeholders = leveler.getRewardFormulaPlaceholders(player, iteratorLevel);
            placeholders.add(lbPositionPlaceholder);
            placeholders.add(lbPositionPercentPlaceholder);
            placeholders.add(lbBoardSizePlaceholder);

            if (iteratorLevel - 1 == level) {
                var currentXP = leveler.getUserData(player).getCurrentXP();
                var requiredXP = leveler.getRequiredXpForLevelUp(player);
                placeholders.add(Placeholder.of("{current}", AuroraAPI.formatNumber(((Double) currentXP).longValue())));
                placeholders.add(Placeholder.of("{current_short}", AuroraAPI.formatNumberShort(currentXP)));
                placeholders.add(Placeholder.of("{required}", AuroraAPI.formatNumber(((Double) requiredXP).longValue())));
                placeholders.add(Placeholder.of("{required_short}", AuroraAPI.formatNumberShort(requiredXP)));

                var bar = menuConfig.getProgressBar();
                var pcs = bar.getLength();
                var completedPercent = currentXP / requiredXP;
                var completedPcs = ((Double) Math.floor(pcs * completedPercent)).intValue();
                var remainingPcs = pcs - completedPcs;
                placeholders.add(Placeholder.of("{progressbar}", bar.getFilledCharacter().repeat(completedPcs) + bar.getUnfilledCharacter().repeat(remainingPcs) + "&r"));
            }

            var lore = new ArrayList<String>();


            for (var line : itemConfig.getLore()) {
                if (line.equals("component:rewards")) {
                    var display = menuConfig.getDisplayComponents().get("rewards");
                    if (!rewards.isEmpty()) {
                        lore.add(display.getTitle());
                    }
                    for (var reward : rewards) {
                        lore.add(display.getLine().replace("{reward}", reward.getDisplay(player, placeholders)));
                    }
                } else {
                    lore.add(line);
                }
            }

            var item = ItemBuilder.of(itemConfig).defaultMaterial(defaultMaterial).slot(slot)
                    .placeholder(placeholders)
                    .loreCompute(() -> lore.stream().map(l -> Text.component(player, l, placeholders)).toList())
                    .build(player);

            menu.addItem(item);

            iteratorLevel++;
        }

        return menu;
    }
}
