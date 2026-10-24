package com.xiaohan.xhsnotegen

import com.xiaohan.xhsnotegen.ai.AiBalance
import com.xiaohan.xhsnotegen.ai.AiConfig
import com.xiaohan.xhsnotegen.ai.AiProvider
import org.junit.Assert.*
import org.junit.Test

/** Text checks avoid wording, which follows the machine's language. */
class AiBalanceTest {

    @Test
    fun deepSeekBalanceShowsTotalAndGranted() {
        // Example response from api-docs.deepseek.com/api/get-user-balance
        val body = """{"is_available":true,"balance_infos":[{"currency":"CNY","total_balance":"110.00","granted_balance":"10.00","topped_up_balance":"100.00"}]}"""
        val line = AiBalance.describeDeepSeek(body)!!
        assertTrue(line, line.contains("¥110.00"))
        assertTrue(line, line.contains("¥10.00"))
    }

    @Test
    fun deepSeekWithoutGrantLeavesItOut() {
        val body = """{"is_available":true,"balance_infos":[{"currency":"USD","total_balance":"5.00","granted_balance":"0.00","topped_up_balance":"5.00"}]}"""
        val line = AiBalance.describeDeepSeek(body)!!
        assertTrue(line, line.contains("$5.00"))
        assertFalse(line, line.contains("$0.00"))
    }

    @Test
    fun deepSeekGarbageIsNull() {
        assertNull(AiBalance.describeDeepSeek("not json"))
        assertNull(AiBalance.describeDeepSeek("""{"balance_infos":[]}"""))
    }

    @Test
    fun openRouterLimitedKeyShowsRemaining() {
        val body = """{"data":{"label":"k","limit":20,"limit_remaining":12.5,"usage":7.5}}"""
        val line = AiBalance.describeOpenRouter(body)!!
        assertTrue(line, line.contains("$12.50"))
        assertTrue(line, line.contains("$20.00"))
    }

    @Test
    fun openRouterUnlimitedKeyShowsUsage() {
        val body = """{"data":{"label":"k","limit":null,"limit_remaining":null,"usage":3.2}}"""
        val line = AiBalance.describeOpenRouter(body)!!
        assertTrue(line, line.contains("$3.20"))
    }

    @Test
    fun onlyDeepSeekAndOpenRouterAreSupported() {
        fun cfg(p: AiProvider, url: String = p.defaultBaseUrl) = AiConfig(p, "m", "k", url, true)
        assertTrue(AiBalance.supports(cfg(AiProvider.DEEPSEEK)))
        assertTrue(AiBalance.supports(cfg(AiProvider.CUSTOM, "https://openrouter.ai/api/v1")))
        assertFalse(AiBalance.supports(cfg(AiProvider.CUSTOM, "https://api.moonshot.cn/v1")))
        assertFalse(AiBalance.supports(cfg(AiProvider.OPENAI)))
        assertFalse(AiBalance.supports(cfg(AiProvider.CLAUDE)))
        assertFalse(AiBalance.supports(cfg(AiProvider.GEMINI)))
    }
}
