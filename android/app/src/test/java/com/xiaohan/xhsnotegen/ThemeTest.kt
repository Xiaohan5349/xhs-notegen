package com.xiaohan.xhsnotegen

import com.xiaohan.xhsnotegen.ui.theme.AppTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class ThemeTest {

    @Test fun newInstallGetsField() {
        assertEquals(AppTheme.FIELD, AppTheme.fromId(null))
        assertEquals(AppTheme.FIELD, AppTheme.DEFAULT)
    }

    @Test fun savedChoiceIsKept() {
        AppTheme.entries.forEach { assertEquals(it, AppTheme.fromId(it.id)) }
    }

    @Test fun retiredThemesMoveToTheClosestOne() {
        assertEquals(AppTheme.TOMATO, AppTheme.fromId("matsuri"))
        assertEquals(AppTheme.SAKURA, AppTheme.fromId("city_pop"))
    }

    @Test fun unknownIdFallsBackToTheDefault() {
        assertEquals(AppTheme.FIELD, AppTheme.fromId("no_such_theme"))
    }

    @Test fun everyThemeHasLightAndDark() {
        AppTheme.entries.forEach {
            assertNotNull(it.scheme(dark = false))
            assertNotNull(it.scheme(dark = true))
        }
    }
}
