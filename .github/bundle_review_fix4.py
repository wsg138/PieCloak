from pathlib import Path

p = Path("packetevents/src/main/java/games/cubi/raycastedantiesp/packetevents/viewcontrollers/PacketEventsBlockViewController.java")
text = p.read_text()
old = '''        scheduleVisibilityRepairsAfterSend(event, viewer, playerData, blockView,
                viewerUUID, currentTick, requestedTileChecksEnabled, withinBundle, bundleDelimiter);
'''
new = '''        scheduleVisibilityRepairsAfterSend(event, viewer, playerData, blockView,
                viewerUUID, currentTick, worldEpoch, requestedTileChecksEnabled, withinBundle, bundleDelimiter);
'''
if old not in text:
    raise RuntimeError("block scheduling call marker missing")
text = text.replace(old, new, 1)
old = '''            UUID viewerUUID,
            int currentTick,
            boolean requestedTileChecksEnabled,
'''
new = '''            UUID viewerUUID,
            int currentTick,
            int expectedWorldEpoch,
            boolean requestedTileChecksEnabled,
'''
if old not in text:
    raise RuntimeError("block scheduling signature marker missing")
text = text.replace(old, new, 1)
old = '''        event.getTasksAfterSend().add(() -> {
            if (modeChangeAfterDelimiter) {
'''
new = '''        event.getTasksAfterSend().add(() -> {
            if (!isCurrentCallbackWorldEpoch(expectedWorldEpoch, playerData.acquireWorldEpoch())) {
                return;
            }
            if (modeChangeAfterDelimiter) {
'''
if old not in text:
    raise RuntimeError("block after-send callback marker missing")
text = text.replace(old, new, 1)
marker = '''    static boolean tileChecksEnabledForViewer(boolean configuredEnabled, boolean hasBypassPermission) {
'''
helper = '''    static boolean isCurrentCallbackWorldEpoch(int expectedWorldEpoch, int currentWorldEpoch) {
        return PlayerData.isStableWorldEpoch(currentWorldEpoch) && expectedWorldEpoch == currentWorldEpoch;
    }

'''
if marker not in text:
    raise RuntimeError("block helper insertion marker missing")
text = text.replace(marker, helper + marker, 1)
p.write_text(text)

p = Path("packetevents/src/test/java/games/cubi/raycastedantiesp/packetevents/viewcontrollers/PacketEventsBlockViewControllerTest.java")
text = p.read_text()
marker = '''    @Test
    void bypassViewersDisableTileChecksWithoutChangingGlobalConfig() {
'''
tests = '''    @Test
    void afterSendRepairsRequireSameStableWorldEpoch() {
        assertTrue(PacketEventsBlockViewController.isCurrentCallbackWorldEpoch(2, 2));
        assertFalse(PacketEventsBlockViewController.isCurrentCallbackWorldEpoch(2, 4));
        assertFalse(PacketEventsBlockViewController.isCurrentCallbackWorldEpoch(2, 3));
        assertFalse(PacketEventsBlockViewController.isCurrentCallbackWorldEpoch(2, 1));
    }

'''
if marker not in text:
    raise RuntimeError("block controller test insertion marker missing")
text = text.replace(marker, tests + marker, 1)
p.write_text(text)
print("Block after-send world-epoch fence applied")
