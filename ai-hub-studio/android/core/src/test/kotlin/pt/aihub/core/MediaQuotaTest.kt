package pt.aihub.core
import kotlin.test.*
import java.time.Instant

class MediaQuotaTest {
    private val now=Instant.parse("2026-07-01T22:00:00Z").toEpochMilli()
    @Test fun cloudflareDailyAndCapacityAreDifferent(){
        val daily=MediaQuotaParser.parse("cloudflare",429,emptyMap(),"{\"errors\":[{\"code\":3036,\"message\":\"daily free allocation exhausted\"}]}",now)
        assertEquals(MediaAvailability.EXHAUSTED,daily.state);assertEquals(Instant.parse("2026-07-02T00:00:00Z").toEpochMilli(),daily.resetAt);assertTrue(daily.predicted);assertNull(daily.remaining);assertTrue(daily.summary(now).contains("01:00:00"))
        val capacity=MediaQuotaParser.parse("cloudflare",429,emptyMap(),"{\"errors\":[{\"code\":3040}]}",now)
        assertEquals(MediaAvailability.UNAVAILABLE,capacity.state);assertNull(capacity.resetAt);assertFalse(capacity.predicted)
    }
    @Test fun retryAfterSupportsSecondsAndOfficialHttpDates(){
        assertEquals(now+45000,MediaQuotaParser.retryAt("45",now))
        assertEquals(now+45000,MediaQuotaParser.retryAt("Wed, 1 Jul 2026 22:00:45 GMT",now))
        assertNull(MediaQuotaParser.retryAt("tomorrow",now));assertNull(MediaQuotaParser.retryAt("-1",now))
        val q=MediaQuotaParser.parse("p",429,mapOf("Retry-After" to "45"),"",now)
        assertEquals(MediaAvailability.WAITING,q.state);assertEquals("desconhecida",q.window);assertNull(q.remaining)
        assertEquals(MediaAvailability.UNKNOWN,q.effectiveState(now+45000));assertFalse(q.summary(now+50000).startsWith("Disponível"))
    }
    @Test fun knownRateWindowsNeverBecomeDailyQuota(){
        val q=MediaQuotaParser.parse("p",200,mapOf("X-RateLimit-Remaining" to "2","X-RateLimit-Limit" to "30","RateLimit-Policy" to "default;q=30;w=60","Retry-After" to "20"),"",now)
        assertEquals(MediaAvailability.LOW,q.state);assertEquals("por minuto",q.window);assertEquals(2L,q.remaining);assertEquals(now+20000,q.resetAt)
        val cf=MediaQuotaParser.parse("cloudflare",429,mapOf("RateLimit" to "default;r=0;t=12"),"",now)
        assertEquals(MediaAvailability.WAITING,cf.state);assertEquals(now+12000,cf.resetAt);assertFalse(cf.predicted)
    }
    @Test fun providerQuotaTextIsUsedWithoutGuessingAccountWindow(){
        val q=MediaQuotaParser.parse("hf-zimage",0,emptyMap(),"You have exceeded your ZeroGPU quota. Try again in 23:59:57",now)
        assertEquals(MediaAvailability.EXHAUSTED,q.state);assertEquals(now+86397000,q.resetAt);assertNull(q.remaining);assertEquals("desconhecida",q.window)
        val unknown=MediaQuotaParser.parse("hf-flux",0,emptyMap(),"You have exceeded your ZeroGPU quota",now)
        assertNull(unknown.resetAt);assertTrue(unknown.summary(now).contains("Reposição desconhecida"))
    }
    @Test fun paidRequiredAndMissingModelsAreNotInvalidTokens(){
        assertEquals(MediaAvailability.MODEL_UNAVAILABLE,MediaQuotaParser.parse("cloudflare",403,emptyMap(),"{\"code\":5035}",now).state)
        assertEquals(MediaAvailability.INVALID_CREDENTIALS,MediaQuotaParser.parse("cloudflare",401,emptyMap(),"",now).state)
    }
    @Test fun portugalWinterAndSummerAreAutomatic(){
        val summer=MediaQuota(resetAt=Instant.parse("2026-07-02T00:00:00Z").toEpochMilli()).summary(now)
        val winterNow=Instant.parse("2026-12-01T22:00:00Z").toEpochMilli()
        val winter=MediaQuota(resetAt=Instant.parse("2026-12-02T00:00:00Z").toEpochMilli()).summary(winterNow)
        assertTrue(summer.contains("01:00:00"));assertTrue(winter.contains("00:00:00"))
    }
}
