package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.AppDatabase
import com.example.data.ReferenceTrackEntity
import com.example.viewmodel.ProtectionMode
import com.example.viewmodel.VolumeCheckerViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @Test
    fun `read app name from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("Lish Volume Checker", appName)
    }

    @Test
    fun `room database persists and deletes reference track`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            val dao = db.referenceDao()

            val ref = ReferenceTrackEntity(
                id = 1,
                fileName = "ref_master.mp3",
                fileUri = "content://media/123",
                durationMs = 180000L,
                sampleRate = 44100,
                channelCount = 2,
                integratedLufs = -14.0,
                truePeakDbtp = -1.0
            )
            dao.saveReferenceTrack(ref)

            val loaded = dao.getReferenceTrackOnce()
            assertNotNull(loaded)
            assertEquals("ref_master.mp3", loaded?.fileName)
            assertEquals(-14.0, loaded?.integratedLufs ?: 0.0, 0.01)

            dao.deleteReferenceTrack()
            val afterDelete = dao.getReferenceTrack().first()
            assertNull(afterDelete)
        } finally {
            db.close()
        }
    }

    @Test
    fun `gain safety plan prevents blind clipping in safe cap mode`() {
        val planCapped = VolumeCheckerViewModel.computeGainSafetyPlan(
            originalLufs = -18.0,
            originalTruePeak = -1.5,
            requestedGainDb = 5.0,
            protectionMode = ProtectionMode.SAFE_GAIN_CAP,
            ceilingDbtp = -1.0
        )

        assertTrue("Gain must be capped in SAFE_GAIN_CAP mode", planCapped.wasGainCapped)
        assertEquals(0.5, planCapped.effectiveGainDb, 0.01)
        assertTrue("Projected true peak must stay <= -1.0 dBTP", planCapped.projectedTruePeak <= -1.0)

        val planLimiter = VolumeCheckerViewModel.computeGainSafetyPlan(
            originalLufs = -18.0,
            originalTruePeak = -1.5,
            requestedGainDb = 5.0,
            protectionMode = ProtectionMode.TRUE_PEAK_LIMITER,
            ceilingDbtp = -1.0
        )
        assertTrue("Should flag aggressive boost when > 4 dB above headroom", planLimiter.isAggressiveBoost)
        assertEquals(4.5, planLimiter.estimatedReductionDb, 0.01)
    }
}
