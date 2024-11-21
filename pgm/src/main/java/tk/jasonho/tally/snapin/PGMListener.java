package tk.jasonho.tally.snapin;

import com.google.common.util.concurrent.AtomicDouble;
import com.google.gson.JsonObject;
import org.apache.commons.lang3.tuple.Pair;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import tc.oc.pgm.api.match.Match;
import tc.oc.pgm.api.match.event.MatchFinishEvent;
import tc.oc.pgm.api.match.event.MatchLoadEvent;
import tc.oc.pgm.api.match.event.MatchStartEvent;
import tc.oc.pgm.api.match.event.MatchUnloadEvent;
import tc.oc.pgm.api.party.Competitor;
import tc.oc.pgm.api.player.MatchPlayer;
import tc.oc.pgm.api.player.ParticipantState;
import tc.oc.pgm.api.player.event.MatchPlayerDeathEvent;
import tc.oc.pgm.api.tracker.info.DamageInfo;
import tc.oc.pgm.api.tracker.info.FallInfo;
import tc.oc.pgm.api.tracker.info.MeleeInfo;
import tc.oc.pgm.api.tracker.info.PotionInfo;
import tc.oc.pgm.controlpoint.events.CapturingTeamChangeEvent;
import tc.oc.pgm.controlpoint.events.ControllerChangeEvent;
import tc.oc.pgm.flag.event.FlagCaptureEvent;
import tc.oc.pgm.flag.event.FlagPickupEvent;
import tc.oc.pgm.goals.events.GoalCompleteEvent;
import tc.oc.pgm.goals.events.GoalEvent;
import tc.oc.pgm.goals.events.GoalTouchEvent;
import tc.oc.pgm.spawns.events.PlayerSpawnEvent;
import tc.oc.pgm.stats.PlayerStats;
import tc.oc.pgm.stats.StatsMatchModule;
import tc.oc.pgm.teams.Team;
import tc.oc.pgm.tracker.info.*;
import tk.jasonho.tally.core.bukkit.*;
import tk.jasonho.tally.snapin.core.competitive.CompetitiveOperations;
import tk.jasonho.tally.snapin.core.competitive.StatType;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

public class PGMListener extends TallyListener {

    public PGMListener(TallyOperationHandler handler) {
        super(handler);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onGoalEvent(GoalEvent event) {
        Competitor competitor = event.getCompetitor();
        JsonObject jsonObject = PGMUtils.pgmEventToData(event);

        if(competitor != null) {
            Collection<MatchPlayer> players = competitor.getPlayers();
            players.forEach(p -> {
                UUID id = p.getBukkit().getUniqueId();
                super.operationHandler.track("goalevent_" + event.getClass().getSimpleName(), null, id, jsonObject);
            });
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onGoalCompleteEvent(GoalCompleteEvent event) {
        Competitor competitor = event.getCompetitor();
        JsonObject jsonObject = PGMUtils.pgmEventToData(event);

        event.getContributions().forEach(contribution ->
                contribution.getPlayerState().getPlayer().ifPresent(p ->
                        super.operationHandler.track("goalcompletevent_contribution", null, p.getBukkit().getUniqueId(), jsonObject)));

        if(competitor != null) {
            Collection<MatchPlayer> players = competitor.getPlayers();
            players.forEach(p -> {
                UUID id = p.getBukkit().getUniqueId();
                super.operationHandler.track("goalcompleteevent", null, id, jsonObject);
            });
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onGoalTouchEvent(GoalTouchEvent event) {
        Competitor competitor = event.getCompetitor();
        JsonObject jsonObject = PGMUtils.pgmEventToData(event);

        if(competitor != null) {
            Collection<MatchPlayer> players = competitor.getPlayers();
            players.forEach(p -> {
                UUID id = p.getBukkit().getUniqueId();
                super.operationHandler.track("goaltouchevent", null, id, jsonObject);
            });
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHillCaptureTeamChange(CapturingTeamChangeEvent event) {
        Competitor newTeam = event.getNewTeam();
        Competitor oldTeam = event.getOldTeam();
        JsonObject jsonObject = PGMUtils.pgmEventToData(event);

        if(newTeam != null) {
            newTeam.getPlayers().forEach(p -> super.operationHandler.track("objective_hillcapture_win", null, p.getBukkit().getUniqueId(), jsonObject));
        }
        if(oldTeam != null) {
            oldTeam.getPlayers().forEach(p -> super.operationHandler.track("objective_hillcapture_lose", null, p.getBukkit().getUniqueId(), jsonObject));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHillControlTeamChange(ControllerChangeEvent event) {
        Competitor newTeam = event.getNewController();
        Competitor oldTeam = event.getOldController();
        JsonObject jsonObject = PGMUtils.pgmEventToData(event);

        if(newTeam != null) {
            newTeam.getPlayers().forEach(p -> super.operationHandler.track("objective_hillcontrol_win", null, p.getBukkit().getUniqueId(), jsonObject));
        }
        if(oldTeam != null) {
            oldTeam.getPlayers().forEach(p -> super.operationHandler.track("objective_hillcontrol_lose", null, p.getBukkit().getUniqueId(), jsonObject));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFlagCapture(FlagCaptureEvent event) {
        Competitor flagCaptureTeam = event.getCompetitor();
        MatchPlayer flagCapturePlayer = event.getCarrier();

        JsonObject jsonObject = PGMUtils.pgmEventToData(event);
        if(flagCaptureTeam != null) {
            flagCaptureTeam.getPlayers().forEach(p -> super.operationHandler.track("flag_capture_support", null, p.getBukkit().getUniqueId(), jsonObject));
        }
        super.operationHandler.track("flag_capture_capture", null, flagCapturePlayer.getBukkit().getUniqueId(), jsonObject);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFlagPickup(FlagPickupEvent event) {
        MatchPlayer flagCapturePlayer = event.getCarrier();

        JsonObject jsonObject = PGMUtils.pgmEventToData(event);
        super.operationHandler.track("flag_carry", null, flagCapturePlayer.getBukkit().getUniqueId(), jsonObject);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerDeath(MatchPlayerDeathEvent event) {
        MatchPlayer killed = event.getVictim();
        if(killed == null) { // make sure they still exist
            Bukkit.getLogger().warning(event.toString() + " event has a null victim.");
            return;
        }

        // resolve damage type
        DamageInfo info = event.getDamageInfo();
        String damage;
        if (info instanceof MeleeInfo) {
            damage = "melee";
        } else if (info instanceof ProjectileInfo) {
            damage = "projectile";
        } else if (info instanceof ExplosionInfo) {
            damage = "explosion";
        } else if (info instanceof FireInfo) {
            damage = "fire";
        } else if (info instanceof PotionInfo) {
            damage = "magic";
        } else if (info instanceof FallingBlockInfo) {
            damage = "squash";
        } else if (info instanceof FallInfo) {
            damage = "fall";
        } else if (info instanceof GenericDamageInfo) {
            damage = "generic";
        } else {
            damage = "unknown";
        }

        UUID exclude = null;
        ParticipantState killer = event.getKiller();
        if(killer != null && killer.getPlayer().isPresent()) {
            exclude = killer.getPlayer().get().getBukkit().getUniqueId();
        }

        // Assists
        CompetitiveOperations operationHandler = ((CompetitiveOperations) super.operationHandler);
        DamageTrackModule damageTrackModule = operationHandler.getTally().getDamageTrackModule();
        List<DamageTrackModule.DamageExchange> damageExchanges = damageTrackModule.getDamageExchanges();

        Map<UUID, Pair<AtomicDouble, AtomicInteger>> assisters = new HashMap<>();
        for (DamageTrackModule.DamageExchange exc : damageExchanges) {
            if(exc.getDirection() == DamageTrackModule.DamageDirection.GIVE && exc.getYou() == killed.getBukkit().getUniqueId()) {
                if(exc.getMe() == exclude // is the killer
                        || exc.getMe() == killed.getBukkit().getUniqueId() // or is suicide
                        || exc.isCreditRewarded()) { // or is already tracked/rewarded
                    continue;
                }

                if(assisters.containsKey(exc.getMe())) { // if previously damaged
                    assisters.get(exc.getMe()).getLeft().addAndGet(exc.getAmount()); // add to damage total
                    assisters.get(exc.getMe()).getRight().incrementAndGet(); // add to hits total
                } else { // not previously damaged
                    assisters.put(exc.getMe(), Pair.of(new AtomicDouble(exc.getAmount()), new AtomicInteger(1))); // insert to damagers
                }

                // set as rewarded :)
                exc.setCreditRewarded(true);
            }
        }

        // track!
        JsonObject jsonObject = PGMUtils.pgmEventToData(event);
        jsonObject.addProperty("caused_by_type", damage);
        jsonObject.addProperty("assisted", killer.getPlayer().get().getBukkit().getUniqueId().toString());

        if(killer != null && killer.getPlayer().isPresent()) {
            operationHandler.trackPVPTransaction(killer.getPlayer().get().getBukkit().getUniqueId(), killed.getBukkit().getUniqueId(), damage);
        } else {
            operationHandler.trackPVPTransaction(DamageTrackModule.ENVIRONMENT, killed.getBukkit().getUniqueId(), damage);
        }

        for (Map.Entry<UUID, Pair<AtomicDouble, AtomicInteger>> assist : assisters.entrySet()) {
            Player assister = Bukkit.getPlayer(assist.getKey());

            if(assister != null) {
                if(assist.getValue().getLeft().get() > 5) {
                    // people who do less than 5 damage(2 1/2 hearts) are given assists usually but
                    // we are tracking all hits i guess
                }

                jsonObject.addProperty("caused_by_type", damage);
                operationHandler.track(StatType.ASSIST, killed.getBukkit().getUniqueId(), assister.getUniqueId(), jsonObject);
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMatchHi(MatchLoadEvent e) {
        super.operationHandler.getTally()
                .loadTracker(new DefaultDamageTrackModule(super.operationHandler,e.getWorld().getName() + "-" + e.getMatch().getId()));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMatchBye(MatchUnloadEvent e) {
        super.operationHandler.getTally().unloadTracker();
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSpawn(PlayerSpawnEvent e) {
        // Reset damage tracker
        ((CompetitiveOperations) super.operationHandler).handleRespawn(e.getPlayer().getBukkit().getUniqueId());
    }

    // Extra stats
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onWin(MatchFinishEvent event) {
        // track the winners
        Match match = event.getMatch();
        JsonObject jsonObject = PGMUtils.pgmEventToData(event);

        Competitor winner = event.getWinner();
        boolean inconclusive = winner == null;
        List<UUID> observers = match.getPlayers()
                .stream()
                .filter(p -> p.getParty().isObserving())
                .map(mp -> mp.getBukkit().getUniqueId())
                .collect(Collectors.toList());
        List<UUID> winners = match.getPlayers()
                .stream()
                .filter(p -> (winner == p.getParty()))
                .map(mp -> mp.getBukkit().getUniqueId())
                .collect(Collectors.toList());
        List<UUID> losers = match.getPlayers()
                .stream()
                .filter(p -> (winner != p.getParty()) && (p.getParty() instanceof Team))
                .map(mp -> mp.getBukkit().getUniqueId())
                .collect(Collectors.toList());
        List<UUID> everyone = match.getParticipants().stream().map(mp -> mp.getBukkit().getUniqueId()).collect(Collectors.toList());

        CompetitiveOperations operationHandler = ((CompetitiveOperations) super.operationHandler);
        operationHandler.trackMatchParticipants(
                winners,
                losers,
                observers,
                everyone,
                inconclusive,
                jsonObject
        );

        Bukkit.getLogger().info("Tally is tracking stats on " + match.getParticipants().size() + " participants");
        StatsMatchModule statsModule = event.getMatch().getModule(StatsMatchModule.class);
        if (statsModule == null) {
            Bukkit.getLogger().warning("Could not track PGM stats summary, the stats module was not available on this match!");
            return;
        }

        for (MatchPlayer participant : match.getParticipants()) {
            PlayerStats playerStat = statsModule.getPlayerStat(participant);
            if (playerStat == null) {
                Bukkit.getLogger().warning("Player " + participant.getName() + "'s stats were not available for this match.");
                continue;
            }
            JsonObject playerStats = PGMUtils.playerStats(playerStat);
            operationHandler.track("pgm_stats_summary", null, participant.getBukkit().getUniqueId(), playerStats);
        }
    }
}
