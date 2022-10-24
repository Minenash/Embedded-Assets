# Embedded Assets

A fabric mod that automatically loads ResourcePacks from DataPacks on load. Even works on servers.

## Can you be more specific?

### Client
When you enter a single-player world with supported datapacks enabled, the resource packs contained within them are
automatically added to you resource pack list. You can enable/disable and reorder the packs in the resource pack screen.

### Server
Since the server can only send the client a single datapack, this mod merges all the resource packs that are within
the datapacks into a single resource pack. It can also optionally merge it with another resource pack ("base pack") so
you don't lose you're existing server resource pack.

Use the `/embedded_assets` command to configure pack priority, enable/disable certain resource packs without disabling
the datapack, and to configure the pack hosting server. 

#### Pack Hosting Server
If enabled, this mod will also host the resource pack for you, so you don't have to manually upload the file somewhere
everytime you add a new datapack. Run `/embedded_assets pack_hosting` for the status of the server and th commands to
control and configure it.

## What datapacks are supported?

### Mixed-packs
Packs that have both a `data` folder and an `assets` folder. These packs contain both sides so that the same zip
can be placed in the datapacks folder and the resource packs folder. With this mod ofc, you only need to put it in the
datapack folder.

### Resource Pack Zips at Datapack root
Any resource pack zip located at the root of the datapack will be loaded