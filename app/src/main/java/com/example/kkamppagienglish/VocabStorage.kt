package com.example.kkamppagienglish

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class VocabItem(
    val english: String,
    val korean: String,
    val pos: String = "word",
    val example: String = "",
    var memorized: Boolean = false
)

data class WordGroup(
    val id: String,
    var name: String,
    val words: MutableList<VocabItem>
)

data class AddWordsResult(
    val addedCount: Int,
    val skippedCount: Int,
    val isFull: Boolean
)

object VocabStorage {
    const val MAX_GROUPS = 30
    const val MAX_WORDS_PER_GROUP = 30

    private const val PREFS_NAME = "kkamppagi_prefs"
    private const val KEY_GROUPS = "vocab_groups_v1"
    private const val KEY_SELECTED_GROUP_ID = "selected_group_id"
    private const val KEY_LEGACY_VOCAB = "vocab_list"

    fun loadGroups(context: Context): MutableList<WordGroup> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val savedGroupsJson = prefs.getString(KEY_GROUPS, null)
        val groups = mutableListOf<WordGroup>()

        if (savedGroupsJson != null) {
            val arr = JSONArray(savedGroupsJson)
            for (i in 0 until arr.length()) {
                val gObj = arr.getJSONObject(i)
                val id = gObj.getString("id")
                val name = gObj.getString("name")
                val wordsArr = gObj.getJSONArray("words")
                val words = mutableListOf<VocabItem>()
                for (j in 0 until minOf(wordsArr.length(), MAX_WORDS_PER_GROUP)) {
                    val wObj = wordsArr.getJSONObject(j)
                    words.add(
                        VocabItem(
                            english = wObj.getString("english"),
                            korean = wObj.getString("korean"),
                            pos = wObj.optString("pos", "word"),
                            example = wObj.optString("example", ""),
                            memorized = wObj.optBoolean("memorized", false)
                        )
                    )
                }
                groups.add(WordGroup(id = id, name = name, words = words))
            }
        }

        if (groups.isEmpty()) {
            val initialWords = loadLegacyWordsOrDefault(context)
            val chunks = initialWords.chunked(MAX_WORDS_PER_GROUP).take(MAX_GROUPS)
            if (chunks.isEmpty()) {
                groups.add(
                    WordGroup(
                        id = UUID.randomUUID().toString(),
                        name = "1번 그룹",
                        words = mutableListOf()
                    )
                )
            } else {
                chunks.forEachIndexed { idx, chunk ->
                    groups.add(
                        WordGroup(
                            id = UUID.randomUUID().toString(),
                            name = "${idx + 1}번 그룹",
                            words = chunk.toMutableList()
                        )
                    )
                }
            }
            saveGroups(context, groups)
            setSelectedGroupId(context, groups.first().id)
        }

        return groups
    }

    fun saveGroups(context: Context, groups: List<WordGroup>) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val arr = JSONArray()
        for (group in groups.take(MAX_GROUPS)) {
            val gObj = JSONObject()
            gObj.put("id", group.id)
            gObj.put("name", group.name)
            val wordsArr = JSONArray()
            for (item in group.words.take(MAX_WORDS_PER_GROUP)) {
                val wObj = JSONObject()
                wObj.put("english", item.english)
                wObj.put("korean", item.korean)
                wObj.put("pos", item.pos)
                wObj.put("example", item.example)
                wObj.put("memorized", item.memorized)
                wordsArr.put(wObj)
            }
            gObj.put("words", wordsArr)
            arr.put(gObj)
        }
        prefs.edit().putString(KEY_GROUPS, arr.toString()).apply()
    }

    fun getSelectedGroupId(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val groups = loadGroups(context)
        val savedId = prefs.getString(KEY_SELECTED_GROUP_ID, null)
        if (savedId != null && groups.any { it.id == savedId }) {
            return savedId
        }
        val firstId = groups.first().id
        setSelectedGroupId(context, firstId)
        return firstId
    }

    fun setSelectedGroupId(context: Context, groupId: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_SELECTED_GROUP_ID, groupId).apply()
    }

    fun getCurrentGroup(context: Context): WordGroup {
        val groups = loadGroups(context)
        val selectedId = getSelectedGroupId(context)
        return groups.firstOrNull { it.id == selectedId } ?: groups.first()
    }

    fun createGroup(context: Context, rawName: String): WordGroup? {
        val groups = loadGroups(context)
        if (groups.size >= MAX_GROUPS) return null
        val finalName = rawName.trim().ifEmpty { "${groups.size + 1}번 그룹" }
        val newGroup = WordGroup(
            id = UUID.randomUUID().toString(),
            name = finalName,
            words = mutableListOf()
        )
        groups.add(newGroup)
        saveGroups(context, groups)
        setSelectedGroupId(context, newGroup.id)
        return newGroup
    }

    fun renameGroup(context: Context, groupId: String, newName: String): Boolean {
        val cleanName = newName.trim()
        if (cleanName.isEmpty()) return false
        val groups = loadGroups(context)
        val target = groups.firstOrNull { it.id == groupId } ?: return false
        target.name = cleanName
        saveGroups(context, groups)
        return true
    }

    fun deleteGroup(context: Context, groupId: String) {
        val groups = loadGroups(context)
        val removed = groups.removeAll { it.id == groupId }
        if (!removed) return

        if (groups.isEmpty()) {
            val fallback = WordGroup(
                id = UUID.randomUUID().toString(),
                name = "1번 그룹",
                words = mutableListOf()
            )
            groups.add(fallback)
            saveGroups(context, groups)
            setSelectedGroupId(context, fallback.id)
            return
        }

        saveGroups(context, groups)
        val currentSelected = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_SELECTED_GROUP_ID, null)
        if (currentSelected == groupId || groups.none { it.id == currentSelected }) {
            setSelectedGroupId(context, groups.first().id)
        }
    }

    fun updateGroupWords(context: Context, groupId: String, updatedWords: List<VocabItem>) {
        val groups = loadGroups(context)
        val target = groups.firstOrNull { it.id == groupId } ?: return
        target.words.clear()
        target.words.addAll(updatedWords.take(MAX_WORDS_PER_GROUP))
        saveGroups(context, groups)
    }

    fun prependWordsToCurrentGroup(context: Context, newItems: List<VocabItem>): AddWordsResult {
        if (newItems.isEmpty()) return AddWordsResult(0, 0, false)
        val groups = loadGroups(context)
        val selectedId = getSelectedGroupId(context)
        val target = groups.firstOrNull { it.id == selectedId } ?: groups.first()

        val remaining = MAX_WORDS_PER_GROUP - target.words.size
        if (remaining <= 0) {
            return AddWordsResult(addedCount = 0, skippedCount = newItems.size, isFull = true)
        }

        val toAdd = newItems.take(remaining)
        val skipped = newItems.size - toAdd.size
        target.words.addAll(0, toAdd)
        saveGroups(context, groups)

        return AddWordsResult(
            addedCount = toAdd.size,
            skippedCount = skipped,
            isFull = target.words.size >= MAX_WORDS_PER_GROUP
        )
    }

    private fun loadLegacyWordsOrDefault(context: Context): List<VocabItem> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val savedJson = prefs.getString(KEY_LEGACY_VOCAB, null)
        if (savedJson != null) {
            val list = mutableListOf<VocabItem>()
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
            if (list.isNotEmpty()) return list
        }
        return defaultVocabulary()
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
