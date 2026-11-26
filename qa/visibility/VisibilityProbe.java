package qa.visibility;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Cow;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import java.util.List;

public final class VisibilityProbe extends JavaPlugin {
    private Villager target;
    private Cow control;
    private Player viewer;
    private Player witness;
    private World world;
    private static final int X=32, Y=80, Z=0;

    @Override public void onEnable() {
        getLogger().info("VIS_QA READY");
        new BukkitRunnable() {
            @Override public void run() {
                if (Bukkit.getOnlinePlayers().size()!=2) return;
                cancel();
                try { setup(); } catch (Exception e) { fail(e); }
            }
        }.runTaskTimer(this, 20L,20L);
    }
    private void fail(Exception e) {
        getLogger().severe("VIS_QA FAIL "+e);
        e.printStackTrace();
    }
    private void later(long delay, Runnable action) {
        Bukkit.getScheduler().runTaskLater(this, () -> {
            try { action.run(); } catch (Exception e) { fail(e); }
        }, delay);
    }
    private void setup() {
        viewer=Bukkit.getPlayerExact("FancyQaA");
        witness=Bukkit.getPlayerExact("FancyQaB");
        world=viewer.getWorld();
        for(int x=-3;x<=36;x++) for(int z=-5;z<=5;z++) world.getBlockAt(x,Y-1,z).setType(Material.STONE,false);
        for(int x=12;x<=15;x++) for(int y=Y;y<=Y+5;y++) for(int z=-5;z<=5;z++) world.getBlockAt(x,y,z).setType(Material.STONE,false);
        world.getBlockAt(X,Y,Z+2).setType(Material.WHITE_SHULKER_BOX,false);
        world.getBlockAt(X,Y,Z-2).setType(Material.CHEST,false);
        viewer.teleport(new Location(world,0.5,Y,0.5));
        witness.teleport(new Location(world,30.5,Y,0.5));
        later(100, this::spawn);
    }
    private void spawn() {
        target=world.spawn(new Location(world,X+0.5,Y,Z+0.5),Villager.class,v->{v.setAI(false);v.setGravity(false);v.setInvulnerable(true);});
        control=world.spawn(new Location(world,X+0.5,Y,Z+1.5),Cow.class,v->{v.setAI(false);v.setGravity(false);v.setInvulnerable(true);});
        getLogger().info("VIS_QA IDS "+target.getEntityId()+" "+control.getEntityId());
        later(120,()->{
            if(!target.isValid()||!control.isValid())throw new IllegalStateException("server entity removed");
            getLogger().info("VIS_QA HIDDEN");
            later(100,()->{
                getLogger().info("VIS_QA REVEAL");
                viewer.teleport(new Location(world,28.5,Y,0.5));
                later(120,()->{
                    if(!target.isValid()||!control.isValid())throw new IllegalStateException("server entity removed during reveal");
                    if(world.getBlockAt(X,Y,Z+2).getType()!=Material.WHITE_SHULKER_BOX)throw new IllegalStateException("real block was altered");
                    getLogger().info("VIS_QA DONE");
                });
            });
        });
    }
}
