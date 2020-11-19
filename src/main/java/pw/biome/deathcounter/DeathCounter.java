package pw.biome.deathcounter;

import net.md_5.bungee.api.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.plugin.java.JavaPlugin;
import pw.biome.biomechat.BiomeChat;
import pw.biome.biomechat.obj.Corp;
import pw.biome.biomechat.obj.ScoreboardHook;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class DeathCounter extends JavaPlugin implements ScoreboardHook, Listener {

    private final Map<UUID, Integer> deathMap = new HashMap<>();

    int taskId;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        getServer().getPluginManager().registerEvents(this, this);
        setupScoreboardHook();
    }

    private void setupScoreboardHook() {
        BiomeChat biomeChat = BiomeChat.getPlugin();

        // Stop all hooks
        biomeChat.getScoreboardHookList().forEach(ScoreboardHook::stopScoreboardTask);
        biomeChat.stopScoreboardTask();

        // Then register ours
        biomeChat.registerHook(this);

        // Run our scoreboard task
        restartScoreboardTask();
    }

    @Override
    public void onDisable() {
        saveConfig();
    }

    @Override
    public void restartScoreboardTask() {
        if (taskId == 0) {
            taskId = getServer().getScheduler().runTaskTimerAsynchronously(this, this::updateScoreboards, 10, 20).getTaskId();
        }
    }

    @Override
    public void stopScoreboardTask() {
        if (taskId != 0) {
            getServer().getScheduler().cancelTask(taskId);
        }
    }

    private void updateScoreboards() {
        for (Player player : getServer().getOnlinePlayers()) {
            player.setPlayerListHeader(ChatColor.BLUE + "Biome");

            Corp corp = Corp.getCorpForUser(player.getUniqueId());
            ChatColor prefix = corp.getPrefix();
            boolean afk = BiomeChat.getPlugin().isAFK(player);
            int deaths = deathMap.getOrDefault(player.getUniqueId(), 0);

            if (afk) {
                player.setPlayerListName(ChatColor.GRAY + player.getDisplayName() + ChatColor.GRAY + " | " + ChatColor.YELLOW + deaths);
            } else {
                player.setPlayerListName(prefix + player.getDisplayName() + ChatColor.GRAY + " | " + ChatColor.YELLOW + deaths);
            }
        }
    }

    @EventHandler
    public void asyncPlayerJoin(AsyncPlayerPreLoginEvent event) {
        UUID uuid = event.getUniqueId();

        // If they aren't in the map (which would mean, they have 0)
        if (!deathMap.containsKey(uuid)) {
            getServer().getScheduler().runTaskAsynchronously(this, () -> {
                int deaths = getConfig().getInt(uuid.toString());
                deathMap.put(uuid, deaths);

                // Save to config
                getConfig().set(uuid.toString(), deaths);
                saveConfig();
            });
        }
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        UUID uuid = event.getEntity().getUniqueId();

        // Run task async
        getServer().getScheduler().runTaskAsynchronously(this, () -> {
            int deaths = deathMap.getOrDefault(uuid, 0);
            deaths += 1;

            // Cache
            deathMap.put(uuid, deaths);

            // Save to config
            getConfig().set(uuid.toString(), deaths);
            saveConfig();
        });
    }
}
