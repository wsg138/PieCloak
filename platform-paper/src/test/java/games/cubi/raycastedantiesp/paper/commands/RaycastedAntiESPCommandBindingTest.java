package games.cubi.raycastedantiesp.paper.commands;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RaycastedAntiESPCommandBindingTest {
    @Test
    void benchmarkUsesInjectedCommandSenderInsteadOfPlayerArgument() throws NoSuchMethodException {
        assertNotNull(RaycastedAntiESPCommand.class.getDeclaredMethod(
                "benchmarkCommand", int.class, int.class, CommandSender.class));
        assertThrows(NoSuchMethodException.class, () -> RaycastedAntiESPCommand.class.getDeclaredMethod(
                "benchmarkCommand", int.class, int.class, Player.class));
    }
}
