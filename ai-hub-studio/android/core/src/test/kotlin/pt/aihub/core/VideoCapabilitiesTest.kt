package pt.aihub.core

import kotlin.test.*

class VideoCapabilitiesTest {
    private val imageOnly=VideoCapabilities(textToVideo=false,minSeconds=.5,maxSeconds=5.0,referenceDeterminesSize=true)
    @Test fun preventsUnsupportedTextJobsAndInvalidDurationsBeforeSending() {
        assertFailsWith<IllegalArgumentException>{imageOnly.validate(2.0,false)}
        listOf(Double.NaN,Double.POSITIVE_INFINITY,.49,5.01).forEach{seconds->assertFailsWith<IllegalArgumentException>{imageOnly.validate(seconds,true)}}
    }
    @Test fun acceptsBothPublishedBoundariesAndPreservesLegacyModes() {
        imageOnly.validate(.5,true);imageOnly.validate(5.0,true)
        VideoCapabilities().validate(.3,false);VideoCapabilities().validate(8.5,true)
    }
}
