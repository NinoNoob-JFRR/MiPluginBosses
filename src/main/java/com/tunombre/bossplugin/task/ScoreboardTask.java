package com.tunombre.bossplugin.task;

import com.tunombre.bossplugin.Main;

/**
 * Refresco periodico de la scoreboard lateral. La vida se actualiza
 * instantaneamente desde el listener de dano/curacion.
 */
public class ScoreboardTask implements Runnable {

    private final Main plugin;

    public ScoreboardTask(Main plugin) {
        this.plugin = plugin;
    }

    @Override
    public void run() {
        plugin.getScoreboardManager().tick();
    }
}
