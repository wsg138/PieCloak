/*
 * SPDX-License-Identifier: AGPL-3.0-only
 * Copyright © 2025-2026 Cubicake and Contributors.
 * This file is part of PieCloak, a modified fork of RaycastedAntiESP.
 */

package games.cubi.raycastedantiesp.paper.commands;

import net.strokkur.commands.Command;
import net.strokkur.commands.Executes;
import net.strokkur.commands.arguments.StringArg;
import net.strokkur.commands.arguments.StringArgType;
import org.bukkit.command.CommandSender;

@Command("piecloak")
public class SourceCommand {
    public static final String SOURCE_URL = "https://github.com/wsg138/PieCloak";

    @Executes("source")
    public void source(CommandSender sender) {
        sendSourceLink(sender);
    }

    @Executes("inspect")
    public void inspect(@StringArg(StringArgType.STRING) String viewer, int entityId, CommandSender sender) {
        if (!canInspect(sender)) {
            return;
        }
        EntityVisibilityInspector.inspect(sender, viewer, entityId);
    }

    @Executes("inspectblock")
    public void inspectBlock(@StringArg(StringArgType.STRING) String viewer,
            int x, int y, int z, CommandSender sender) {
        if (!canInspect(sender)) {
            return;
        }
        BlockVisibilityInspector.inspect(sender, viewer, x, y, z);
    }

    private static boolean canInspect(CommandSender sender) {
        if (sender.hasPermission("raycastedantiesp.command")) {
            return true;
        }
        sender.sendRichMessage("<red>You do not have permission to inspect PieCloak visibility state.");
        return false;
    }

    static void sendSourceLink(CommandSender sender) {
        sender.sendRichMessage("<white>PieCloak source: <u><blue><hover:show_text:'Click to view source'><click:open_url:'"
                + SOURCE_URL + "'>" + SOURCE_URL + "</click></hover></blue></u>");
    }
}
