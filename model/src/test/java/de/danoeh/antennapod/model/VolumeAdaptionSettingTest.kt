package de.danoeh.antennapod.model

import de.danoeh.antennapod.model.feed.VolumeAdaptionSetting
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.CoreMatchers.`is`
import org.hamcrest.MatcherAssert.assertThat
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class VolumeAdaptionSettingTest {

    @Before
    fun setUp() {
        VolumeAdaptionSetting.setBoostSupported(false)
    }

    @After
    fun tearDown() {
        VolumeAdaptionSetting.setBoostSupported(null)
    }

    @Test
    fun mapOffToInteger() {
        val setting = VolumeAdaptionSetting.OFF
        assertThat(setting.toInteger(), `is`(equalTo(0)))
    }

    @Test
    fun mapLightReductionToInteger() {
        val setting = VolumeAdaptionSetting.LIGHT_REDUCTION

        assertThat(setting.toInteger(), `is`(equalTo(1)))
    }

    @Test
    fun mapHeavyReductionToInteger() {
        val setting = VolumeAdaptionSetting.HEAVY_REDUCTION

        assertThat(setting.toInteger(), `is`(equalTo(2)))
    }

    @Test
    fun mapLightBoostToInteger() {
        val setting = VolumeAdaptionSetting.LIGHT_BOOST

        assertThat(setting.toInteger(), `is`(equalTo(3)))
    }

    @Test
    fun mapMediumBoostToInteger() {
        val setting = VolumeAdaptionSetting.MEDIUM_BOOST

        assertThat(setting.toInteger(), `is`(equalTo(4)))
    }

    @Test
    fun mapHeavyBoostToInteger() {
        val setting = VolumeAdaptionSetting.HEAVY_BOOST

        assertThat(setting.toInteger(), `is`(equalTo(5)))
    }

    @Test
    fun mapIntegerToVolumeAdaptionSetting() {
        assertThat(VolumeAdaptionSetting.fromInteger(0), `is`(equalTo(VolumeAdaptionSetting.OFF)))
        assertThat(VolumeAdaptionSetting.fromInteger(1), `is`(equalTo(VolumeAdaptionSetting.LIGHT_REDUCTION)))
        assertThat(VolumeAdaptionSetting.fromInteger(2), `is`(equalTo(VolumeAdaptionSetting.HEAVY_REDUCTION)))
        assertThat(VolumeAdaptionSetting.fromInteger(3), `is`(equalTo(VolumeAdaptionSetting.LIGHT_BOOST)))
        assertThat(VolumeAdaptionSetting.fromInteger(4), `is`(equalTo(VolumeAdaptionSetting.MEDIUM_BOOST)))
        assertThat(VolumeAdaptionSetting.fromInteger(5), `is`(equalTo(VolumeAdaptionSetting.HEAVY_BOOST)))
    }

    @Test(expected = IllegalArgumentException::class)
    fun cannotMapNegativeValues() {
        VolumeAdaptionSetting.fromInteger(-1)
    }

    @Test(expected = IllegalArgumentException::class)
    fun cannotMapValuesOutOfRange() {
        VolumeAdaptionSetting.fromInteger(6)
    }

    @Test
    fun noAdaptionIfTurnedOff() {
        val adaptionFactor = VolumeAdaptionSetting.OFF.getAdaptionFactor()
        assertEquals(1.0f, adaptionFactor, 0.01f)
    }

    @Test
    fun lightReductionYieldsHigherValueThanHeavyReduction() {
        val lightReductionFactor = VolumeAdaptionSetting.LIGHT_REDUCTION.getAdaptionFactor()

        val heavyReductionFactor = VolumeAdaptionSetting.HEAVY_REDUCTION.getAdaptionFactor()

        assertTrue(
            "Light reduction must have higher factor than heavy reduction",
            lightReductionFactor > heavyReductionFactor
        )
    }

    @Test
    fun lightBoostYieldsHigherValueThanLightReduction() {
        val lightReductionFactor = VolumeAdaptionSetting.LIGHT_REDUCTION.getAdaptionFactor()

        val lightBoostFactor = VolumeAdaptionSetting.LIGHT_BOOST.getAdaptionFactor()

        assertTrue("Light boost must have higher factor than light reduction", lightBoostFactor > lightReductionFactor)
    }

    @Test
    fun mediumBoostYieldsHigherValueThanLightBoost() {
        val lightBoostFactor = VolumeAdaptionSetting.LIGHT_BOOST.getAdaptionFactor()

        val mediumBoostFactor = VolumeAdaptionSetting.MEDIUM_BOOST.getAdaptionFactor()

        assertTrue("Medium boost must have higher factor than light boost", mediumBoostFactor > lightBoostFactor)
    }

    @Test
    fun heavyBoostYieldsHigherValueThanMediumBoost() {
        val mediumBoostFactor = VolumeAdaptionSetting.MEDIUM_BOOST.getAdaptionFactor()

        val heavyBoostFactor = VolumeAdaptionSetting.HEAVY_BOOST.getAdaptionFactor()

        assertTrue("Heavy boost must have higher factor than medium boost", heavyBoostFactor > mediumBoostFactor)
    }
}
