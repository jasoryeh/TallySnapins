package tk.jasonho.tally.snapin;

import com.google.gson.Gson;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import org.bukkit.event.Event;
import org.bukkit.event.world.WorldEvent;
import tc.oc.pgm.api.match.Match;
import tc.oc.pgm.api.match.event.MatchEvent;
import tc.oc.pgm.api.party.Competitor;
import tc.oc.pgm.api.player.MatchPlayer;
import tc.oc.pgm.api.player.MatchPlayerState;
import tc.oc.pgm.controlpoint.events.CapturingTeamChangeEvent;
import tc.oc.pgm.controlpoint.events.ControlPointEvent;
import tc.oc.pgm.controlpoint.events.ControllerChangeEvent;
import tc.oc.pgm.flag.event.FlagCaptureEvent;
import tc.oc.pgm.flag.event.FlagPickupEvent;
import tc.oc.pgm.goals.Contribution;
import tc.oc.pgm.goals.Goal;
import tc.oc.pgm.goals.events.GoalCompleteEvent;
import tc.oc.pgm.goals.events.GoalEvent;
import tc.oc.pgm.stats.PlayerStats;
import tc.oc.pgm.stats.StatsMatchModule;

import java.lang.reflect.Field;
import java.util.Optional;

public class PGMUtils {

    public static JsonObject competitorToData(Competitor competitor) {
        JsonObject jsonObject = new JsonObject();
        if(competitor != null) {
            jsonObject.addProperty("id", competitor.getId());
            jsonObject.addProperty("name", competitor.getDefaultName());
            jsonObject.addProperty("color", competitor.getColor().toString());
            jsonObject.addProperty("tostring", competitor.toString());
        } else {
            jsonObject.add("competitor", JsonNull.INSTANCE);
        }
        return jsonObject;
    }

    public static JsonObject goalToData(Goal goal) {
        JsonObject jsonObject = new JsonObject();
        jsonObject.addProperty("goal", goal.getId());
        jsonObject.addProperty("goal_name", goal.getName());
        jsonObject.addProperty("goal_color", goal.getColor().toString());
        jsonObject.addProperty("goal_tostring", goal.toString());
        return jsonObject;
    }

    public static JsonObject matchPlayerToInfo(MatchPlayer matchPlayer) {
        JsonObject jsonObject = new JsonObject();

        JsonObject coords = new JsonObject();
        coords.add("x", new JsonPrimitive(matchPlayer.getLocation().getX()));
        coords.add("y", new JsonPrimitive(matchPlayer.getLocation().getY()));
        coords.add("z", new JsonPrimitive(matchPlayer.getLocation().getZ()));
        coords.add("yaw", new JsonPrimitive(matchPlayer.getLocation().getYaw()));
        coords.add("pitch", new JsonPrimitive(matchPlayer.getLocation().getPitch()));
        jsonObject.add("location", coords);

        jsonObject.add("gamemode", new JsonPrimitive(matchPlayer.getGameMode().toString()));
        jsonObject.add("alive", new JsonPrimitive(matchPlayer.isAlive()));

        return jsonObject;
    }

    public static JsonObject matchInfo(Match m) {
        JsonObject jsonObject = new JsonObject();

        JsonObject competitors = new JsonObject();
        for (Competitor competitor : m.getCompetitors()) {
            JsonObject people = new JsonObject();
            for (MatchPlayer player : competitor.getPlayers()) {
                people.add(player.getBukkit().getUniqueId().toString(), matchPlayerToInfo(player));
            }
            competitors.add("competitor_" + competitor.getDefaultName().replaceAll(" ", "_"), people);
        }
        jsonObject.add("competitors", competitors);
        jsonObject.add("map", new JsonPrimitive(m.getMap().getName()));
        jsonObject.add("phase", new JsonPrimitive(m.getPhase().toString()));
        return jsonObject;
    }

    public static JsonObject playerStats(PlayerStats playerStat, JsonObject matchData) {
        Gson gson = new Gson();
        JsonObject src = gson.fromJson(gson.toJson(matchData), JsonObject.class);
        JsonObject jsonObject = new JsonObject();

        for (Field declaredField : playerStat.getClass().getDeclaredFields()) {
            declaredField.setAccessible(true);
            try {
                jsonObject.add(declaredField.getName(), new JsonPrimitive(declaredField.get(playerStat).toString()));
            } catch (Exception e) {
                System.out.println("Error capturing PGM stat field: " + declaredField.getName());
                e.printStackTrace();
            }
            declaredField.setAccessible(false);
        }

        src.add("pgm_stats", jsonObject);

        return src;
    }

    public static JsonObject pgmEventToData(Event event) {
        JsonObject jsonObject = new JsonObject();
        jsonObject.addProperty("related_event", event.getClass().getCanonicalName().toLowerCase());
        jsonObject.addProperty("event_name", event.getEventName());

        if(event instanceof WorldEvent) {
            jsonObject.addProperty("world", ((WorldEvent) event).getWorld().getName());
        }

        if(event instanceof MatchEvent) {
            jsonObject.add("match", matchInfo(((MatchEvent) event).getMatch()));
        }

        if(event instanceof GoalEvent) {
            jsonObject.add("competitor", ((GoalEvent) event).getCompetitor() == null ? JsonNull.INSTANCE : competitorToData(((GoalEvent) event).getCompetitor()));

            if(event instanceof GoalCompleteEvent) {
                jsonObject.add("goal_data", goalToData(((GoalCompleteEvent) event).getGoal()));

                JsonObject contributions = new JsonObject();
                int ctrb = 0;
                for (Contribution contribution : ((GoalCompleteEvent) event).getContributions()) {
                    MatchPlayerState playerState = contribution.getPlayerState();
                    Optional<MatchPlayer> player = playerState.getPlayer();
                    contributions.addProperty(
                            (player.isPresent() ? player.get().getBukkit().getUniqueId().toString() : "unknown") + ctrb++,
                            contribution.getPercentage());
                }
                jsonObject.add("goal_contributions", contributions);

                if(event instanceof FlagCaptureEvent) {
                    JsonObject flagData = new JsonObject();
                    flagData.addProperty("flag", ((FlagCaptureEvent) event).getGoal().toString());
                    Competitor flagCaptureTeam = ((FlagCaptureEvent) event).getCompetitor();
                    if(flagCaptureTeam != null) {
                        jsonObject.add("team", competitorToData(flagCaptureTeam));
                    }
                    jsonObject.add("flag_data", flagData);
                }
            }
        }

        if(event instanceof FlagPickupEvent) {
            JsonObject flagData = new JsonObject();
            flagData.addProperty("flag", ((FlagPickupEvent) event).getFlag().toString());
            jsonObject.add("flag_data", flagData);
        }

        if(event instanceof ControlPointEvent) {
            if(event instanceof CapturingTeamChangeEvent) {
                Competitor newTeam = ((CapturingTeamChangeEvent) event).getNewTeam();
                Competitor oldTeam = ((CapturingTeamChangeEvent) event).getOldTeam();
                jsonObject.add("oldteam", competitorToData(oldTeam));
                jsonObject.add("newteam", competitorToData(newTeam));
            }

            if(event instanceof ControllerChangeEvent) {
                Competitor newTeam = ((ControllerChangeEvent) event).getNewController();
                Competitor oldTeam = ((ControllerChangeEvent) event).getOldController();
                jsonObject.add("oldteam", competitorToData(oldTeam));
                jsonObject.add("newteam", competitorToData(newTeam));
            }
        }

        // none match finish

        jsonObject.addProperty("tostring", event.toString());

        return jsonObject;
    }

}
