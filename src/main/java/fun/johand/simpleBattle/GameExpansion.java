package fun.johand.simpleBattle;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.entity.Player;
import org.jspecify.annotations.NonNull;

public class GameExpansion extends PlaceholderExpansion {
    private final SimpleBattle plugin;

    public GameExpansion(SimpleBattle plugin) { this.plugin = plugin; }

    @Override
    public @NonNull String getIdentifier() { return "game"; }
    @Override
    public @NonNull String getAuthor() { return "Author"; }
    @Override
    public @NonNull String getVersion() { return "1.0"; }
    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onPlaceholderRequest(Player player, String params) {
        if (player == null) return "";

        if (params.equalsIgnoreCase("personal_kills")) {
            return String.valueOf(plugin.getPersonalKills(player.getUniqueId()));
        }
        if (params.equalsIgnoreCase("team_kills")) {
            return String.valueOf(plugin.getTeamKills(player.getUniqueId()));
        }
        return null;
    }
}
