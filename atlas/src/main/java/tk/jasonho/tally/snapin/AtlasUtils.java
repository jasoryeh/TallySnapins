package tk.jasonho.tally.snapin;

import com.google.gson.JsonArray;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import net.avicus.atlas.match.Match;
import net.avicus.atlas.module.groups.Competitor;
import net.avicus.atlas.module.groups.Group;
import net.avicus.atlas.module.groups.GroupsModule;
import net.avicus.atlas.module.objectives.Objective;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.world.WorldEvent;

public class AtlasUtils {

    public static JsonObject competitorToData(Competitor competitor) {
        JsonObject jsonObject = new JsonObject();
        if(competitor != null) {
            jsonObject.addProperty("id", competitor.getId());
            jsonObject.addProperty("name", competitor.getName().translateDefault());
            jsonObject.addProperty("color", competitor.getTeamColor().getName());
            jsonObject.addProperty("tostring", competitor.toString());
        } else {
            jsonObject.add("competitor", JsonNull.INSTANCE);
        }
        return jsonObject;
    }

    public static JsonObject objectiveToData(Objective objective) {
        JsonObject jsonObject = new JsonObject();
        jsonObject.addProperty("objective", objective.getName().translateDefault());
        jsonObject.addProperty("objective_tostring", objective.toString());
        return jsonObject;
    }

    public static JsonObject matchInfo(Match m) {
        JsonObject jsonObject = new JsonObject();
        jsonObject.addProperty("match", m.getId());
        jsonObject.addProperty("match_world", m.getWorld().getName());

        JsonObject groups = new JsonObject();
        for (Group group : m.getRequiredModule(GroupsModule.class).getGroups()) {
            JsonArray people = new JsonArray();
            for (Player player : group.getPlayers()) {
                people.add(new JsonPrimitive(player.getUniqueId().toString()));
            }
            groups.add("group_" + group.getName().translateDefault().replaceAll(" ", "_"), people);
        }
        jsonObject.add("groups", groups);
        return jsonObject;
    }

    public static JsonObject atlasEventToData(Event event, Match match, Objective objective) {
        JsonObject jsonObject = new JsonObject();
        jsonObject.addProperty("related_event", event.getClass().getCanonicalName().toLowerCase());
        jsonObject.addProperty("event_name", event.getEventName());

        if(event instanceof WorldEvent) {
            jsonObject.addProperty("world", ((WorldEvent) event).getWorld().getName());
        }

        if(match != null) {
            jsonObject.add("match", matchInfo(match));
        }

        if(objective != null) {
            jsonObject.add("objective", objectiveToData(objective));
            jsonObject.addProperty("objective_detail", objective.getClass().getSimpleName().toLowerCase());
        }

        // none match finish

        jsonObject.addProperty("tostring", event.toString());
        return jsonObject;
    }

}
