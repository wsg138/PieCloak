from pathlib import Path

p = Path("packetevents/src/test/java/games/cubi/raycastedantiesp/packetevents/viewcontrollers/BlockTransitionRetryQueueTest.java")
text = p.read_text()
old = '''        assertFalse(queue.enqueue(request(viewer, tile, BlockTransitionRetryQueue.Operation.SHOW,
                BlockTransitionRetryQueue.Stage.BLOCK, 1, 2, 0, 0)));
'''
new = '''        assertFalse(queue.enqueue(new BlockTransitionRetryQueue.RetryRequest(
                viewer, SHOW, BLOCK, tile, 1, 2, 7L, 1, 0)));
'''
if old not in text:
    raise RuntimeError("generated pending-predicate test not found")
p.write_text(text.replace(old, new, 1))
print("Bundle retry queue test fixed")
