package com.bapel_slimefun_mod.automation;  

import com.bapel_slimefun_mod.automation.state.AutomationState;  
import org.junit.jupiter.api.*;  
import org.mockito.Mock;  
import org.mockito.MockitoAnnotations;  

import static org.assertj.core.api.Assertions.*;  
import static org.mockito.Mockito.*;  

@DisplayName("AutomationStateMachine Tests")  
class AutomationStateMachineTest {  

    @Mock private com.bapel_slimefun_mod.automation.infra.NetworkGridLocator  gridLocator;  
    @Mock private com.bapel_slimefun_mod.automation.infra.FastMachineLocator  machineLocator;  
    @Mock private com.bapel_slimefun_mod.automation.infra.SubRecipeResolver   subRecipeResolver;  
    @Mock private com.bapel_slimefun_mod.automation.infra.RecipeLockExecutor  recipeLockExecutor;  
    @Mock private com.bapel_slimefun_mod.automation.infra.InventoryChecker    inventoryChecker;  
    @Mock private com.bapel_slimefun_mod.automation.infra.CameraController    cameraController;  
    @Mock private com.bapel_slimefun_mod.automation.infra.GuiInteractor       guiInteractor;  
    @Mock private com.bapel_slimefun_mod.automation.infra.GridScanner         gridScanner;  

    private AutomationStateMachine stateMachine;  
    private AutoCloseable          mocks;  

    @BeforeEach  
    void setUp() {  
        mocks = MockitoAnnotations.openMocks(this);  

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

    @AfterEach  
    void tearDown() throws Exception {  
        mocks.close();  
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
        return new CraftingJob(key, net.minecraft.world.item.ItemStack.EMPTY, amount, 0);  
    }  
}
