# Embedded Assets

[![fabric-api](https://cdn.jsdelivr.net/npm/@intergrav/devins-badges@2/assets/cozy/requires/fabric-api_64h.png)](https://modrinth.com/mod/fabric-api) 
[![discord](https://cdn.jsdelivr.net/npm/@intergrav/devins-badges@2/assets/cozy/social/discord-singular_64h.png)](https://discord.com/invite/eYf7DDHhvN) 
[![kofi](https://cdn.jsdelivr.net/npm/@intergrav/devins-badges@2/assets/cozy/donate/kofi-singular-alt_64h.png)](https://ko-fi.com/minenash) \
A fabric mod that automatically loads Resource Packs embedded within Datapacks on load. For servers, it'll merge the packs and send it to the client.

## Can you be more specific?

### Client
When you enter a single-player world with supported datapacks enabled, the resource packs contained within them are
automatically added to you resource pack list. You can enable/disable and reorder the packs in the resource pack screen.

### Server
Since the server can only send the client a single resource pack, this mod merges all the resource packs that are within
the datapacks into a single pack. It can also optionally merge it with another resource pack ("base pack") so
you don't lose your existing server resource pack.

Use the `/embedded_assets` command to configure pack priority, enable/disable certain resource packs without disabling
the datapack, and to configure the pack hosting server.

#### Pack Hosting Server
If enabled, this mod will also host the resource pack for you, so you don't have to manually upload the file somewhere
everytime you add a new datapack. Run `/embedded_assets pack_hosting` for the status of the server and the commands to
control and configure it.

## What datapacks are supported?

### Mixed-packs
Packs that have both a `data` folder and an `assets` folder. These packs contain both sides so that the same zip
can be placed in the datapacks folder and the resource packs folder. With this mod ofc, you only need to put it in the
datapack folder.

### Resource Pack Zips at Datapack root
Any resource pack zip located at the root of the datapack will be loaded

## FAQ

### Q: Will this mod be ported to Forge?
* For the first time ever, the answer is **Yes!**

### Q: What about Spigot/Paper/etc?
* Surprisingly, **also Yes!!**apack will be loaded
