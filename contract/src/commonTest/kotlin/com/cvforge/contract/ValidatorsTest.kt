package com.cvforge.contract

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ValidatorsTest {
    @Test fun emailAcceptsValidAndRejectsInvalid() {
        assertNull(Validators.email("ana@exemplo.com.br"))
        assertNotNull(Validators.email("ana@"))
        assertNotNull(Validators.email("sem-arroba.com"))
    }

    @Test fun passwordNeedsMinimumLength() {
        assertNotNull(Validators.password("1234567"))
        assertNull(Validators.password("12345678"))
    }

    @Test fun profileRejectsExperienceWithoutCompany() {
        val p = ProfileDto(experiences = listOf(ExperienceDto(company = "", role = "Dev", startDate = "01/2020")))
        assertNotNull(Validators.profile(p))
    }

    @Test fun profileWithOnlySkillsHasContent() {
        assertTrue(Validators.profileHasContent(ProfileDto(skills = listOf("Kotlin"))))
        assertEquals(false, Validators.profileHasContent(ProfileDto()))
    }
}
