package com.javboss.phone.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class LaunchTabTest {

    @Test
    fun `startup options only contain videos and jav`() {
        assertEquals(listOf(LaunchTab.VIDEOS, LaunchTab.JAV), LaunchTab.startupOptions)
        assertEquals(2, LaunchTab.startupOptions.size)
    }

    @Test
    fun `favorites and settings are not startup options`() {
        assertFalse(LaunchTab.FAVORITES in LaunchTab.startupOptions)
        assertFalse(LaunchTab.SETTINGS in LaunchTab.startupOptions)
    }

    @Test
    fun `tab indexes stay aligned with the four bottom nav entries`() {
        assertEquals(0, LaunchTab.VIDEOS.index)
        assertEquals(1, LaunchTab.JAV.index)
        assertEquals(2, LaunchTab.FAVORITES.index)
        assertEquals(3, LaunchTab.SETTINGS.index)
        assertEquals(LaunchTab.JAV, LaunchTab.fromIndex(99))
    }
}
