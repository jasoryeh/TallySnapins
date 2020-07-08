package tk.jasonho.tally.snapin;

import com.google.common.util.concurrent.AtomicDouble;
import com.google.gson.JsonObject;
import net.avicus.atlas.Atlas;
import net.avicus.atlas.event.match.MatchCloseEvent;
import net.avicus.atlas.event.match.MatchCompleteEvent;
import net.avicus.atlas.event.match.MatchLoadEvent;
import net.avicus.atlas.event.objective.ObjectiveCompleteEvent;
import net.avicus.atlas.event.player.PlayerSpawnCompleteEvent;
import net.avicus.atlas.match.Match;
import net.avicus.atlas.module.groups.Competitor;
import net.avicus.atlas.module.objectives.Objective;
import net.avicus.atlas.sets.competitive.objectives.flag.events.FlagCaptureEvent;
import net.avicus.atlas.sets.competitive.objectives.flag.events.FlagPickupEvent;
import net.avicus.atlas.sets.competitive.objectives.hill.event.HillOwnerChangeEvent;
import net.avicus.libraries.grave.event.PlayerDeathEvent;
import net.avicus.libraries.tracker.Damage;
import net.avicus.libraries.tracker.DamageInfo;
import net.avicus.libraries.tracker.Lifetime;
import net.avicus.libraries.tracker.damage.*;
import net.avicus.libraries.tracker.trackers.base.gravity.Fall;
import org.apache.commons.lang3.tuple.Pair;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.inventory.ItemStack;
import tk.jasonho.tally.core.bukkit.*;
import tk.jasonho.tally.snapin.core.competitive.CompetitiveOperations;
import tk.jasonho.tally.snapin.core.competitive.StatType;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

public class AtlasListener extends TallyListener {

    public AtlasListener(TallyOperationHandler handler) {
        super(handler);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onObjectiveCompleteEvent(ObjectiveCompleteEvent event) {
        List<Player> players = event.getPlayers();
        Objective objective = event.getObjective();
        JsonObject jsonObject = AtlasUtils.atlasEventToData(event, Atlas.getMatch(), objective);
        jsonObject.addProperty("objective", objective.getName().translateDefault());

        players.forEach(p -> {
            super.operationHandler.track("objectivecompleteevent", null, p.getUniqueId(), jsonObject);
        });
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHillOwnerChange(HillOwnerChangeEvent event) {
        Optional<Competitor> newOwner = event.getNewOwner();
        Optional<Competitor> oldOwner = event.getOldOwner();
        Objective objective = event.getObjective();
        JsonObject jsonObject = AtlasUtils.atlasEventToData(event, Atlas.getMatch(), objective);

        newOwner.ifPresent(c -> {
            c.getPlayers().forEach(p -> {
                super.operationHandler.track("objective_hillcapture_win", null, p.getUniqueId(), jsonObject);
            });
        });
        oldOwner.ifPresent(c -> {
            c.getPlayers().forEach(p -> {
                super.operationHandler.track("objective_hillcapture_lose", null, p.getUniqueId(), jsonObject);
            });
        });
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFlagCapture(FlagCaptureEvent event) {
        List<Player> players = event.getPlayers();
        JsonObject jsonObject = AtlasUtils.atlasEventToData(event, Atlas.getMatch(), event.getObjective());
        players.forEach(p -> {
            super.operationHandler.track("flag_capture", null, p.getUniqueId(), jsonObject);
        });
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFlagPickup(FlagPickupEvent event) {
        Player player = event.getPlayer();
        JsonObject jsonObject = AtlasUtils.atlasEventToData(event, Atlas.getMatch(), event.getObjective());
        super.operationHandler.track("flag_carry", null, player.getUniqueId(), jsonObject);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player killed = event.getPlayer();
        Match match = Atlas.getMatch();
        Lifetime lifetime = event.getLifetime();
        Damage lastDamage = lifetime.getLastDamage();

        StringJoiner cause = new StringJoiner(".");
        UUID killer;
        if(lastDamage == null) {
            killer = DamageTrackModule.UNKNOWN;
            cause.add("unknown");
        } else {
            DamageInfo info = lastDamage.getInfo();

            LivingEntity resolvedDamager = info.getResolvedDamager();
            if(resolvedDamager != null) {
                if(!(resolvedDamager instanceof Player)) {
                    if(info instanceof OwnedMobDamageInfo) {
                        OwnedMobDamageInfo ownedInfo = (OwnedMobDamageInfo) info;
                        cause.add("ownedentity");

                        Player mobOwner = ownedInfo.getMobOwner();
                        if(mobOwner != null) {
                            killer = mobOwner.getUniqueId();
                        } else {
                            killer = DamageTrackModule.UNKNOWN;
                        }
                    } else {
                        killer = DamageTrackModule.ENVIRONMENT;
                        cause.add("entity");
                    }
                    cause.add(resolvedDamager.getType().name().toLowerCase().replaceAll("_", "-"));
                } else {
                    if(resolvedDamager instanceof Player) {
                        killer = resolvedDamager.getUniqueId();
                    } else {
                        killer = DamageTrackModule.UNKNOWN;
                    }

                    if(info instanceof AnvilDamageInfo) {
                        cause.add("anvil");
                    } else if(info instanceof ExplosiveDamageInfo) {
                        cause.add("explosive");
                    } else if(info instanceof GravityDamageInfo) {
                        cause.add("gravity");

                        GravityDamageInfo gravityInfo = (GravityDamageInfo) info;
                        Fall.Cause fallCause = gravityInfo.getCause();
                        Fall.From fallFrom = gravityInfo.getFrom();

                        if(fallCause == Fall.Cause.HIT) {
                            cause.add("hit");
                        } else if(fallCause == Fall.Cause.SHOOT) {
                            Damage projectileDamage = lifetime.getLastDamage(ProjectileDamageInfo.class);
                            cause.add("shot-dist-" + projectileDamage != null ?
                                    ((ProjectileDamageInfo) projectileDamage.getInfo()).getDistance() + "" :
                                    "unknown");
                        } else if(fallCause == Fall.Cause.SPLEEF) {
                            cause.add("spleefed");
                        }

                        if(fallFrom == Fall.From.FLOOR) {
                            cause.add("off-floor");
                        } else if(fallFrom == Fall.From.LADDER) {
                            cause.add("off-ladder");
                        } else if(fallFrom == Fall.From.WATER) {
                            cause.add("off-water");
                        }

                        if(event.getLocation().getY() < 0) cause.add("into-void");
                    } else if(info instanceof MeleeDamageInfo) {
                        cause.add("melee");

                        MeleeDamageInfo meleeInfo = (MeleeDamageInfo) info;
                        Material weapon = meleeInfo.getWeapon();

                        if(weapon == Material.AIR) {
                            cause.add("fist");
                        } else {
                            ItemStack weaponStack = meleeInfo.getWeaponStack();
                            cause.add(weapon.name().toLowerCase().replaceAll("_", "-"));
                            if(weaponStack != null && weaponStack.getItemMeta().hasDisplayName()) {
                                cause.add(weaponStack.getItemMeta().getDisplayName());
                            }
                        }
                    } else if(info instanceof ProjectileDamageInfo) {
                        cause.add("projectile");
                        ProjectileDamageInfo projectileInfo = (ProjectileDamageInfo) info;

                        cause.add(projectileInfo.getProjectile().getType().name().replaceAll("_", "-"));
                        cause.add("shot-dist-" +
                                (resolvedDamager != null ? lastDamage.getLocation().distance(resolvedDamager.getLocation()) : "unknown"));
                    } else if(info instanceof VoidDamageInfo) {
                        cause.add("void");
                    } else {
                        cause.add("unknown");
                    }
                }
            } else {
                killer = DamageTrackModule.ENVIRONMENT;
                if(info instanceof AnvilDamageInfo) {
                    cause.add("anvil");
                } else if(info instanceof BlockDamageInfo) {
                    cause.add("block");
                } else if(info instanceof ExplosiveDamageInfo) {
                    cause.add("explosive");
                } else if(info instanceof FallDamageInfo) {
                    cause.add("fall-dist-" + ((FallDamageInfo) info).getFallDistance());
                } else if(info instanceof LavaDamageInfo) {
                    cause.add("lava");
                } else if(info instanceof VoidDamageInfo) {
                    cause.add("void");
                } else if(info instanceof ProjectileDamageInfo) {
                    cause.add("projectile");
                } else {
                    cause.add("unknown");
                }
            }
        }

        // Assists
        CompetitiveOperations operationHandler = ((CompetitiveOperations) super.operationHandler);
        DamageTrackModule damageTrackModule = operationHandler.getTally().getDamageTrackModule();
        List<DamageTrackModule.DamageExchange> damageExchanges = damageTrackModule.getDamageExchanges();

        Map<UUID, Pair<AtomicDouble, AtomicInteger>> assisters = new HashMap<>();
        for (DamageTrackModule.DamageExchange exc : damageExchanges) {
            if(exc.getDirection() == DamageTrackModule.DamageDirection.GIVE && exc.getYou() == killed.getUniqueId()) {
                if(exc.getMe() == killer // is the killer
                        || exc.getMe() == killed.getUniqueId() // or is suicide
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
        operationHandler.trackPVPTransaction(killer, killed.getUniqueId(), cause.toString());

        JsonObject jsonObject = AtlasUtils.atlasEventToData(event, Atlas.getMatch(), null);
        jsonObject.addProperty("caused_by_type", cause.toString());
        jsonObject.addProperty("assisted", killer.toString());

        for (Map.Entry<UUID, Pair<AtomicDouble, AtomicInteger>> assist : assisters.entrySet()) {
            Player assister = Bukkit.getPlayer(assist.getKey());
            if(assister != null) {
                operationHandler.track(StatType.ASSIST, killed.getUniqueId(), assister.getUniqueId(), jsonObject);
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMatchHi(MatchLoadEvent e) {
        Match match = e.getMatch();
        super.operationHandler
                .getTally()
                .loadTracker(
                        new DefaultDamageTrackModule(
                                super.operationHandler,
                                match.getWorld().getName() + "-" + e.getMatch().getId()));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMatchBye(MatchCloseEvent e) {
        super.operationHandler.getTally().unloadTracker();
        // handle close
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSpawn(PlayerSpawnCompleteEvent e) {
        // Reset damage tracker
        ((CompetitiveOperations) super.operationHandler).handleRespawn(e.getPlayer().getUniqueId());
    }

    // Extra stats
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onWin(MatchCompleteEvent event) {
        CompetitiveOperations operationHandler = (CompetitiveOperations) super.operationHandler;
        JsonObject extras = AtlasUtils.atlasEventToData(event, event.getMatch(), null);

        boolean inconclusive = event.getWinners().size() == 0;

        List<UUID> winners = event.getWinners()
                .stream()
                .flatMap(c -> c.getPlayers().stream())
                .map(Player::getUniqueId)
                .collect(Collectors.toList());
        List<UUID> losers = event.getMatch()
                .getPlayers()
                .stream()
                .filter(p -> !winners.contains(p.getUniqueId()))
                .map(Player::getUniqueId)
                .collect(Collectors.toList());
        List<UUID> observers = event.getMatch()
                .getPlayers()
                .stream()
                .filter(p -> !(winners.contains(p.getUniqueId()) || losers.contains(p.getUniqueId())))
                .map(Player::getUniqueId)
                .collect(Collectors.toList());

        operationHandler.trackMatchParticipants(winners, losers, observers,
                event.getMatch().getPlayers()
                        .stream().map(Player::getUniqueId).collect(Collectors.toList()),
                inconclusive, extras);
    }
}
