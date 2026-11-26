package qa.fancymanager;

import de.oliver.fancynpcs.api.FancyNpcsPlugin;
import de.oliver.fancynpcs.api.Npc;
import de.oliver.fancynpcs.api.NpcData;
import de.oliver.fancynpcs.api.events.NpcModifyEvent;
import games.cubi.raycastedantiesp.core.entity.EntityBypassRegistry;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Probe only, never deployed. Intentionally retain old NPC references. */
public final class ManagerProbe extends JavaPlugin {
    private final List<Npc> retained = new ArrayList<>();

    @Override public void onEnable() {
        new BukkitRunnable() {
            @Override public void run() {
                if (!FancyNpcsPlugin.get().getNpcManager().isLoaded() || Bukkit.getWorlds().isEmpty()) return;
                cancel();
                guarded(ManagerProbe.this::direct);
            }
        }.runTaskTimer(this,20L,20L);
    }
    private Npc create(String name, Location location, boolean save) {
        NpcData data = new NpcData(name,UUID.randomUUID(),location);
        data.setType(EntityType.VILLAGER);
        Npc npc=FancyNpcsPlugin.get().getNpcAdapter().apply(data);
        npc.create();
        npc.setSaveToFile(save);
        FancyNpcsPlugin.get().getNpcManager().registerNpc(npc);
        retained.add(npc);
        return npc;
    }
    private void modify(Npc npc) {
        new NpcModifyEvent(npc,NpcModifyEvent.NpcModification.TYPE,EntityType.VILLAGER,Bukkit.getConsoleSender()).callEvent();
    }
    private void direct() {
        var manager=FancyNpcsPlugin.get().getNpcManager();
        Location loc=Bukkit.getWorlds().getFirst().getSpawnLocation();
        Npc npc=create("qa_direct",loc,false);
        modify(npc);
        int id=npc.getEntityId();
        require(bypassed(id),"direct precondition");
        npc.removeForAll();
        manager.removeNpc(npc);
        require(manager.getNpc("qa_direct")==null,"direct manager removal");
        later(100,()->{
            marker("DIRECT_STALE "+bypassed(id));
            npc.create();
            modify(npc);
            marker("DIRECT_LATE "+bypassed(npc.getEntityId()));
            reload(loc);
        });
    }
    private void reload(Location loc) {
        var manager=FancyNpcsPlugin.get().getNpcManager();
        Npc npc=create("qa_reload",loc,true);
        modify(npc);
        int id=npc.getEntityId();
        require(bypassed(id),"reload precondition");
        manager.saveNpcs(true);
        manager.reloadNpcs();
        later(100,()->{
            Npc replacement=manager.getNpc("qa_reload");
            require(replacement!=null && replacement!=npc,"reload replaced object");
            retained.add(replacement);
            modify(replacement);
            marker("RELOAD_STALE "+bypassed(id));
            marker("RELOAD_NEW "+bypassed(replacement.getEntityId()));
            npc.create();
            modify(npc);
            marker("RELOAD_LATE "+bypassed(npc.getEntityId()));
            replacement.removeForAll();
            manager.removeNpc(replacement);
            marker("READY");
            waitForViewers();
        });
    }
    private void waitForViewers() {
        new BukkitRunnable(){
            @Override public void run(){
                if(Bukkit.getOnlinePlayers().size()!=2)return;
                cancel();
                guarded(()->{
                    List<? extends Player> players=List.copyOf(Bukkit.getOnlinePlayers());
                    Location loc=players.getFirst().getLocation();
                    players.get(1).teleport(loc);
                    later(40,()->live(loc.clone().add(0,0,2)));
                });
            }
        }.runTaskTimer(this,10L,10L);
    }
    private void live(Location loc) {
        Npc npc=create("qa_live_direct",loc,false);
        int id=npc.getEntityId();
        npc.spawnForAll();
        later(100,()->{
            require(bypassed(id),"live precondition");
            marker("LIVE_ID "+id);
            npc.removeForAll();
            FancyNpcsPlugin.get().getNpcManager().removeNpc(npc);
            later(100,()->{
                marker("LIVE_STALE "+bypassed(id));
                marker("DONE");
            });
        });
    }
    private boolean bypassed(int id){return EntityBypassRegistry.isBypassed(id);}
    private void later(long ticks,Runnable r){Bukkit.getScheduler().runTaskLater(this,()->guarded(r),ticks);}
    private void require(boolean value,String reason){if(!value)throw new IllegalStateException(reason);}
    private void guarded(Runnable r){try{r.run();}catch(Throwable t){marker("FAIL "+t);t.printStackTrace();}}
    private void marker(String s){getLogger().info("MGR_QA "+s);}
}
