package dev.ryanhcode.sable.neoforge.gametest;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import net.minecraft.world.BossEvent;

public class TestProgressBar {

    private final ServerBossEvent bossEvent;
    private final PlayerList playerList;
    private long maxItems;

    public TestProgressBar(final PlayerList playerList) {
        this.bossEvent = new ServerBossEvent(Component.literal("Test Progress"), BossEvent.BossBarColor.RED, BossEvent.BossBarOverlay.PROGRESS);
        this.playerList = playerList;
    }

    private void updateVisible() {
        for (final ServerPlayer player : this.playerList.getPlayers()) {
            this.bossEvent.addPlayer(player);
        }
    }

    public void begin(final long maxItems) {
        this.maxItems = maxItems;
        this.bossEvent.setName(Component.literal("Test Progress: 0 / " + maxItems));
        this.updateVisible();
    }

    public void update(final long items) {
        this.updateVisible();
        this.bossEvent.setName(Component.literal("Test Progress: " + items + " / " + this.maxItems));
        this.bossEvent.setProgress((float) items / this.maxItems);
    }

    public void end() {
        this.bossEvent.removeAllPlayers();
    }
}
