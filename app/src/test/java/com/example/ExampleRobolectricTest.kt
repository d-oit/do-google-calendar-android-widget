package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.repository.CalendarRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Calendar

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

    @Test
    fun `read string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("Agenda Widget", appName)
    }

    @Test
    fun `computeDayHeader returns TODAY for current time`() {
        val now = System.currentTimeMillis()
        val header = CalendarRepository.computeDayHeader(now)
        assertEquals("TODAY", header)
    }

    @Test
    fun `formatEventTime formats range properly`() {
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 10)
        cal.set(Calendar.MINUTE, 0)
        val start = cal.timeInMillis
        cal.set(Calendar.HOUR_OF_DAY, 11)
        cal.set(Calendar.MINUTE, 0)
        val end = cal.timeInMillis

        val formatted = CalendarRepository.formatEventTime(start, end, false)
        assertNotNull(formatted)
    }
}
