package com.bapel_slimefun_mod.automation.util;  

import net.minecraft.world.item.ItemStack;  
import org.junit.jupiter.api.*;  

import static org.assertj.core.api.Assertions.*;  

@DisplayName("ItemKeyUtil Tests")  
class ItemKeyUtilTest {  

    @Test  
    @DisplayName("getKey null/empty stack harus return 'air'")  
    void shouldReturnAirForEmptyStack() {  
        assertThat(ItemKeyUtil.getKey(null)).isEqualTo("air");  
        assertThat(ItemKeyUtil.getKey(ItemStack.EMPTY)).isEqualTo("air");  
    }  

    @Test  
    @DisplayName("isSlimefunItem null/empty stack harus return false")  
    void shouldReturnFalseForEmptyStack() {  
        assertThat(ItemKeyUtil.isSlimefunItem(null)).isFalse();  
        assertThat(ItemKeyUtil.isSlimefunItem(ItemStack.EMPTY)).isFalse();  
    }  

    @Test  
    @DisplayName("isSameItem harus true untuk empty stack masing-masing")  
    void shouldReturnTrueForSameKey() {  
        assertThat(ItemKeyUtil.isSameItem(null, null)).isFalse();  
        assertThat(ItemKeyUtil.isSameItem(ItemStack.EMPTY, ItemStack.EMPTY)).isTrue();  
        assertThat(ItemKeyUtil.isSameItem(null, ItemStack.EMPTY)).isFalse();  
    }  
}
