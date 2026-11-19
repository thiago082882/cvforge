package com.cvforge.app

import com.cvforge.app.data.Session
import com.cvforge.app.data.SessionStore
import com.russhwolf.settings.MapSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SessionStoreTest {
    @Test fun saveAndClearSession() {
        val store = SessionStore(MapSettings())
        assertNull(store.token)
        store.save(Session("tok", "1", "Ana", "ana@x.com"), isNewUser = true)
        assertEquals("tok", store.token)
        assertEquals(true, store.onboarding.value)
        store.clear()
        assertNull(store.session.value)
    }
}
