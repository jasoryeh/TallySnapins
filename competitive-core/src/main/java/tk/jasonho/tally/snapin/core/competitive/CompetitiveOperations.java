package tk.jasonho.tally.snapin.core.competitive;

import com.google.gson.JsonObject;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import tk.jasonho.tally.core.bukkit.DamageTrackModule;
import tk.jasonho.tally.core.bukkit.TallyOperationHandler;
import tk.jasonho.tally.core.bukkit.TallyPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class CompetitiveOperations extends TallyOperationHandler {
    public CompetitiveOperations(TallyPlugin tally) {
        super(tally);
    }

    public void track(StatType type, UUID loser, UUID victor, JsonObject jsonObject) {
        super.track(type.toString(), loser, victor, jsonObject);
    }

    public void trackPVPTransaction(UUID victor, UUID loser, JsonObject jsonObject) {
        // receiver = owner of stat
        // actor = person who caused this stat to happen to the receiver
        // actor caused addition of <stat type> stat to receiver

        // scenario 1: receiver kills actor
        // ex1. actor [added] KILL [stat to] receiver // reason: actor died to receiver
        // scenario 2: actor kills receiver
        // ex2. actor [added] DEATH [stat to] receiver // reason: actor caused receiver to die

        // receiver gets stat assigned to player
        // receiver = person who's profile this stat shows up on
        // this means that if the objective this stat is for is "KILL"
        // the receiver

        this.track(StatType.KILL, loser, victor, jsonObject);
        this.track(StatType.DEATH, victor, loser, jsonObject);
    }

    public void trackWin(UUID player, JsonObject extra) {
        this.track(StatType.WIN, null, player, extra);
    }

    public void trackLoss(UUID player, JsonObject extra) {
        this.track(StatType.LOSS, null, player, extra);
    }

    public void trackPlayed(UUID player, JsonObject extra) {
        this.track(StatType.PLAYED, null, player, extra);
    }

    public void trackObserved(UUID player, JsonObject extra) {
        this.track(StatType.OBSERVE, null, player, extra);
    }

    public void trackInconclusive(UUID player, JsonObject extra) {
        this.track(StatType.INCONCLUSIVE, null, player, extra);
    }

    public void trackMatchParticipants(List<UUID> winners, List<UUID> losers, List<UUID> observers, List<UUID> everyone,
                                       boolean inconclusive, JsonObject extras) {
        observers.forEach(u -> this.trackObserved(u, extras));
        if(inconclusive) {
            everyone.forEach(u -> this.trackInconclusive(u, extras));
        } else {
            winners.forEach(u -> {
                this.trackPlayed(u, extras);
                this.trackWin(u, extras);
            });
            losers.forEach(u -> {
                this.trackPlayed(u, extras);
                this.trackLoss(u, extras);
            });
        }
    }

    public void handleRespawn(UUID respawning) {
        DamageTrackModule trackModule = super.getTally().getDamageTrackModule();
        Player player = Bukkit.getPlayer(respawning);
        if(respawning != null && player != null) {
            trackModule.trackUntracked(respawning);
            if(this.getTally().getConfig().getBoolean("respawnsound")) {
                player.playSound(player.getLocation(), Sound.ENDERMAN_TELEPORT, 1, 1);
            }
        }
    }
}
