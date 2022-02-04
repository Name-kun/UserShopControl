package xyz.masa3mc.usershopcontrol;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.bukkit.WorldGuardPlugin;
import com.sk89q.worldguard.domains.DefaultDomain;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import com.sk89q.worldguard.protection.regions.RegionContainer;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import net.milkbowl.vault.economy.Economy;
import net.raidstone.wgevents.events.RegionEnteredEvent;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.Sign;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffectType;

import java.util.HashMap;
import java.util.Objects;
import java.util.regex.Pattern;

public final class UserShopControl extends JavaPlugin implements Listener, CommandExecutor {

    private static Economy econ = null;

    @Override
    public void onEnable() {
        if (!setupEconomy()) {
            getLogger().info("§c経済プラグインが導入されていないため無効化しました。");
            getServer().getPluginManager().disablePlugin(this);
            getCommand("shopowner").setExecutor(this);
            return;
        }
        getServer().getPluginManager().registerEvents(this, this);
    }

    private boolean setupEconomy() {
        if (getServer().getPluginManager().getPlugin("Vault") == null) {
            return false;
        }
        RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp == null) {
            return false;
        }
        econ = rsp.getProvider();
        return econ != null;
    }

    public static Economy getEconomy() {
        return econ;
    }

    private final HashMap<Player, Boolean> checkConfirm = new HashMap<>();
    private final HashMap<Player, Block> clickedBlock = new HashMap<>();

    @EventHandler
    public void onSignClick(PlayerInteractEvent e) {
        if (e.getPlayer().getWorld().getName().equalsIgnoreCase("shop")) {
            if (e.getClickedBlock() != null && e.getClickedBlock().getType().equals(Material.OAK_SIGN) && e.getAction().equals(Action.RIGHT_CLICK_BLOCK)) {
                Sign sign = (Sign) e.getClickedBlock().getState();
                RegionContainer container = WorldGuard.getInstance().getPlatform().getRegionContainer();
                RegionManager regions = container.get(BukkitAdapter.adapt(Objects.requireNonNull(getServer().getWorld("shop"))));
                //shopワールドに看板の一行目と同名の保護がある場合
                if (regions.hasRegion(sign.getLine(0))) {
                    ProtectedRegion region = regions.getRegion(sign.getLine(0).toLowerCase());
                    Pattern pattern = Pattern.compile("^[a-z].*[0-9]$");
                    if (pattern.matcher(region.getId()).matches()) {
                        //オーナーまたはメンバーが存在する場合
                        if (region.hasMembersOrOwners()) {
                            e.getPlayer().sendMessage("§cこの区画は既に購入されています。");
                        } else if (regions.getRegionCountOfPlayer(WorldGuardPlugin.inst().wrapPlayer(e.getPlayer())) >= 2) { //既に２区画所有している場合
                            e.getPlayer().sendMessage("§c3区画以上は購入できません。");
                        } else {
                            //初めてクリックしたとき
                            if (checkConfirm.get(e.getPlayer()) == null || !checkConfirm.get(e.getPlayer())) {
                                checkConfirm.put(e.getPlayer(), true);
                                clickedBlock.put(e.getPlayer(), e.getClickedBlock());
                                TextComponent message = new TextComponent();
                                message.setText("§e§l看板を再度クリックして購入");
                                e.getPlayer().spigot().sendMessage(ChatMessageType.ACTION_BAR, message);
                            } else {
                                Pattern outside = Pattern.compile("^[efghijklmnop].*[0-9]$");
                                Pattern inside = Pattern.compile("^[abcd].*[0-9]$");
                                Economy economy = getEconomy();
                                //外側区画の保護名と一致する場合
                                if (outside.matcher(region.getId()).matches()) {
                                    if (economy.getBalance(e.getPlayer()) >= 50000) {
                                        regionPurchase(e.getPlayer(), region, 50000);
                                    } else {
                                        e.getPlayer().sendMessage("§c必要金額は50000円ですが、所持金額が足りません。");
                                        checkConfirm.put(e.getPlayer(), false);
                                    }
                                } else if (inside.matcher(region.getId()).matches()) { //内側区画の保護名と一致する場合
                                    if (economy.getBalance(e.getPlayer()) >= 100000) {
                                        regionPurchase(e.getPlayer(), region, 100000);
                                    } else {
                                        e.getPlayer().sendMessage("§c必要金額は100000円ですが、所持金額が足りません。");
                                        checkConfirm.put(e.getPlayer(), false);
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private void regionPurchase(Player p, ProtectedRegion region, int cost) {
        Economy economy = getEconomy();
        economy.withdrawPlayer(p, cost);
        DefaultDomain defaultDomain = region.getOwners();
        defaultDomain.addPlayer(WorldGuardPlugin.inst().wrapPlayer(p));
        region.setOwners(defaultDomain);
        p.sendMessage("§6" + region.getId() + "§aを購入しました！");
        checkConfirm.put(p, false);
    }

    @EventHandler
    public void onMove(PlayerMoveEvent e) {
        if (checkConfirm.get(e.getPlayer()) != null && checkConfirm.get(e.getPlayer())) {
            if (clickedBlock.get(e.getPlayer()) != null) {
                if (e.getPlayer().getLocation().distance(clickedBlock.get(e.getPlayer()).getLocation()) > 7) {
                    checkConfirm.put(e.getPlayer(), false);
                    e.getPlayer().sendMessage("§c購入をキャンセルしました。");
                }
            }
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        if (checkConfirm.get(e.getPlayer()) != null && checkConfirm.get(e.getPlayer())) {
            checkConfirm.put(e.getPlayer(), false);
        }
    }

    //全く関係ないやつ
    @EventHandler
    public void onRegionEnter(RegionEnteredEvent e) {
        if (e.getPlayer().getWorld().getName().equals("resource") && e.getRegionName().equals("re_athletic")) {
            if (e.getPlayer().getInventory().getChestplate() != null && e.getPlayer().getInventory().getChestplate().getType().equals(Material.ELYTRA)) {
                ConsoleCommandSender console = Bukkit.getConsoleSender();
                Bukkit.dispatchCommand(console, "warp " + e.getPlayer().getName() + " re_athletic");
                e.getPlayer().sendMessage("エリトラを着用しているため入れません。");
            } else {
                if (e.getPlayer().getAllowFlight()) e.getPlayer().setAllowFlight(false);
                if (e.getPlayer().hasPotionEffect(PotionEffectType.JUMP))
                    e.getPlayer().removePotionEffect(PotionEffectType.JUMP);
                e.getPlayer().sendMessage("アスレチックを開始しました。");
            }
        }
    }
}
