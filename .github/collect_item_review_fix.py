from pathlib import Path

p = Path("packetevents/src/main/java/games/cubi/raycastedantiesp/packetevents/viewcontrollers/PacketEventsEntityViewController.java")
text = p.read_text()
old = '''            case PacketType.Play.Server.ENTITY_ANIMATION -> {
'''
new = '''            case PacketType.Play.Server.COLLECT_ITEM -> {
                WrapperPlayServerCollectItem packet = new WrapperPlayServerCollectItem(event);
                if (shouldSuppressCollectItem(
                        playerData, packet.getCollectedEntityId(), packet.getCollectorEntityId())) {
                    event.setCancelled(true);
                }
            }
            case PacketType.Play.Server.ENTITY_ANIMATION -> {
'''
if old not in text:
    raise RuntimeError("collect-item switch marker missing")
text = text.replace(old, new, 1)
marker = '''    static void applyTrackedMetadata(NettyEntity<?> entity, List<EntityData<?>> metadata) {
'''
helper = '''    static boolean shouldSuppressCollectItem(
            PlayerData playerData, int collectedEntityID, int collectorEntityID) {
        return isHiddenCollectItemReference(playerData, collectedEntityID)
                || isHiddenCollectItemReference(playerData, collectorEntityID);
    }

    private static boolean isHiddenCollectItemReference(PlayerData playerData, int entityID) {
        if (playerData.nettyData().isSelfEntityID(entityID)) {
            return false;
        }
        NettyEntity<?> entity = null;
        if (playerData.entityView().exists(entityID)) {
            entity = (NettyEntity<?>) playerData.entityView().getEntity(entityID);
        } else if (playerData.playerView().exists(entityID)) {
            entity = (NettyEntity<?>) playerData.playerView().getEntity(entityID);
        }
        return shouldSuppressCollectItemReference(entity);
    }

    static boolean shouldSuppressCollectItemReference(NettyEntity<?> entity) {
        return entity != null && (!entity.visible() || !entity.clientVisible());
    }

'''
if marker not in text:
    raise RuntimeError("collect-item helper marker missing")
text = text.replace(marker, helper + marker, 1)
p.write_text(text)

p = Path("packetevents/src/test/java/games/cubi/raycastedantiesp/packetevents/viewcontrollers/PacketEventsEntityViewControllerTest.java")
text = p.read_text()
marker = '''    @Test
    void sharedEntityFlagsUpdateSneakingAndGlowingState() {
'''
tests = '''    @Test
    void collectItemSuppressesHiddenOrNotYetClientVisibleReferences() {
        PacketEventsEntity entity = entity();
        entity.setVisible(true);
        entity.setClientVisible(true);
        assertFalse(PacketEventsEntityViewController.shouldSuppressCollectItemReference(entity));

        entity.setVisible(false);
        assertTrue(PacketEventsEntityViewController.shouldSuppressCollectItemReference(entity));

        entity.setVisible(true);
        entity.setClientVisible(false);
        assertTrue(PacketEventsEntityViewController.shouldSuppressCollectItemReference(entity));

        assertFalse(PacketEventsEntityViewController.shouldSuppressCollectItemReference(null));
    }

'''
if marker not in text:
    raise RuntimeError("collect-item test marker missing")
text = text.replace(marker, tests + marker, 1)
p.write_text(text)
print("COLLECT_ITEM visibility suppression applied")
