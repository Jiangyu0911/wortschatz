package de.wortschatz.app.data

/**
 * 拼写测试的答案比对规则。
 *
 * 德语键盘输入变音字母不便，因此 ä/ö/ü/ß 与 ae/oe/ue/ss 视为等价；
 * 大小写、多余空格与句末标点都不计入差异。
 *
 * 冠词默认不参与判定（`Hund` 与 `der Hund` 都算对）；开启「要求写冠词」后必须
 * 写出与词条性别一致的 der/die/das。复数默认完全不要求；开启「要求写复数」后
 * 答案写成 `der Hund, Hunde` 这种逗号分隔形式，后半段单独比对。
 */
object SpellTest {

    private val ARTICLE = Regex("^(der|die|das)\\s+", RegexOption.IGNORE_CASE)
    private val HEAD_ARTICLE = Regex("^(der|die|das)\\s+", RegexOption.IGNORE_CASE)
    private val SPACES = Regex("\\s+")
    private val EDGE_PUNCT = charArrayOf('.', ',', '!', '?', ';', ':', '…', ' ')
    private val PLURAL_SEP = charArrayOf(',', ';', '/', '、')

    fun normalize(raw: String): String {
        var s = raw.trim().lowercase()
        s = ARTICLE.replace(s, "")
        s = s.replace("ä", "ae")
            .replace("ö", "oe")
            .replace("ü", "ue")
            .replace("ß", "ss")
        s = SPACES.replace(s, " ")
        return s.trim(*EDGE_PUNCT)
    }

    /** 去掉开头的冠词 */
    private fun stripArticle(raw: String): String = ARTICLE.replace(raw.trim(), "").trim()

    /** 名词对应的冠词；非名词或性别缺失时为 null（无法要求） */
    fun articleOf(word: Word): String? = when (word.gender) {
        "m" -> "der"
        "f" -> "die"
        "n" -> "das"
        else -> null
    }

    /** 复数形式（去掉冠词）；没有复数则为 null */
    fun pluralOf(word: Word): String? =
        word.plural?.takeIf { it.isNotBlank() }?.let { stripArticle(it) }

    /**
     * 用户输入是否正确。
     *
     * [requireArticle] 只对名词生效：输入开头必须是正确的 der/die/das；
     * [requirePlural] 只对该词条有复数形式的名词生效：逗号后要写出复数。
     * 两项都关闭时与旧版行为完全一致。
     */
    fun check(
        input: String,
        word: Word,
        requireArticle: Boolean = false,
        requirePlural: Boolean = false
    ): Boolean {
        // 允许用逗号 / 分号 / 斜杠分隔单数与复数
        val parts = input.split(*PLURAL_SEP).map { it.trim() }.filter { it.isNotEmpty() }
        val head = parts.firstOrNull() ?: return false
        val typedPlural = parts.getOrNull(1)

        var body = head
        if (word.pos == "n") {
            val article = articleOf(word)
            if (requireArticle && article != null) {
                val m = HEAD_ARTICLE.find(head) ?: return false
                if (!m.groupValues[1].equals(article, ignoreCase = true)) return false
                body = head.substring(m.range.last + 1)
            }
            val plural = pluralOf(word)
            if (requirePlural && plural != null) {
                // 没写复数段直接判错；写了就按变音等价规则比对
                if (typedPlural == null) return false
                if (normalize(typedPlural) != normalize(plural)) return false
            }
        }
        return normalize(body) == normalize(answerOf(word))
    }

    /** 兼容旧调用：不带冠词与复数要求的判定 */
    fun matches(input: String, word: Word): Boolean = check(input, word)

    /**
     * 展示用标准答案。
     *
     * 默认给出不带冠词的词形；开启对应要求时补上冠词与复数，
     * 与判定的口径保持一致——否则会让人以为漏写了什么。
     */
    fun answerOf(
        word: Word,
        requireArticle: Boolean = false,
        requirePlural: Boolean = false
    ): String {
        val base = stripArticle(word.word.ifBlank { word.lemma })
        if (word.pos != "n") return base
        val head = if (requireArticle) articleOf(word)?.let { "$it $base" } ?: base else base
        val plural = pluralOf(word)
        return if (requirePlural && plural != null) "$head, $plural" else head
    }

    /** 首字母提示，帮助回忆但不直接给答案 */
    fun hintOf(word: Word): String {
        val a = answerOf(word)
        return a.mapIndexed { i, c -> if (i == 0) c else '·' }.joinToString("")
    }

    /** 按当前设置给出的书写格式示例（固定用 Hund，不泄露当前题的答案） */
    fun formatSample(requireArticle: Boolean, requirePlural: Boolean): String = when {
        requireArticle && requirePlural -> "der Hund, Hunde"
        requireArticle -> "der Hund"
        requirePlural -> "Hund, Hunde"
        else -> "Hund"
    }
}
