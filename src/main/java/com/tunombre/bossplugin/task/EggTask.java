package com.tunombre.bossplugin.task;

import com.tunombre.bossplugin.Main;

/**
 * Aparicion, desaparicion y particulas de los huevos.
 */
public class EggTask implements Runnable {

    private final Main plugin;

    public EggTask(Main plugin) {
        this.plugin = plugin;
    }

    @Override
    public void run() {
        plugin.getEggManager().tick();
    }
}
