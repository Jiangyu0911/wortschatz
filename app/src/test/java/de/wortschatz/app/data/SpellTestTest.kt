package de.wortschatz.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 拼写判定规则。
 *
 * 这组用例的意义在于锁住「开关组合」的行为：默认宽松、开一项严一项，
 * 三项组合（冠词 + 复数）最容易在改动时出现「明明写对了却判错」。
 */
class SpellTestTest {

    private fun noun(
        word: String,
        gender: String? = "m",
        plural: String? = null,
        lemma: String = word
    ) = Word(
        word = word,
        lemma = lemma,
        pos = "n",
        gender = gender,
        plural = plural,
        zh = "测试",
        en = null,
        example = null,
        exampleZh = null,
        level = "A1",
        tags = null
    )

    private val hund = noun("der Hund", "m", "die Hunde", lemma = "Hund")
    private val gemuse = noun("das Gemüse", "n", "die Gemüse", lemma = "Gemüse")
    private val verb = Word(
        word = "laufen",
        lemma = "laufen",
        pos = "v",
        gender = null,
        plural = null,
        zh = "跑",
        en = null,
        example = null,
        exampleZh = null,
        level = "A1",
        tags = null
    )

    // ---------------- 默认（两个开关都关）：与旧版一致

    @Test
    fun plainWordPasses() {
        assertTrue(SpellTest.check("Hund", hund))
    }

    @Test
    fun articleIsIgnoredWhenNotRequired() {
        // 写不写冠词都对；冠词写错也不追究——这是旧行为，不能因为新开关变严
        assertTrue(SpellTest.check("der Hund", hund))
        assertTrue(SpellTest.check("die Hund", hund))
    }

    @Test
    fun umlautEquivalence() {
        assertTrue(SpellTest.check("Gemuese", gemuse))
        assertTrue(SpellTest.check("das Gemüse", gemuse))
    }

    // ---------------- 要求写冠词

    @Test
    fun articleRequired() {
        assertTrue(SpellTest.check("der Hund", hund, requireArticle = true))
        assertTrue(SpellTest.check("Der Hund", hund, requireArticle = true))
        assertFalse(SpellTest.check("Hund", hund, requireArticle = true))
        assertFalse(SpellTest.check("die Hund", hund, requireArticle = true))
    }

    @Test
    fun articleRequiredForNeuter() {
        assertTrue(SpellTest.check("das Gemuese", gemuse, requireArticle = true))
        assertFalse(SpellTest.check("der Gemuese", gemuse, requireArticle = true))
    }

    // ---------------- 要求写复数

    @Test
    fun pluralRequired() {
        assertTrue(SpellTest.check("Hund, Hunde", hund, requirePlural = true))
        assertTrue(SpellTest.check("der Hund, die Hunde", hund, requirePlural = true))
        assertTrue(SpellTest.check("Hund,Hunde", hund, requirePlural = true))
    }

    @Test
    fun pluralMissingOrWrongFails() {
        assertFalse(SpellTest.check("Hund", hund, requirePlural = true))
        assertFalse(SpellTest.check("der Hund", hund, requirePlural = true))
        assertFalse(SpellTest.check("Hund, Hunden", hund, requirePlural = true))
    }

    @Test
    fun pluralAcceptsUmlautFallback() {
        // 复数带变音时也允许 ae/oe/ue 写法
        assertTrue(SpellTest.check("Hund, Huende", nounDemo(), requirePlural = true))
    }

    private fun nounDemo() = noun("der Hund", "m", "die Hünde", lemma = "Hund")

    // ---------------- 两项都要求

    @Test
    fun bothRequired() {
        assertTrue(SpellTest.check("der Hund, Hunde", hund, true, true))
        assertTrue(SpellTest.check("der Hund, die Hunde", hund, true, true))
        assertFalse(SpellTest.check("Hund, Hunde", hund, true, true))
        assertFalse(SpellTest.check("die Hund, Hunde", hund, true, true))
        assertFalse(SpellTest.check("der Hund", hund, true, true))
    }

    // ---------------- 非名词不受影响

    @Test
    fun verbIgnoresArticleAndPlural() {
        assertTrue(SpellTest.check("laufen", verb, requireArticle = true, requirePlural = true))
        // 动词没有冠词与复数：多写的逗号段忽略掉，不因此判错
        assertTrue(SpellTest.check("laufen, laufen", verb, requireArticle = true, requirePlural = true))
        // 冠词要求只对名词生效，动词前写 der 会被当成多余的冠词去掉后比对
        assertFalse(SpellTest.check("laufen2", verb))
    }

    // ---------------- 展示与提示

    @Test
    fun answerFollowsSettings() {
        assertEquals("Hund", SpellTest.answerOf(hund))
        assertEquals("der Hund", SpellTest.answerOf(hund, requireArticle = true))
        assertEquals("der Hund, Hunde", SpellTest.answerOf(hund, true, true))
    }

    @Test
    fun formatSampleDoesNotLeakAnswer() {
        assertEquals("der Hund, Hunde", SpellTest.formatSample(true, true))
        assertEquals("Hund, Hunde", SpellTest.formatSample(false, true))
    }

    @Test
    fun wordWithoutPluralSkipsPluralCheck() {
        // 没有复数形式的名词（如抽象名词）不该被复数要求卡住
        val stoff = noun("der Mut", "m", null, lemma = "Mut")
        assertTrue(SpellTest.check("der Mut", stoff, requireArticle = true, requirePlural = true))
    }
}
