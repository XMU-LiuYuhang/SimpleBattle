package fun.johand.simpleBattle;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.model.user.User;
import net.luckperms.api.node.NodeType;
import net.luckperms.api.node.types.InheritanceNode;
import org.bukkit.*;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;
import org.jspecify.annotations.NonNull;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;

import java.util.*;

public final class SimpleBattle extends JavaPlugin implements Listener, TabCompleter {
    private LuckPerms luckPerms;
    private Map<UUID, Integer> personalKills = new HashMap<>();
    private Map<String, Integer> teamKills = new HashMap<>();
    private final String GROUP_A = "bage";
    private final String GROUP_B = "sumu";

    @Override
    public void onEnable() {
        // 初始化Luckperms
        RegisteredServiceProvider<LuckPerms> provider = Bukkit.getServicesManager().getRegistration(LuckPerms.class);
        Plugin plugin = Bukkit.getPluginManager().getPlugin("LifeStealZ");
        if (provider != null) this.luckPerms = provider.getProvider();
        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            new GameExpansion(this).register();
        }
        getCommand("SimpleBattle").setExecutor(this);
        getCommand("SimpleBattle").setTabCompleter(this);
        Bukkit.getPluginManager().registerEvents(this, this);
    }

    @Override
    public void onDisable() {
        // Plugin shutdown logic
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args){
        if (args.length == 0) return false;
        World world = Bukkit.getWorlds().getFirst();

        if (args[0].equalsIgnoreCase("prepare")){
            prepareGame(world);
            sender.sendMessage("游戏准备完毕：玩家已传送，边界已缩小，队伍已分配");
        }
        else if(args[0].equalsIgnoreCase("start")){
            startGame(world);
            sender.sendMessage("游戏开始！世界边界已扩大，PVP将在1分钟后开启");
        }
        return true;
    }

    //游戏准备函数
    private void prepareGame(World world) {
        int countA = 0;
        int countB = 0;
        List<Player> players = new ArrayList<>(Bukkit.getOnlinePlayers());
        Collections.shuffle(players);
        Location spawn = world.getSpawnLocation();
        world.setPVP(false);
        for (Player player : players) {
            String targetGroup;

            // ===== 平衡核心逻辑 =====
            if (countA <= countB) {
                targetGroup = GROUP_A;
                countA++;
            } else {
                targetGroup = GROUP_B;
                countB++;
            }
            // ===== LuckPerms 分组 =====
            setPlayerGroup(player, targetGroup);
            // 出生点附近随机 ±3 格
            double x = spawn.getX() + (Math.random() * 6 - 3);
            double z = spawn.getZ() + (Math.random() * 6 - 3);

            Location tpLoc = new Location(
                    world,
                    x,
                    spawn.getY(),
                    z,
                    player.getLocation().getYaw(),
                    player.getLocation().getPitch()
            );
            player.setGameMode(GameMode.SURVIVAL);
            AttributeInstance attr = player.getAttribute(Attribute.GENERIC_MAX_HEALTH);
            if (attr != null) {
                attr.setBaseValue(20.0);
            }
            // 回满血
            player.setHealth(20.0);
            // 回满饱食度
            player.setFoodLevel(20);
            // 回满饱和度（防止立刻掉饥饿）
            player.setSaturation(20.0f);

            personalKills.clear();
            personalKills.put(player.getUniqueId(), 0);
            player.teleport(tpLoc);
            player.sendMessage("§a你已被召集到出生点！");

        }
        world.getWorldBorder().setSize(10);
        teamKills.clear();
        teamKills.put(GROUP_A, 0);
        teamKills.put(GROUP_B, 0);
    }

    private void startGame(World world){
        world.getWorldBorder().setSize(500, 30);
        world.setPVP(true);
        Bukkit.broadcastMessage("PVP已开启！战斗开始！快跑！");
    }

    private void setPlayerGroup(Player player, String groupName){
        luckPerms.getUserManager().modifyUser(player.getUniqueId(), user -> {
            user.data().clear(NodeType.INHERITANCE::matches);
            user.data().add(InheritanceNode.builder(groupName).build());
        });
    }

    @EventHandler
    public void onKill(PlayerDeathEvent event){
        Player victim = event.getEntity();
        Player killer = victim.getKiller();

        if (killer != null && !killer.equals(victim)){
            String killerGroup = luckPerms.getUserManager().getUser(killer.getUniqueId()).getPrimaryGroup();
            String victimGroup = luckPerms.getUserManager().getUser(victim.getUniqueId()).getPrimaryGroup();
            //检查是否为同组
            if(!killerGroup.equals(victimGroup)){
                personalKills.put(killer.getUniqueId(), personalKills.getOrDefault(killer.getUniqueId(), 0) + 1);
                teamKills.put(killerGroup, teamKills.getOrDefault(killerGroup, 0) + 1);
                killer.sendMessage("击杀数：" + getPersonalKills(killer.getUniqueId()));
            }
        }
    }

    public int getPersonalKills(UUID uuid) { return personalKills.getOrDefault(uuid, 0); }
    public int getTeamKills(UUID uuid) {
        User user = luckPerms.getUserManager().getUser(uuid);
        if (user == null) return 0;
        String group = user.getPrimaryGroup();
        return teamKills.getOrDefault(group, 0);
    }

    @Override
    public List<String> onTabComplete (CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return List.of("prepare", "start");
        }
        return Collections.emptyList();
    }

    @EventHandler
    public void onPortalUse(PlayerPortalEvent event) {

        World.Environment target = event.getTo().getWorld().getEnvironment();

        if (target == World.Environment.NETHER || target == World.Environment.THE_END) {
            event.setCancelled(true);
            event.getPlayer().sendMessage("§c当前比赛禁止进入其他维度！");
        }
    }

}
