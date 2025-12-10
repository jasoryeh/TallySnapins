package tk.jasonho.tally.snapin;

import com.google.common.util.concurrent.AtomicDouble;
import com.google.gson.JsonArray;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.apache.commons.lang3.tuple.Pair;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.potion.Potion;
import org.bukkit.potion.PotionEffectType;
import tc.oc.pgm.api.match.Match;
import tc.oc.pgm.api.match.event.*;
import tc.oc.pgm.api.party.Competitor;
import tc.oc.pgm.api.player.MatchPlayer;
import tc.oc.pgm.api.player.ParticipantState;
import tc.oc.pgm.api.player.event.MatchPlayerDeathEvent;
import tc.oc.pgm.api.time.Tick;
import tc.oc.pgm.api.tracker.info.*;
import tc.oc.pgm.controlpoint.events.CapturingTeamChangeEvent;
import tc.oc.pgm.controlpoint.events.ControllerChangeEvent;
import tc.oc.pgm.events.PlayerJoinMatchEvent;
import tc.oc.pgm.events.PlayerJoinPartyEvent;
import tc.oc.pgm.events.PlayerLeavePartyEvent;
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
import tc.oc.pgm.util.material.MaterialData;
import tk.jasonho.tally.api.util.TallyLogger;
import tk.jasonho.tally.core.bukkit.*;
import tk.jasonho.tally.snapin.core.competitive.CompetitiveOperations;
import tk.jasonho.tally.snapin.core.competitive.StatType;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

public class PGMListener extends TallyListener {

    public PGMListener(TallyOperationHandler handler) {
        super(handler);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPartyJoin(PlayerJoinPartyEvent event) {
        JsonObject jsonObject = PGMUtils.pgmEventToData(event);
        if (event.getNewParty() != null) {
            PGMUtils.extract(event.getNewParty(), "newParty", jsonObject);
        } else {
            jsonObject.add("newParty", JsonNull.INSTANCE);
        }

        super.operationHandler.track(
                event.getClass().getSimpleName(),
                null,
                event.getPlayer().getBukkit().getUniqueId(),
                jsonObject);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPartyLeave(PlayerLeavePartyEvent event) {
        JsonObject jsonObject = PGMUtils.pgmEventToData(event);
        PGMUtils.extract(event.getParty(), "oldParty", jsonObject);

        super.operationHandler.track(
                event.getClass().getSimpleName(),
                null,
                event.getPlayer().getBukkit().getUniqueId(),
                jsonObject);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onJoinMatch(PlayerJoinMatchEvent event) {
        JsonObject jsonObject = PGMUtils.pgmEventToData(event);

        super.operationHandler.track(
                event.getClass().getSimpleName(),
                null,
                event.getPlayer().getBukkit().getUniqueId(),
                jsonObject);
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
        JsonObject damageInfo = extractData(info);
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
        } else if (info == null) {
            damage = "unknown";
        } else {
            damage = info.getClass().getCanonicalName();
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
        jsonObject.add("damage_info", damageInfo);
        if(killer != null && killer.getPlayer().isPresent()) {
            jsonObject.addProperty("assisted", killer.getPlayer().get().getBukkit().getUniqueId().toString());
            operationHandler.trackPVPTransaction(killer.getPlayer().get().getBukkit().getUniqueId(), killed.getBukkit().getUniqueId(), jsonObject);
        } else {
            operationHandler.trackPVPTransaction(DamageTrackModule.ENVIRONMENT, killed.getBukkit().getUniqueId(), jsonObject);
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
        super.operationHandler.track(e.getClass().getSimpleName(), null, null, PGMUtils.pgmEventToData(e));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMatchAfterLoad(MatchAfterLoadEvent e) {
        this.operationHandler.getTally().getStatsManager().setMatchTag(e.getMatch().getId().toString());
        super.operationHandler.track(e.getClass().getSimpleName(), null, null, PGMUtils.pgmEventToData(e));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMatchBye(MatchUnloadEvent e) {
        super.operationHandler.getTally().unloadTracker();
        super.operationHandler.track(e.getClass().getSimpleName(), null, null, PGMUtils.pgmEventToData(e));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSpawn(PlayerSpawnEvent e) {
        // Reset damage tracker
        ((CompetitiveOperations) super.operationHandler).handleRespawn(e.getPlayer().getBukkit().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMatchStart(MatchStartEvent e) {
        super.operationHandler.getTally()
                .loadTracker(new DefaultDamageTrackModule(super.operationHandler,e.getWorld().getName() + "-" + e.getMatch().getId()));

        JsonObject jsonObject = PGMUtils.pgmEventToData(e);

        TallyOperationHandler handler = super.operationHandler;
        handler.track(e.getClass().getSimpleName(), null, null, jsonObject);
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
        operationHandler.track(event.getClass().getSimpleName(), null, null, jsonObject);

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
            JsonObject playerStats = PGMUtils.playerStats(playerStat, jsonObject);
            operationHandler.track("pgm_stats_summary", null, participant.getBukkit().getUniqueId(), playerStats);
        }
    }

    private JsonObject extractMatchPlayer(MatchPlayer player, JsonObject root) {
        if (player == null) return root;

        root.addProperty("player_uuid", player.getBukkit().getUniqueId().toString());
        root.addProperty("player_name", player.getBukkit().getName());
        root.addProperty("match_player_id", player.getId().toString());

        return root;
    }

    private JsonObject extractCompetitor(Competitor competitor, JsonObject root) {
        if (competitor == null) return root;

        root.addProperty("id", competitor.getId().toString());
        root.addProperty("name", LegacyComponentSerializer.legacyAmpersand().serialize(competitor.getName()));
        root.addProperty("size", competitor.getPlayers().size());

        return root;
    }

    private JsonObject extractParticipantState(ParticipantState participantState, JsonObject root) {
        if (participantState == null) return root;
        if (participantState.getPlayer().isPresent()) {
            root.add("player", extractMatchPlayer(participantState.getPlayer().get(), new JsonObject()));
        } else {
            root.add("player", extractMatchPlayer(null, new JsonObject()));
        }
        if (participantState.getId() != null) {
            root.addProperty("attacker_id", participantState.getId().toString());
        } else {
            root.add("attacker_id", JsonNull.INSTANCE);
        }
        if (participantState.getName() != null) {
            root.addProperty("attacker_name", LegacyComponentSerializer.legacyAmpersand().serialize(participantState.getName()));
        } else {
            root.add("attacker_name", JsonNull.INSTANCE);
        }
        if (participantState.getParty() != null) {
            root.add("party", extractCompetitor(participantState.getParty(), new JsonObject()));
        } else {
            root.add("party", extractCompetitor(null, new JsonObject()));
        }
        return root;
    }

    private JsonObject extractCauseInfo(CauseInfo info, JsonObject root) {
        if (info == null) return root;
        if (info.getCause() != null) {
            root.add("cause", extractData(info.getCause()));
        } else {
            root.add("cause", JsonNull.INSTANCE);
        }
        return root;
    }

    private JsonObject extractDamageInfo(DamageInfo info, JsonObject root) {
        if (info == null) return root;
        if (info.getAttacker() != null) {
            root.add("attacker", extractParticipantState(info.getAttacker(), new JsonObject()));
        } else {
            root.add("attacker", JsonNull.INSTANCE);
        }
        return root;
    }

    private JsonObject extractFallInfo(FallInfo info, JsonObject root) {
        if (info == null) return root;
        if (info.getFrom() != null) {
            root.addProperty("from", info.getFrom().toString());
        } else {
            root.add("from", JsonNull.INSTANCE);
        }
        if (info.getTo() != null) {
            root.addProperty("to", info.getTo().toString());
        } else {
            root.add("to", JsonNull.INSTANCE);
        }
        return root;
    }

    private JsonObject extractMeleeInfo(MeleeInfo info, JsonObject root) {
        if (info == null) return root;
        if (info.getWeapon() != null) {
            root.add("weapon", extractPhysicalInfo(info.getWeapon(), new JsonObject()));
        } else {
            root.add("weapon", JsonNull.INSTANCE);
        }
        return root;
    }

    private JsonObject extractOwnerInfo(OwnerInfo info, JsonObject root) {
        if (info == null) return root;
        if (info.getOwner() != null) {
            root.add("owner", extractParticipantState(info.getOwner(), new JsonObject()));
        } else {
            root.add("owner", JsonNull.INSTANCE);
        }
        return root;
    }

    private JsonObject extractPhysicalInfo(PhysicalInfo info, JsonObject root) {
        if (info == null) return root;
        if (info.getIdentifier() != null) {
            root.addProperty("identifier", info.getIdentifier());
        } else {
            root.add("identifier", JsonNull.INSTANCE);
        }
        if (info.getName() != null) {
            root.addProperty("name", LegacyComponentSerializer.legacyAmpersand().serialize(info.getName()));
        } else {
            root.add("name", JsonNull.INSTANCE);
        }
        root = extractOwnerInfo(info, root);
        return root;
    }

    private JsonObject extractPotionEffectType(PotionEffectType type, JsonObject root) {
        if (type == null) return root;
        root.addProperty("id", type.getId());
        root.addProperty("name", type.getName());
        root.addProperty("durationModifier", type.getDurationModifier());
        root.addProperty("class", type.getClass().getCanonicalName());
        return root;
    }

    private JsonObject extractPotionInfo(PotionInfo info, JsonObject root) {
        if (info == null) return root;
        root.add("potionEffectType", extractPotionEffectType(info.getPotionEffect(), new JsonObject()));
        return root;
    }

    private JsonObject extractWorld(World world, JsonObject root) {
        if (world == null) return root;

        root.addProperty("name", world.getName());
        root.addProperty("environment", world.getEnvironment().name());
        root.addProperty("seed", world.getSeed());
        root.addProperty("time", world.getTime());
        root.addProperty("weather", world.hasStorm() ? "storm" : "clear");
        root.addProperty("difficulty", world.getDifficulty().name());

        return root;
    }

    private JsonObject extractBlock(Block block, JsonObject root) {
        if (block == null) return root;

        root.addProperty("x", block.getX());
        root.addProperty("y", block.getY());
        root.addProperty("z", block.getZ());

        root.add("type", extractMaterial(block.getType(), new JsonObject()));
        return root;
    }

    private JsonObject extractLocation(Location location, JsonObject root) {
        if (location == null) return root;

        root.addProperty("x", location.getX());
        root.addProperty("y", location.getY());
        root.addProperty("z", location.getZ());
        root.addProperty("yaw", location.getYaw());
        root.addProperty("pitch", location.getPitch());

        root.add("world", extractWorld(location.getWorld(), new JsonObject()));

        return root;
    }

    private JsonObject extractRangedInfo(RangedInfo info, JsonObject root) {
        if (info == null) return root;
        root.add("origin", extractLocation(info.getOrigin(), new JsonObject()));
        return root;
    }

    private JsonObject extractMaterial(Material material, JsonObject root) {
        if (material != null) {
            root.addProperty("name", material.name());
            root.addProperty("toString", material.toString());
        } else {
            root.add("name", JsonNull.INSTANCE);
            root.add("toString", JsonNull.INSTANCE);
        }
        return root;
    }

    private JsonObject extractBlockInfo(BlockInfo info, JsonObject root) {
        if (info == null) return root;
        if (info.getMaterial() != null) {
            MaterialData material = info.getMaterial();
            Material itemType = material.getItemType();
            root.add("material", extractMaterial(itemType, new JsonObject()));
        } else {
            root.add("material", extractMaterial(null, new JsonObject()));
        }
        return root;
    }

    public JsonObject extractDispenserInfo(DispenserInfo info, JsonObject root) {
        if (info == null) return root;

        return root; // nothing to extract here that isn't already handled
    }

    public JsonObject extractEntityInfo(EntityInfo info, JsonObject root) {
        if (info == null) return root;
        if (info.getEntityType() != null) {
            root.addProperty("entity_type", info.getEntityType().name());
            root.addProperty("entity_type_string", info.getEntityType().toString());
        } else {
            root.add("entity_type", JsonNull.INSTANCE);
            root.add("entity_type_string", JsonNull.INSTANCE);
        }
        if (info.getCustomName() != null) {
            root.addProperty("custom_name", info.getCustomName());
        } else {
            root.add("custom_name", JsonNull.INSTANCE);
        }
        return root;
    }

    public JsonObject extractExplosionInfo(ExplosionInfo info, JsonObject root) {
        if (info == null) return root;
        if (info.getExplosive() != null) {
            root.add("explosive", extractPhysicalInfo(info.getExplosive(), new JsonObject()));
        }
        return root;
    }

    public JsonObject extractFallingBlockInfo(FallingBlockInfo info, JsonObject root) {
        if (info == null) return root;
        if (info.getMaterial() != null) {
            root.add("material", extractMaterial(info.getMaterial(), new JsonObject()));
        } else {
            root.add("material", extractMaterial(null, new JsonObject()));
        }
        return root;
    }

    public JsonObject extractFallState(FallState state, JsonObject root) {
        if (state == null) return root;

        root.add("victim", extractMatchPlayer(state.victim, new JsonObject()));
        root.add("origin", extractLocation(state.origin, new JsonObject()));
        root.add("from", state.from != null ? new JsonPrimitive(state.from.toString()) : JsonNull.INSTANCE);
        root.add("cause", extractData(state.cause));
        root.add("to", state.to != null ? new JsonPrimitive(state.to.toString()) : JsonNull.INSTANCE);
        root.addProperty("isStarted", state.isStarted);
        root.addProperty("isEnded", state.isEnded);
        root.addProperty("onGroundTick", state.onGroundTick);
        root.addProperty("isSwimming", state.isSwimming);
        root.addProperty("swimmingTick", state.swimmingTick);
        root.addProperty("isClimbing", state.isClimbing);
        root.addProperty("climbingTick", state.climbingTick);
        root.addProperty("isInLava", state.isInLava);
        root.addProperty("inLavaTick", state.inLavaTick);
        root.addProperty("outLavaTick", state.outLavaTick);
        root.addProperty("groundTouchCount", state.groundTouchCount);

        return root;
    }

    private JsonObject extractFireInfo(FireInfo info, JsonObject root) {
        if (info == null) return root;

        root.add("igniter", extractPhysicalInfo(info.getIgniter(), new JsonObject()));

        return root;
    }

    private JsonObject extractGenericDamageInfo(GenericDamageInfo info, JsonObject root) {
        if (info == null) return root;

        root.add("damageType", info.getDamageType() == null ? JsonNull.INSTANCE : new JsonPrimitive(info.getDamageType().name()));

        return root;
    }

    private JsonObject extractGenericFallInfo(GenericFallInfo info, JsonObject root) {
        if (info == null) return root;

        root.add("origin", extractLocation(info.getOrigin(), new JsonObject()));

        return root;
    }

    private JsonObject extractGenericPotionInfo(GenericPotionInfo info, JsonObject root) {
        if (info == null) return root;

        root.add("potionEffect", extractPotionEffectType(info.getPotionEffect(), new JsonObject()));

        return root;
    }
    
    private JsonObject extractItemMeta(ItemMeta meta, JsonObject root) {
        if (meta == null) return root;

        if (meta.getDisplayName() != null) {
            root.addProperty("displayName", meta.getDisplayName());
        } else {
            root.add("displayName", JsonNull.INSTANCE);
        }

        if (meta.getEnchants() != null) {
            JsonObject enchants = new JsonObject();
            meta.getEnchants().forEach((key, value) -> enchants.addProperty(key.getName(), value));
            root.add("enchants", enchants);
        } else {
            root.add("enchants", JsonNull.INSTANCE);
        }

        if (meta.getLore() != null) {
            JsonArray lore = new JsonArray();
            meta.getLore().forEach(line -> lore.add(line));
            root.add("lore", lore);
        } else {
            root.add("lore", JsonNull.INSTANCE);
        }

        if (meta.getItemFlags() != null) {
            JsonArray flags = new JsonArray();
            meta.getItemFlags().forEach(flag -> flags.add(flag.name()));
            root.add("itemFlags", flags);
        } else {
            root.add("itemFlags", JsonNull.INSTANCE);
        }

        if (meta.getModifiedAttributes() != null) {
            JsonArray attributes = new JsonArray();
            meta.getModifiedAttributes().forEach(attribute -> attributes.add(attribute));
            root.add("modifiedAttributes", attributes);
        } else {
            root.add("modifiedAttributes", JsonNull.INSTANCE);
        }
        
        return root;
    }

    private JsonObject extractItemStack(ItemStack stack, JsonObject root) {
        if (stack == null) return root;

        root.addProperty("type", stack.getTypeId());
        root.add("type", extractMaterial(stack.getType(), new JsonObject()));
        root.addProperty("amount", stack.getAmount());
        root.addProperty("durability", stack.getDurability());
        root.add("itemmeta", extractItemMeta(stack.getItemMeta(), new JsonObject()));

        return root;
    }

    private JsonObject extractItemInfo(ItemInfo info, JsonObject root) {
        if (info == null) return root;

        root.add("item", extractItemStack(info.getItem(), new JsonObject()));

        return root;
    }

    private JsonObject extractMobInfo(MobInfo info, JsonObject root) {
        if (info == null) return root;

        root.add("weapon", extractItemInfo(info.getWeapon(), new JsonObject()));

        return root;
    }

    private JsonObject extractNullDamageInfo(NullDamageInfo info, JsonObject root) {
        if (info == null) return root;

        return root;
    }

    private JsonObject extractOwnerInfoBase(OwnerInfoBase info, JsonObject root) {
        if (info == null) return root;

        root.add("owner", extractParticipantState(info.getOwner(), new JsonObject()));

        return root;
    }

    public JsonObject extractPlayerInfo(PlayerInfo info, JsonObject root) {
        if (info == null) return root;

        root.add("player", extractParticipantState(info.getAttacker(), new JsonObject()));
        root.add("weapon", extractItemInfo(info.getWeapon(), new JsonObject()));

        return root;
    }

    public JsonObject extractProjectileInfo(ProjectileInfo info, JsonObject root) {
        if (info == null) return root;

        root.add("projectile", extractPhysicalInfo(info.getProjectile(), new JsonObject()));
        root.add("shooter", extractPhysicalInfo(info.getShooter(), new JsonObject()));
        root.add("origin", extractLocation(info.getOrigin(), new JsonObject()));
        root.add("customName", info.getName() == null ? JsonNull.INSTANCE : new JsonPrimitive(LegacyComponentSerializer.legacySection().serialize(info.getName())));

        return root;
    }

    public JsonObject extractInstant(Instant instant, JsonObject root) {
        if (instant == null) return root;

        root.addProperty("epochSecond", instant.getEpochSecond());
        root.addProperty("epochMillis", instant.toEpochMilli());

        return root;
    }

    public JsonObject extractTick(Tick tick, JsonObject root) {
        if (tick == null) return root;

        root.addProperty("tick", tick.tick);
        root.add("instant", extractInstant(tick.instant, new JsonObject()));

        return root;
    }

    public JsonObject extractSpleefInfo(SpleefInfo info, JsonObject root) {
        if (info == null) return root;

        root.add("breaker", extractDamageInfo(info.getBreaker(), new JsonObject()));
        root.add("time", extractTick(info.getTime(), new JsonObject()));

        return root;
    }

    public JsonObject extractThrownPotionInfo(ThrownPotionInfo info, JsonObject root) {
        if (info == null) return root;

        root.add("effectType", extractPotionEffectType(info.getPotionEffect(), new JsonObject()));

        return root;
    }

    public JsonObject extractTNTInfo(TNTInfo info, JsonObject root) {
        if (info == null) return root;

        root.add("origin", extractLocation(info.getOrigin(), new JsonObject()));

        return root;
    }


    private void extractHelper(TrackerInfo info, String name, JsonObject root, ExtractFunction extractor) {
        try {
            if (info != null) {
                root.add(name, extractor.extract(info, new JsonObject()));
            }
        } catch (Exception e) {
            root.addProperty(name + "_extract_error", true);
            TallyLogger.optionalLog("Failed to extract " + name + " from tracker info: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private interface ExtractFunction {
        JsonObject extract(TrackerInfo info, JsonObject json);
    }

    public JsonObject extractData(TrackerInfo info) {
        JsonObject root = new JsonObject();

        if (info == null) {
            root.add("info_class", JsonNull.INSTANCE);
            return root;
        }

        extractHelper(info instanceof CauseInfo ? info : null, "cause", root, (i, j) -> extractCauseInfo((CauseInfo) i, j));
        extractHelper(info instanceof DamageInfo ? info : null, "damage", root, (i, j) -> extractDamageInfo((DamageInfo) i, j));
        extractHelper(info instanceof FallInfo ? info : null, "fall", root, (i, j) -> extractFallInfo((FallInfo) i, j));
        extractHelper(info instanceof MeleeInfo ? info : null, "melee", root, (i, j) -> extractMeleeInfo((MeleeInfo) i, j));
        extractHelper(info instanceof OwnerInfo ? info : null, "owner", root, (i, j) -> extractOwnerInfo((OwnerInfo) i, j));
        extractHelper(info instanceof PhysicalInfo ? info : null, "physical", root, (i, j) -> extractPhysicalInfo((PhysicalInfo) i, j));
        extractHelper(info instanceof PotionInfo ? info : null, "potion", root, (i, j) -> extractPotionInfo((PotionInfo) i, j));
        extractHelper(info instanceof RangedInfo ? info : null, "ranged", root, (i, j) -> extractRangedInfo((RangedInfo) i, j));

        extractHelper(info instanceof BlockInfo ? info : null, "block", root, (i, j) -> extractBlockInfo((BlockInfo) i, j));
        extractHelper(info instanceof DispenserInfo ? info : null, "dispenser", root, (i, j) -> extractDispenserInfo((DispenserInfo) i, j));
        extractHelper(info instanceof EntityInfo ? info : null, "entity", root, (i, j) -> extractEntityInfo((EntityInfo) i, j));
        extractHelper(info instanceof ExplosionInfo ? info : null, "explosion", root, (i, j) -> extractExplosionInfo((ExplosionInfo) i, j));
        extractHelper(info instanceof FallingBlockInfo ? info : null, "fallingBlock", root, (i, j) -> extractFallingBlockInfo((FallingBlockInfo) i, j));
        extractHelper(info instanceof FallState ? info : null, "fallState", root, (i, j) -> extractFallState((FallState) i, j));
        extractHelper(info instanceof FireInfo ? info : null, "fire", root, (i, j) -> extractFireInfo((FireInfo) i, j));
        extractHelper(info instanceof GenericDamageInfo ? info : null, "genericDamage", root, (i, j) -> extractGenericDamageInfo((GenericDamageInfo) i, j));
        extractHelper(info instanceof GenericFallInfo ? info : null, "genericFall", root, (i, j) -> extractGenericFallInfo((GenericFallInfo) i, j));
        extractHelper(info instanceof GenericPotionInfo ? info : null, "genericPotion", root, (i, j) -> extractGenericPotionInfo((GenericPotionInfo) i, j));
        extractHelper(info instanceof ItemInfo ? info : null, "item", root, (i, j) -> extractItemInfo((ItemInfo) i, j));
        extractHelper(info instanceof MobInfo ? info : null, "mob", root, (i, j) -> extractMobInfo((MobInfo) i, j));
        extractHelper(info instanceof NullDamageInfo ? info : null, "nullDamage", root, (i, j) -> extractNullDamageInfo((NullDamageInfo) i, j));
        extractHelper(info instanceof OwnerInfoBase ? info : null, "ownerBase", root, (i, j) -> extractOwnerInfoBase((OwnerInfoBase) i, j));
        extractHelper(info instanceof PlayerInfo ? info : null, "player", root, (i, j) -> extractPlayerInfo((PlayerInfo) i, j));
        extractHelper(info instanceof ProjectileInfo ? info : null, "projectile", root, (i, j) -> extractProjectileInfo((ProjectileInfo) i, j));
        extractHelper(info instanceof SpleefInfo ? info : null, "spleef", root, (i, j) -> extractSpleefInfo((SpleefInfo) i, j));
        extractHelper(info instanceof ThrownPotionInfo ? info : null, "thrownPotion", root, (i, j) -> extractThrownPotionInfo((ThrownPotionInfo) i, j));
        extractHelper(info instanceof TNTInfo ? info : null, "tnt", root, (i, j) -> extractTNTInfo((TNTInfo) i, j));

        root.addProperty("info_class", info.getClass().getCanonicalName());

        return root;
    }
    
}
