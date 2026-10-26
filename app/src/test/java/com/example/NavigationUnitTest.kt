package com.example

import com.example.ui.navigation.Screen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NavigationUnitTest {

    @Test
    fun testBottomNavItemsInitialization() {
        val items = Screen.bottomNavItems
        assertNotNull(items)
        assertEquals(5, items.size)
        items.forEach { screen ->
            assertNotNull(screen)
            assertNotNull(screen.route)
            assertTrue(screen.route.isNotEmpty())
            assertNotNull(screen.title)
            assertTrue(screen.title.isNotEmpty())
        }

        // Verify distinct routes
        val uniqueRoutes = items.map { it.route }.toSet()
        assertEquals(items.size, uniqueRoutes.size)
    }
}
