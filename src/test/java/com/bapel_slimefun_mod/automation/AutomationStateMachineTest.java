package com.bapel_slimefun_mod.automation;  

import com.bapel_slimefun_mod.automation.state.AutomationState;  
import com.bapel_slimefun_mod.automation.infra.*;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import org.junit.jupiter.api.*;  

import java.util.Optional;
import static org.assertj.core.api.Assertions.*;  

@DisplayName("AutomationStateMachine Tests")  
class AutomationStateMachineTest {  

    private final NetworkGridLocator gridLocator = new NetworkGridLocator() {
        @Override public Optional<BlockPos> findNearest(BlockPos origin, int radius) { return Optional.empty(); }
        @Override public boolean isGridActive(BlockPos pos) { return false; }
    };

    private final FastMachineLocator machineLocator = new FastMachineLocator() {
        @Override public Optional<BlockPos> findNearest(BlockPos origin, ItemStack targetItem, int radius) { return Optional.empty(); }
        @Override public boolean isMachineAvailable(BlockPos pos) { return false; }
        @Override public boolean supportsRecipe(BlockPos pos, ItemStack targetItem) { return false; }
    };

    private final SubRecipeResolver subRecipeResolver = new SubRecipeResolver() {
        @Override public int getMissingAmount(CraftingJob job) { return 0; }
        @Override public Optional<CraftingJob> resolveSubJob(CraftingJob parentJob, int currentDepth) { return Optional.empty(); }
        @Override public int getReservedAmount(String itemKey) { return 0; }
    };

    private final RecipeLockExecutor recipeLockExecutor = new RecipeLockExecutor() {
        @Override public boolean lockAndExecute(ItemStack targetItem) { return false; }
        @Override public boolean isCraftingInProgress() { return false; }
    };

    private final InventoryChecker inventoryChecker = new InventoryChecker() {
        @Override public int countInInventory(ItemStack item) { return 0; }
        @Override public boolean hasInventorySpace() { return false; }
    };

    private final CameraController cameraController = new CameraController() {
        @Override public void lookAt(BlockPos target) {}
        @Override public boolean isLookingAt(BlockPos target) { return false; }
        @Override public void release() {}
        @Override public boolean isControlled() { return false; }
    };

    private final GuiInteractor guiInteractor = new GuiInteractor() {
        @Override public void rightClickBlock(BlockPos pos) {}
        @Override public void closeCurrentGui() {}
        @Override public boolean isGridGuiOpen() { return false; }
        @Override public boolean isMachineGuiOpen() { return false; }
        @Override public void shiftClickSlot(int slotIndex) {}
        @Override public int getActiveGuiSlotCount() { return 0; }
    };

    private final GridScanner gridScanner = new GridScanner() {
        @Override public int getMissingAmount(CraftingJob job) { return 0; }
        @Override public boolean shiftClickMaterials(CraftingJob job, int page) { return false; }
        @Override public void navigateToPage(int page) {}
        @Override public boolean isLastPage(int page) { return false; }
    };

    private AutomationStateMachine stateMachine;  

    @BeforeEach  
    void setUp() {  
        stateMachine = AutomationStateMachine.builder()  
            .gridLocator(gridLocator)  
            .machineLocator(machineLocator)  
            .subRecipeResolver(subRecipeResolver)  
            .recipeLockExecutor(recipeLockExecutor)  
            .inventoryChecker(inventoryChecker)  
            .cameraController(cameraController)  
            .guiInteractor(guiInteractor)  
            .gridScanner(gridScanner)  
            .build();  
    }  

    // ─────────────────────────────────────────────────────────────  

    @Test  
    @DisplayName("Initial state harus IDLE")  
    void shouldStartInIdleState() {  
        assertThat(stateMachine.getCurrentState())  
            .isEqualTo(AutomationState.IDLE);  
        assertThat(stateMachine.isRunning()).isFalse();  
    }  

    @Test  
    @DisplayName("start() harus pindah ke FINDING_GRID")  
    void shouldTransitionToFindingGridOnStart() {  
        CraftingJob job = makeJob("slimefun:COPPER_INGOT", 1);  
        stateMachine.start(job);  

        assertThat(stateMachine.getCurrentState())  
            .isEqualTo(AutomationState.FINDING_GRID);  
        assertThat(stateMachine.isRunning()).isTrue();  
    }  

    @Test  
    @DisplayName("cancelChain() harus kembali ke IDLE")  
    void shouldReturnToIdleOnCancel() {  
        stateMachine.start(makeJob("slimefun:COPPER_INGOT", 1));  
        stateMachine.cancelChain();  

        assertThat(stateMachine.getCurrentState())  
            .isEqualTo(AutomationState.IDLE);  
        assertThat(stateMachine.isRunning()).isFalse();  
    }  

    @Test  
    @DisplayName("start() saat running harus cancel chain lama")  
    void shouldCancelOldChainBeforeStartingNew() {  
        CraftingJob job1 = makeJob("slimefun:COPPER_INGOT", 1);  
        CraftingJob job2 = makeJob("slimefun:IRON_INGOT", 1);  

        stateMachine.start(job1);  
        assertThat(stateMachine.isRunning()).isTrue();  

        stateMachine.start(job2);  
        assertThat(stateMachine.isRunning()).isTrue();  
        assertThat(stateMachine.getCurrentState())  
            .isEqualTo(AutomationState.FINDING_GRID);  
    }  

    @Test  
    @DisplayName("Transisi ilegal harus diabaikan")  
    void shouldIgnoreIllegalTransitions() {  
        assertThat(AutomationState.CRAFTING.canTransitionFrom(AutomationState.IDLE))  
            .isFalse();  
    }  

    @Test  
    @DisplayName("ERROR selalu bisa dicapai dari state manapun")  
    void errorShouldBeReachableFromAnyState() {  
        for (AutomationState state : AutomationState.values()) {  
            assertThat(AutomationState.ERROR.canTransitionFrom(state)).isTrue();  
        }  
    }  

    private CraftingJob makeJob(String key, int amount) {  
        return new CraftingJob(key, ItemStack.EMPTY, amount, 0);  
    }  
}
