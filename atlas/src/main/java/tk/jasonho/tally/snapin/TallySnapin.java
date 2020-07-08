package tk.jasonho.tally.snapin;

import tk.jasonho.tally.core.bukkit.TallyOperationHandler;
import tk.jasonho.tally.core.bukkit.TallyPlugin;
import tk.jasonho.tally.snapin.core.competitive.CompetitiveOperations;
import tk.jasonho.tally.snapin.core.competitive.Snapin;

public class TallySnapin extends Snapin {

    public TallyPlugin tallyInstance;
    public TallyOperationHandler operationHandler;
    public AtlasListener atlasListener;

    @Override
    public void onEnable() {
        this.getLogger().info("Tally Snap-in for Atlas is loading...");
        this.tallyInstance = TallyPlugin.getInstance();
        this.operationHandler = new CompetitiveOperations(tallyInstance);

        this.atlasListener = new AtlasListener(this.operationHandler);
        this.tallyInstance.registerTallyListener(this.atlasListener, this);
        this.tallyInstance.unregisterTallyListener(this.tallyInstance.getCombatListener()); // we have a different way of listening for deaths

        this.getLogger().info("Tally Snap-in for Atlas loaded.");
    }

    @Override
    public void onDisable() {
        this.tallyInstance.unregisterTallyListener(this.atlasListener);
    }
}
