package games.cubi.raycastedantiesp.paper.utils;

import games.cubi.logs.Logger;
import games.cubi.raycastedantiesp.paper.RaycastedAntiESP;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;

public final class PaperScheduler {
    private PaperScheduler() {}

    public static void runAsync(RaycastedAntiESP plugin, Runnable task) {
        Bukkit.getAsyncScheduler().runNow(plugin, ignored -> task.run());
    }

    public static void runGlobal(RaycastedAntiESP plugin, Runnable task) {
        Bukkit.getGlobalRegionScheduler().run(plugin, ignored -> task.run());
    }

    /**
     * Runs audience work on the owning entity region where one exists, otherwise on the global region.
     */
    public static void runForAudience(RaycastedAntiESP plugin, CommandSender audience, Runnable task) {
        if (audience instanceof Entity entity) {
            Logger.info("[AudienceSchedulerDiagnostic] scheduling entity audience callback.",
                    4, PaperScheduler.class);
            var scheduled = entity.getScheduler().run(plugin, ignored -> {
                Logger.info("[AudienceSchedulerDiagnostic] entity audience callback executed.",
                        4, PaperScheduler.class);
                task.run();
            }, () -> Logger.warning("[AudienceSchedulerDiagnostic] entity audience callback retired before execution.",
                    4, PaperScheduler.class));
            Logger.info("[AudienceSchedulerDiagnostic] entity audience schedule result="
                            + (scheduled == null ? "null" : "scheduled") + ".",
                    4, PaperScheduler.class);
            return;
        }
        Logger.info("[AudienceSchedulerDiagnostic] scheduling non-entity audience callback on global region.",
                4, PaperScheduler.class);
        runGlobal(plugin, task);
    }
}
