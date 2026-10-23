package com.tunombre.bossplugin;

import com.tunombre.bossplugin.boss.BossManager;
import com.tunombre.bossplugin.command.BossCommand;
import com.tunombre.bossplugin.listener.BossListener;
import org.bukkit.plugin.java.JavaPlugin;

public final class Main extends JavaPlugin {

    private static Main instance;
    private BossManager bossManager;

    @Override
    public void onEnable() {
        instance = this;

        // Guardar configuración por defecto si no existe
        saveDefaultConfig();

        // Inicializar tarea de barras de jefe
        com.tunombre.bossplugin.task.BossBarTask bossBarTask = new com.tunombre.bossplugin.task.BossBarTask(this, null);

        // Inicializar gestor de jefes (le pasamos la tarea de barras)
        bossManager = new BossManager(this, bossBarTask);
        bossBarTask.setBossManager(bossManager);
        bossManager.registerDefaultBosses();

        // Registrar comando
        BossCommand bossCommand = new BossCommand(this);
        getCommand("boss").setExecutor(bossCommand);
        getCommand("boss").setTabCompleter(bossCommand);

        // Registrar eventos
        getServer().getPluginManager().registerEvents(new BossListener(this), this);

        // Iniciar tarea de actualización de barras de jefe cada 5 ticks (0.25 s)
        getServer().getScheduler().runTaskTimer(this, bossBarTask, 0L, 5L);

        getLogger().info("MiPluginBosses ha sido activado en Minecraft 26.3!");
    }

    @Override
    public void onDisable() {
        if (bossManager != null) {
            bossManager.cleanup();
        }
        getLogger().info("MiPluginBosses ha sido desactivado.");
    }

    public static Main getInstance() {
        return instance;
    }

    public BossManager getBossManager() {
        return bossManager;
    }
}
