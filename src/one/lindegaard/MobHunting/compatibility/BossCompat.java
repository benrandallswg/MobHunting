package one.lindegaard.MobHunting.compatibility;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntitySpawnEvent;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.plugin.Plugin;

import org.mineacademy.boss.api.*;
import one.lindegaard.Core.compatibility.CompatPlugin;
import one.lindegaard.MobHunting.MobHunting;
import one.lindegaard.MobHunting.StatType;
import one.lindegaard.MobHunting.mobs.ExtendedMobRewardData;
import one.lindegaard.MobHunting.mobs.MinecraftMob;

public class BossCompat implements Listener {

	// https://www.spigotmc.org/resources/%E2%98%9B-boss%E2%84%A2-custom-monsters-and-animals-on-your-server-1-8-9-1-14-4.46497/
	// https://github.com/kangarko/Boss

	private static boolean supported = false;
	private static Plugin mPlugin;
	private static HashMap<String, ExtendedMobRewardData> mMobRewardData = new HashMap<String, ExtendedMobRewardData>();
	public static final String MH_BOSSMOBS = "MH:Boss";
	private static File file = new File(MobHunting.getInstance().getDataFolder(), "boss-rewards.yml");
	private static YamlConfiguration config = new YamlConfiguration();

	public BossCompat() {
		if (!isEnabledInConfig()) {
			Bukkit.getConsoleSender().sendMessage(ChatColor.GOLD + "[MobHunting] " + ChatColor.RESET
					+ "Compatibility with Herobrine is disabled in config.yml");
		} else {
			mPlugin = Bukkit.getPluginManager().getPlugin(CompatPlugin.Herobrine.getName());

			Bukkit.getPluginManager().registerEvents(this, MobHunting.getInstance());

			Bukkit.getConsoleSender().sendMessage(ChatColor.GOLD + "[MobHunting] " + ChatColor.RESET
					+ "Enabling Compatibility with Herobrine (" + mPlugin.getDescription().getVersion() + ")");

			supported = true;
		}
	}

	// **************************************************************************
	// OTHER FUNCTIONS
	// **************************************************************************

	public static boolean isSupported() {
		return supported;
	}

	public static boolean isBossMob(Entity entity) {
		if (isSupported()) {
			return entity.hasMetadata(MH_BOSSMOBS) || BossAPI.isBoss(entity);
		}
		return false;
	}

	public static boolean isBossMob(String key) {
		if (isSupported()) {
			for (Boss i : BossAPI.getBosses()) {
				if (i.getName().replace(" ", "_").equalsIgnoreCase(key))
					return true;
			}
		}
		return false;
	}

	public static String getBossType(Entity entity) {
		return BossAPI.getBoss(entity).getName().replace(" ", "_");
	}

	public static HashMap<String, ExtendedMobRewardData> getMobRewardData() {
		return mMobRewardData;
	}

	public static boolean isEnabledInConfig() {
		return MobHunting.getInstance().getConfigManager().enableIntegrationBoss;
	}

	// **************************************************************************
	// EVENTS
	// **************************************************************************
	@EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
	private void onBossMobDeathEvent(EntityDeathEvent event) {
		Entity entity = event.getEntity();
		if (isBossMob(entity)) {
			entity.setMetadata(MH_BOSSMOBS, new FixedMetadataValue(MobHunting.getInstance(), true));
		}
	}

	@EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
	private void onBossMobSpawnEvent(EntitySpawnEvent event) {
		if (isSupported()) {
			Entity entity = event.getEntity();
			if (isBossMob(entity)) {
				MobHunting.getInstance().getMessages().debug("A Boss Mob (%s) was spawned at %s,%s,%s in %s",
						BossAPI.getBoss(entity).getName(), event.getEntity().getLocation().getBlockX(),
						event.getEntity().getLocation().getBlockY(), event.getEntity().getLocation().getBlockZ(),
						event.getEntity().getLocation().getWorld().getName());
				saveBossMobsData(BossAPI.getBoss(entity).getName().replace(" ", "_"));
				event.getEntity().setMetadata(MH_BOSSMOBS, new FixedMetadataValue(mPlugin, true));
			}
		}
	}

	public static int getProgressAchievementLevel1(String mobtype) {
		MinecraftMob mob = MinecraftMob.valueOf(mobtype);
		if (mob != null)
			return MobHunting.getInstance().getConfigManager().getProgressAchievementLevel1(mob);
		else
			return 100;
	}

	// **************************************************************************
	// LOAD & SAVE
	// **************************************************************************

	public static void loadBossMobsData() {
		try {
			if (!file.exists())
				return;
			MobHunting.getInstance().getMessages().debug("Loading extra MobRewards for Boss mobs.");

			config.load(file);
			int n = 0;
			for (String key : config.getKeys(false)) {
				ConfigurationSection section = config.getConfigurationSection(key);
				if (isBossMob(key)) {
					ExtendedMobRewardData mob = new ExtendedMobRewardData();
					mob.read(section);
					mob.setMobType(key);
					mMobRewardData.put(key, mob);
					MobHunting.getInstance().getStoreManager().insertMissingHerobrineMobs(key);
					n++;
				} else {
					MobHunting.getInstance().getMessages().debug("The mob=%s can't be found in Boss configuration file",
							key);
				}
			}
			MobHunting.getInstance().getMessages().injectMissingMobNamesToLangFiles();
			MobHunting.getInstance().getMessages().debug("Loaded %s Boss mobs", n);
		} catch (IOException e) {
			e.printStackTrace();
		} catch (InvalidConfigurationException e) {
			e.printStackTrace();
		}

	}

	public static void loadHerobrineMobsData(String key) {
		//key = key.replace(" ", "_");
		try {
			if (!file.exists())
				return;

			config.load(file);
			ConfigurationSection section = config.getConfigurationSection(key);
			if (isBossMob(key)) {
				ExtendedMobRewardData mob = new ExtendedMobRewardData();
				mob.read(section);
				mob.setMobType(key);
				mMobRewardData.put(key, mob);
				int n = StatType.values().length;
				StatType.values()[n + 1] = new StatType(mob.getMobType() + "_kill", mob.getMobName());
				StatType.values()[n + 2] = new StatType(mob.getMobType() + "_assist", mob.getMobName());
				MobHunting.getInstance().getStoreManager().insertMissingHerobrineMobs(key);
			} else {
				MobHunting.getInstance().getMessages().debug("The mob=%s can't be found in Boss mob configuration file",
						key);
			}
		} catch (IOException e) {
			e.printStackTrace();
		} catch (InvalidConfigurationException e) {
			e.printStackTrace();
		}
	}

	public static void saveBossMobsData() {
		try {
			config.options().header("This a extra MobHunting config data for Boss mobs on your server.");

			if (mMobRewardData.size() > 0) {

				int n = 0;
				for (String str : mMobRewardData.keySet()) {
					ConfigurationSection section = config.createSection(str);
					mMobRewardData.get(str).save(section);
					n++;
				}

				if (n != 0) {
					MobHunting.getInstance().getMessages().debug("Saving Mobhunting extra Boss data.");
					config.save(file);
				}
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public static void saveBossMobsData(String key) {
		try {
			if (mMobRewardData.containsKey(key)) {
				ConfigurationSection section = config.createSection(key);
				mMobRewardData.get(key).save(section);
				MobHunting.getInstance().getMessages().debug("Saving Mobhunting extra Boss data.");
				config.save(file);
			} else {
				MobHunting.getInstance().getMessages().debug("ERROR! Boss ID (%s) is not found in mMobRewardData", key);
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

}
