package com.tunombre.bossplugin.task;

import com.tunombre.bossplugin.Main;

/**
 * Actualiza barras y temporizadores cada 5 ticks; habilidades cada segundo.
 */
public class BossTask implements Runnable {

    private final Main plugin;
    private int counter;

    public BossTask(Main plugin) {
        this.plugin = plugin;
    }

    @Override
    public void run() {
        plugin.getBossManager().tick();
        counter++;
        if (counter % 4 == 0) {
            plugin.getBossManager().tickAbilities();
        }
    }
}
