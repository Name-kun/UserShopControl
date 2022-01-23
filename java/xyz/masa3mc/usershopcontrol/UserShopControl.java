package xyz.masa3mc.usershopcontrol;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.bukkit.WorldGuardPlugin;
import com.sk89q.worldguard.domains.DefaultDomain;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import com.sk89q.worldguard.protection.regions.RegionContainer;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Material;
import org.bukkit.block.Sign;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Objects;
import java.util.regex.Pattern;

public final class UserShopControl extends JavaPlugin implements Listener {

    private static Economy econ = null;

    @Override
    public void onEnable() {
        if (!setupEconomy()) {
            getLogger().info("§c経済プラグインが導入されていないため無効化しました。");
            getServer().getPluginManager().disablePlugin(this);
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
                            Pattern outside = Pattern.compile("^[efghijklmnop].*[0-9]$");
                            Pattern inside = Pattern.compile("^[abcd].*[0-9]$");
                            Economy economy = getEconomy();
                            //外側区画の保護名と一致する場合
                            if (outside.matcher(region.getId()).matches()) {
                                if (economy.getBalance(e.getPlayer()) > 50000) {
                                    economy.withdrawPlayer(e.getPlayer(), 50000);
                                    DefaultDomain defaultDomain = region.getOwners();
                                    defaultDomain.addPlayer(WorldGuardPlugin.inst().wrapPlayer(e.getPlayer()));
                                    region.setOwners(defaultDomain);
                                    e.getPlayer().sendMessage("§6" + region.getId() + "§aを購入しました！");
                                } else {
                                    e.getPlayer().sendMessage("§c必要金額は50000円ですが、所持金額が足りません。");
                                }
                            } else if (inside.matcher(region.getId()).matches()) { //内側区画の保護名と一致する場合
                                if (economy.getBalance(e.getPlayer()) > 100000) {
                                    economy.withdrawPlayer(e.getPlayer(), 100000);
                                    DefaultDomain defaultDomain = region.getOwners();
                                    defaultDomain.addPlayer(WorldGuardPlugin.inst().wrapPlayer(e.getPlayer()));
                                    region.setOwners(defaultDomain);
                                    e.getPlayer().sendMessage("§6" + region.getId() + "§aを購入しました！");
                                } else {
                                    e.getPlayer().sendMessage("§c必要金額は100000円ですが、所持金額が足りません。");
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
