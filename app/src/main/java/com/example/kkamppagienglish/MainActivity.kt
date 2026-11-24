package com.example.kkamppagienglish

import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.card.MaterialCardView
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

data class VocabItem(
    val english: String,
    val korean: String,
    val pos: String = "word",
    val example: String = "",
    var memorized: Boolean = false
)

class MainActivity : AppCompatActivity(), TextToSpeech.OnInitListener {

    private lateinit var tvProgress: TextView
    private lateinit var tvPhaseHint: TextView
    private lateinit var tvPartOfSpeech: TextView
    private lateinit var tvEnglishWord: TextView
    private lateinit var tvKoreanMeaning: TextView
    private lateinit var tvExample: TextView
    private lateinit var cardFlash: MaterialCardView
    private lateinit var btnPlayPause: Button
    private lateinit var btnTtsToggle: Button

    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var ttsEnabled = true

    private val handler = Handler(Looper.getMainLooper())
    private var isPlaying = false
    private var isMeaningShown = true
    private var intervalMs: Long = 1500L // 기본 1.5초 간격

    private val allWords = mutableListOf<VocabItem>()
    private var activeWords = mutableListOf<VocabItem>()
    private var currentIndex = 0

    private val flashRunnable = object : Runnable {
        override fun run() {
            if (!isPlaying || activeWords.isEmpty()) return

            if (!isMeaningShown) {
                // 2단계: 한국어 뜻과 예문을 깜빡이며 공개
                isMeaningShown = true
                renderCurrentWord()
                handler.postDelayed(this, intervalMs)
            } else {
                // 다음 단어로 이동 후 1단계(영단어만 표시 + 발음) 시작
                currentIndex = (currentIndex + 1) % activeWords.size
                isMeaningShown = false
                renderCurrentWord()
                speakCurrentWord()
                handler.postDelayed(this, intervalMs)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initViews()
        tts = TextToSpeech(this, this)
        loadWords()
        refreshActiveWords()
        renderCurrentWord()
    }

    private fun initViews() {
        tvProgress = findViewById(R.id.tvProgress)
        tvPhaseHint = findViewById(R.id.tvPhaseHint)
        tvPartOfSpeech = findViewById(R.id.tvPartOfSpeech)
        tvEnglishWord = findViewById(R.id.tvEnglishWord)
        tvKoreanMeaning = findViewById(R.id.tvKoreanMeaning)
        tvExample = findViewById(R.id.tvExample)
        cardFlash = findViewById(R.id.cardFlash)
        btnPlayPause = findViewById(R.id.btnPlayPause)
        btnTtsToggle = findViewById(R.id.btnTtsToggle)

        cardFlash.setOnClickListener {
            if (activeWords.isEmpty()) return@setOnClickListener
            if (!isMeaningShown) {
                isMeaningShown = true
                renderCurrentWord()
            }
            speakCurrentWord()
        }

        btnPlayPause.setOnClickListener {
            if (isPlaying) pauseBlinker() else startBlinker()
        }

        findViewById<Button>(R.id.btnNext).setOnClickListener {
            if (activeWords.isEmpty()) return@setOnClickListener
            currentIndex = (currentIndex + 1) % activeWords.size
            isMeaningShown = !isPlaying
            renderCurrentWord()
            speakCurrentWord()
            if (isPlaying) restartTimer()
        }

        findViewById<Button>(R.id.btnPrev).setOnClickListener {
            if (activeWords.isEmpty()) return@setOnClickListener
            currentIndex = if (currentIndex - 1 < 0) activeWords.size - 1 else currentIndex - 1
            isMeaningShown = !isPlaying
            renderCurrentWord()
            speakCurrentWord()
            if (isPlaying) restartTimer()
        }

        btnTtsToggle.setOnClickListener {
            ttsEnabled = !ttsEnabled
            btnTtsToggle.text = if (ttsEnabled) "발음 ON" else "발음 OFF"
        }

        findViewById<Button>(R.id.btnShuffle).setOnClickListener {
            activeWords.shuffle()
            currentIndex = 0
            isMeaningShown = !isPlaying
            renderCurrentWord()
            Toast.makeText(this, "단어 순서를 무작위로 섞었습니다.", Toast.LENGTH_SHORT).show()
        }

        findViewById<Button>(R.id.btnSpeedFast).setOnClickListener { setSpeed(800L, "빠름 (0.8초)") }
        findViewById<Button>(R.id.btnSpeedNormal).setOnClickListener { setSpeed(1500L, "보통 (1.5초)") }
        findViewById<Button>(R.id.btnSpeedSlow).setOnClickListener { setSpeed(2500L, "느림 (2.5초)") }

        findViewById<Button>(R.id.btnAddWord).setOnClickListener { showAddWordDialog() }

        findViewById<Button>(R.id.btnMemorized).setOnClickListener {
            if (activeWords.isEmpty()) return@setOnClickListener
            val current = activeWords[currentIndex]
            current.memorized = true
            saveWords()
            refreshActiveWords()
            renderCurrentWord()
            Toast.makeText(this, "'${current.english}' 암기 완료 처리됨", Toast.LENGTH_SHORT).show()
        }
    }

    private fun startBlinker() {
        if (activeWords.isEmpty()) return
        isPlaying = true
        btnPlayPause.text = "⏸ 일시정지"
        isMeaningShown = false
        renderCurrentWord()
        speakCurrentWord()
        handler.removeCallbacks(flashRunnable)
        handler.postDelayed(flashRunnable, intervalMs)
    }

    private fun pauseBlinker() {
        isPlaying = false
        btnPlayPause.text = "▶ 깜빡이 시작"
        handler.removeCallbacks(flashRunnable)
        isMeaningShown = true
        renderCurrentWord()
    }

    private fun restartTimer() {
        handler.removeCallbacks(flashRunnable)
        handler.postDelayed(flashRunnable, intervalMs)
    }

    private fun setSpeed(ms: Long, label: String) {
        intervalMs = ms
        Toast.makeText(this, "속도 변경: $label", Toast.LENGTH_SHORT).show()
        if (isPlaying) restartTimer()
    }

    private fun renderCurrentWord() {
        if (activeWords.isEmpty()) {
            tvProgress.text = "0 / 0"
            tvPartOfSpeech.text = "완료"
            tvEnglishWord.text = "모든 단어 암기 완료!"
            tvKoreanMeaning.visibility = View.VISIBLE
            tvKoreanMeaning.text = "새 단어를 추가하거나 초기화하세요"
            tvExample.visibility = View.GONE
            return
        }

        if (currentIndex >= activeWords.size) currentIndex = 0
        val item = activeWords[currentIndex]

        tvProgress.text = "${currentIndex + 1} / ${activeWords.size}"
        tvPartOfSpeech.text = item.pos
        tvEnglishWord.text = item.english

        if (isMeaningShown) {
            tvKoreanMeaning.visibility = View.VISIBLE
            tvKoreanMeaning.text = item.korean
            if (item.example.isNotBlank()) {
                tvExample.visibility = View.VISIBLE
                tvExample.text = item.example
            } else {
                tvExample.visibility = View.GONE
            }
            tvPhaseHint.text = if (isPlaying) "2단계: 뜻 확인 중..." else "정지 상태 (전체 표시)"
        } else {
            tvKoreanMeaning.visibility = View.INVISIBLE
            tvExample.visibility = View.INVISIBLE
            tvPhaseHint.text = "1단계: 영단어 뜻을 떠올려 보세요!"
        }
    }

    private fun speakCurrentWord() {
        if (!ttsEnabled || !ttsReady || activeWords.isEmpty()) return
        val word = activeWords[currentIndex].english
        tts?.speak(word, TextToSpeech.QUEUE_FLUSH, null, "vocab_tts")
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = tts?.setLanguage(Locale.US)
            ttsReady = result != TextToSpeech.LANG_MISSING_DATA && result != TextToSpeech.LANG_NOT_SUPPORTED
        }
    }

    private fun showAddWordDialog() {
        val wasPlaying = isPlaying
        if (wasPlaying) pauseBlinker()

        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_add_word, null)
        val etEnglish = dialogView.findViewById<EditText>(R.id.etEnglish)
        val etKorean = dialogView.findViewById<EditText>(R.id.etKorean)
        val etExample = dialogView.findViewById<EditText>(R.id.etExample)

        AlertDialog.Builder(this)
            .setTitle("새 영단어 추가")
            .setView(dialogView)
            .setPositiveButton("저장") { _, _ ->
                val eng = etEnglish.text.toString().trim()
                val kor = etKorean.text.toString().trim()
                val ex = etExample.text.toString().trim()
                if (eng.isNotEmpty() && kor.isNotEmpty()) {
                    allWords.add(0, VocabItem(eng, kor, "custom", ex, false))
                    saveWords()
                    refreshActiveWords()
                    currentIndex = 0
                    renderCurrentWord()
                }
            }
            .setNegativeButton("취소", null)
            .show()
    }

    private fun refreshActiveWords() {
        activeWords = allWords.filter { !it.memorized }.toMutableList()
        if (activeWords.isEmpty() && allWords.isNotEmpty()) {
            // 모두 암기 완료 시 전체 복습 모드로 자동 전환
            allWords.forEach { it.memorized = false }
            saveWords()
            activeWords = allWords.toMutableList()
            Toast.makeText(this, "전체 단어를 모두 암기하여 복습 모드로 초기화했습니다!", Toast.LENGTH_LONG).show()
        }
    }

    private fun saveWords() {
        val prefs = getSharedPreferences("kkamppagi_prefs", Context.MODE_PRIVATE)
        val arr = JSONArray()
        for (item in allWords) {
            val obj = JSONObject()
            obj.put("english", item.english)
            obj.put("korean", item.korean)
            obj.put("pos", item.pos)
            obj.put("example", item.example)
            obj.put("memorized", item.memorized)
            arr.put(obj)
        }
        prefs.edit().putString("vocab_list", arr.toString()).apply()
    }

    private fun loadWords() {
        val prefs = getSharedPreferences("kkamppagi_prefs", Context.MODE_PRIVATE)
        val savedJson = prefs.getString("vocab_list", null)
        allWords.clear()

        if (savedJson != null) {
            val arr = JSONArray(savedJson)
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                allWords.add(
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
            allWords.addAll(defaultVocabulary())
            saveWords()
        }
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

    override fun onDestroy() {
        handler.removeCallbacks(flashRunnable)
        tts?.stop()
        tts?.shutdown()
        super.onDestroy()
    }
}
