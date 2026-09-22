from pathlib import Path

p = Path("packetevents/src/main/java/games/cubi/raycastedantiesp/packetevents/viewcontrollers/BlockTransitionRetryQueue.java")
text = p.read_text()
duplicate = '''    boolean hasPending(UUID viewerUUID) {
        synchronized (this) {
            LinkedHashMap<Key, Retry> retries = retriesByViewer.get(viewerUUID);
            return retries != null && !retries.isEmpty();
        }
    }

'''
if duplicate not in text:
    raise RuntimeError("generated duplicate hasPending method not found")
p.write_text(text.replace(duplicate, "", 1))
print("Removed redundant generated hasPending method")
