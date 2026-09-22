from pathlib import Path

p = Path("core/src/test/java/games/cubi/raycastedantiesp/core/view/controller/PacketEntityViewControllerTest.java")
text = p.read_text()

old = '''class PacketEntityViewControllerTest {
    private static final HarnessController CONTROLLER = new HarnessController();
'''
new = '''class PacketEntityViewControllerTest {
    private static final HarnessVisibilityPolicy VISIBILITY_POLICY = new HarnessVisibilityPolicy();
    private static final HarnessController CONTROLLER = new HarnessController(VISIBILITY_POLICY);
'''
if old not in text:
    raise RuntimeError("controller declaration not found")
text = text.replace(old, new, 1)

old = '''        CONTROLLER.directlyShownEntityIDs.clear();
        CONTROLLER.directlyHiddenEntityIDs.clear();
        CONTROLLER.replacementPassengerPackets.clear();
'''
new = '''        CONTROLLER.directlyShownEntityIDs.clear();
        CONTROLLER.directlyHiddenEntityIDs.clear();
        CONTROLLER.replacementPassengerPackets.clear();
        CONTROLLER.movementEntityID = -1;
        VISIBILITY_POLICY.disable();
'''
if old not in text:
    raise RuntimeError("before-each block not found")
text = text.replace(old, new, 1)

text = text.replace(
    '''        HarnessController controller = new HarnessController((ignoredWorld, x, y, z) -> x >= 10);
        controller.movementEntityID = 2;

        boolean cancelled = controller.handleRelativeMove(null, playerData, 20);
''',
    '''        VISIBILITY_POLICY.exemptAtOrAboveX(10);
        CONTROLLER.movementEntityID = 2;

        boolean cancelled = CONTROLLER.handleRelativeMove(null, playerData, 20);
'''
)
text = text.replace("assertEquals(List.of(2), controller.directlyShownEntityIDs);",
                    "assertEquals(List.of(2), CONTROLLER.directlyShownEntityIDs);")
text = text.replace("assertTrue(controller.directlyShownEntityIDs.isEmpty());",
                    "assertTrue(CONTROLLER.directlyShownEntityIDs.isEmpty());")
text = text.replace("assertEquals(List.of(2), controller.directlyHiddenEntityIDs);",
                    "assertEquals(List.of(2), CONTROLLER.directlyHiddenEntityIDs);")

old = '''        private HarnessController() {
            super();
        }

        private HarnessController(VisibilityExemptionPolicy visibilityExemptionPolicy) {
            super(visibilityExemptionPolicy);
        }
'''
new = '''        private HarnessController(VisibilityExemptionPolicy visibilityExemptionPolicy) {
            super(visibilityExemptionPolicy);
        }
'''
if old not in text:
    raise RuntimeError("harness constructor block not found")
text = text.replace(old, new, 1)

marker = '''    private static final class HarnessController extends PacketEntityViewController<Void> {
'''
policy_class = '''    private static final class HarnessVisibilityPolicy implements VisibilityExemptionPolicy {
        private volatile boolean enabled;
        private volatile double minimumX;

        private void exemptAtOrAboveX(double x) {
            minimumX = x;
            enabled = true;
        }

        private void disable() {
            enabled = false;
        }

        @Override
        public boolean isExempt(UUID world, double x, double y, double z) {
            return enabled && x >= minimumX;
        }
    }

'''
if marker not in text:
    raise RuntimeError("harness class marker not found")
text = text.replace(marker, policy_class + marker, 1)
p.write_text(text)
print("Boundary test harness fixed")
