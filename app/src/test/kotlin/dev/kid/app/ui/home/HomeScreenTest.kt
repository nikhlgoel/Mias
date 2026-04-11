package dev.kid.app.ui.home

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("HomeScreen ViewModel placeholder")
class HomeScreenTest {

    @Test
    fun `placeholder test - verifies test infrastructure works`() {
        // This validates that JUnit 5 + Truth are wired correctly in the app module.
        // Real ViewModel tests will be added when HomeViewModel is implemented.
        assertThat(true).isTrue()
    }

    @Test
    fun `app module can reference core common types`() {
        val result = dev.kid.core.common.KidResult.Success("test")
        assertThat(result.data).isEqualTo("test")
    }
}
