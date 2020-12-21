# Tally Snap Ins
What is this?
- I originally created Tally for Atlas, but as time rolled by I wanted to use this for multiple things such as SkyBlock, PGM, and even normal survival
- Modifying plugins are especially tedious when using different forks of Minecraft servers.
- Exercise of my ability to use Maven
- Instead of a super project, the original end goal is to create a central database for game statistics that game servers can control, and the public can search on their own.

Why?
- Tally was originally designed for cross platform use, therefore the snapins are for multiple *non-Minecraft* games to possibly contribute stats to a large central database rather than having a custom database for every game and every platform.

## SnapIn Requirements
A SnapIn is a plugin that attaches to TallyLib, the main plugin responsible for communications with a API server. In order to register a SnapIn (which doesn't need to be a independent plugin), you simply need to get the instance of Tally, and register a TallyListener, which is essentially a normal Bukkit listener.

See [here](https://github.com/jasoryeh/TallySnapins/blob/179f6641ccac87c059eb119ab8aa5eab3ca84b67/atlas/src/main/java/tk/jasonho/tally/snapin/TallySnapin.java#L21) for an example where default event listeners that ship with the library aren't necessary.

### Updates
Updates will be provided in my free time from school. Feel free to make pull requests.
