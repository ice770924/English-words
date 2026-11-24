package com.example.kkamppagienglish

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class VocabItem(
    val english: String,
    val korean: String,
    val pos: String = "word",
    val example: String = "",
    var memorized: Boolean = false
)

object VocabStorage {
    private const val PREFS_NAME = "kkamppagi_prefs"
    private const val KEY_VOCAB = "vocab_list"

    fun loadWords(context: Context): MutableList<VocabItem> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val savedJson = prefs.getString(KEY_VOCAB, null)
        val list = mutableListOf<VocabItem>()

        if (savedJson != null) {
            val arr = JSONArray(savedJson)
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                list.add(
                    VocabItem(
                        english = obj.getString("english"),
                        korean = obj.getString("korean"),
                        pos = obj.optString("pos", "word"),
                        example = obj.optString("example", ""),
                        memorized = obj.optBoolean("memorized", false)
                    )
                )
            }
        } else {
            list.addAll(defaultVocabulary())
            saveWords(context, list)
        }
        return list
    }

    fun saveWords(context: Context, words: List<VocabItem>) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val arr = JSONArray()
        for (item in words) {
            val obj = JSONObject()
            obj.put("english", item.english)
            obj.put("korean", item.korean)
            obj.put("pos", item.pos)
            obj.put("example", item.example)
            obj.put("memorized", item.memorized)
            arr.put(obj)
        }
        prefs.edit().putString(KEY_VOCAB, arr.toString()).apply()
    }

    fun prependWords(context: Context, newItems: List<VocabItem>) {
        if (newItems.isEmpty()) return
        val current = loadWords(context)
        current.addAll(0, newItems)
        saveWords(context, current)
    }

    private fun defaultVocabulary(): List<VocabItem> = listOf(
        VocabItem("accomplish", "성취하다, 완수하다", "verb", "She accomplished her goal ahead of schedule."),
        VocabItem("inevitable", "피할 수 없는, 필연적인", "adjective", "Change is an inevitable part of life."),
        VocabItem("substantial", "상당한, 실질적인", "adjective", "They made a substantial contribution to the project."),
        VocabItem("implement", "실행하다, 구현하다", "verb", "We will implement the new policy next month."),
        VocabItem("persistent", "끈기 있는, 지속적인", "adjective", "Persistent effort leads to mastery."),
        VocabItem("adequate", "적절한, 충분한", "adjective", "Make sure you get adequate sleep before the exam."),
        VocabItem("comprehensive", "포괄적인, 종합적인", "adjective", "The report offers a comprehensive analysis."),
        VocabItem("distinguish", "구별하다, 식별하다", "verb", "It is important to distinguish fact from opinion."),
        VocabItem("elaborate", "정교한 / 자세히 설명하다", "adj / verb", "Could you elaborate on that idea?"),
        VocabItem("fluctuate", "변동하다, 오르내리다", "verb", "Prices fluctuate depending on supply and demand."),
        VocabItem("generate", "생성하다, 발생시키다", "verb", "Wind turbines generate clean electricity."),
        VocabItem("hesitate", "망설이다, 주저하다", "verb", "Do not hesitate to contact me if you have questions."),
        VocabItem("incentive", "동기, 장려책", "noun", "Bonuses provide a strong incentive to work harder."),
        VocabItem("justify", "정당화하다", "verb", "How can you justify such an expensive purchase?"),
        VocabItem("keen", "예리한, 열망하는", "adjective", "She has a keen interest in foreign languages."),
        VocabItem("legitimate", "합법적인, 타당한", "adjective", "He raised a legitimate concern during the meeting."),
        VocabItem("modify", "수정하다, 변경하다", "verb", "We need to modify the design slightly."),
        VocabItem("neglect", "방치하다, 소홀히 하다", "verb", "Never neglect your health while studying."),
        VocabItem("obstacle", "장애물, 방해", "noun", "Lack of time was the main obstacle."),
        VocabItem("perceive", "인지하다, 인식하다", "verb", "How do customers perceive our brand?"),
        VocabItem("reluctant", "꺼리는, 마지못해 하는", "adjective", "He was reluctant to admit his mistake."),
        VocabItem("simultaneously", "동시에", "adverb", "Two events happened simultaneously."),
        VocabItem("tangible", "유형의, 명백한", "adjective", "We need tangible results by Friday."),
        VocabItem("undergo", "겪다, 경험하다", "verb", "The city is undergoing rapid transformation."),
        VocabItem("versatile", "다재다능한, 다용도의", "adjective", "Eggs are a versatile ingredient in cooking."),
        VocabItem("withdraw", "철회하다, 인출하다", "verb", "She decided to withdraw her application."),
        VocabItem("yield", "산출하다 / 양보하다", "verb", "The research yielded promising results."),
        VocabItem("allocate", "할당하다, 배분하다", "verb", "We must allocate resources wisely."),
        VocabItem("coherent", "일관성 있는, 논리적인", "adjective", "She presented a clear and coherent argument."),
        VocabItem("deteriorate", "악화되다, 나빠지다", "verb", "Weather conditions began to deteriorate rapidly.")
    )
}
