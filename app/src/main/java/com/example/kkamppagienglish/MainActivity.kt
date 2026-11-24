package com.example.kkamppagienglish

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.card.MaterialCardView
import java.util.Locale

class MainActivity : AppCompatActivity(), TextToSpeech.OnInitListener {

    private lateinit var tvProgress: TextView
    private lateinit var btnCurrentGroup: Button
    private lateinit var tvPhaseHint: TextView
    private lateinit var tvPartOfSpeech: TextView
    private lateinit var tvEnglishWord: TextView
    private lateinit var tvKoreanMeaning: TextView
    private lateinit var tvExample: TextView
    private lateinit var tvCardTip: TextView
    private lateinit var cardFlash: MaterialCardView
    private lateinit var btnPlayPause: Button
    private lateinit var btnTtsToggle: Button

    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var ttsEnabled = true

    private val handler = Handler(Looper.getMainLooper())
    private var isPlaying = false
    private var isMeaningShown = true
    private var intervalMs: Long = 1500L

    private var currentGroup: WordGroup? = null
    private var groupWords = mutableListOf<VocabItem>()
    private var activeWords = mutableListOf<VocabItem>()
    private var currentIndex = 0

    private val flashRunnable = object : Runnable {
        override fun run() {
            if (!isPlaying || activeWords.isEmpty()) return

            if (!isMeaningShown) {
                isMeaningShown = true
                renderCurrentWord()
                handler.postDelayed(this, intervalMs)
            } else {
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
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContentView(R.layout.activity_main)

        applySystemBarInsets()
        initViews()
        tts = TextToSpeech(this, this)
        reloadVocabulary(resetIndex = true)
    }

    override fun onResume() {
        super.onResume()
        reloadVocabulary(resetIndex = true)
    }

    private fun applySystemBarInsets() {
        val rootView = findViewById<LinearLayout>(R.id.rootMain)
        val basePaddingPx = (18 * resources.displayMetrics.density).toInt()
        ViewCompat.setOnApplyWindowInsetsListener(rootView) { view, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(
                basePaddingPx + insets.left,
                basePaddingPx + insets.top,
                basePaddingPx + insets.right,
                basePaddingPx + insets.bottom
            )
            WindowInsetsCompat.CONSUMED
        }
    }

    private fun reloadVocabulary(resetIndex: Boolean) {
        val group = VocabStorage.getCurrentGroup(this)
        currentGroup = group
        groupWords = group.words.toMutableList()
        btnCurrentGroup.text = "📁 ${group.name} (${groupWords.size}/${VocabStorage.MAX_WORDS_PER_GROUP}) ▼"
        refreshActiveWords()
        if (resetIndex) currentIndex = 0
        renderCurrentWord()
    }

    private fun initViews() {
        tvProgress = findViewById(R.id.tvProgress)
        btnCurrentGroup = findViewById(R.id.btnCurrentGroup)
        tvPhaseHint = findViewById(R.id.tvPhaseHint)
        tvPartOfSpeech = findViewById(R.id.tvPartOfSpeech)
        tvEnglishWord = findViewById(R.id.tvEnglishWord)
        tvKoreanMeaning = findViewById(R.id.tvKoreanMeaning)
        tvExample = findViewById(R.id.tvExample)
        tvCardTip = findViewById(R.id.tvCardTip)
        cardFlash = findViewById(R.id.cardFlash)
        btnPlayPause = findViewById(R.id.btnPlayPause)
        btnTtsToggle = findViewById(R.id.btnTtsToggle)

        val openGroupManager = View.OnClickListener {
            if (isPlaying) pauseBlinker()
            startActivity(Intent(this, GroupManageActivity::class.java))
        }
        btnCurrentGroup.setOnClickListener(openGroupManager)
        findViewById<Button>(R.id.btnManageGroups).setOnClickListener(openGroupManager)

        cardFlash.setOnClickListener {
            if (activeWords.isEmpty()) return@setOnClickListener
            if (!isMeaningShown) {
                isMeaningShown = true
                renderCurrentWord()
            }
            speakCurrentWord()
        }

        btnPlayPause.setOnClickListener {
            if (activeWords.isEmpty()) {
                Toast.makeText(this, "현재 그룹에 단어가 없습니다. 먼저 단어를 추가해 주세요.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
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
            if (activeWords.isEmpty()) return@setOnClickListener
            activeWords.shuffle()
            currentIndex = 0
            isMeaningShown = !isPlaying
            renderCurrentWord()
            Toast.makeText(this, "단어 순서를 무작위로 섞었습니다.", Toast.LENGTH_SHORT).show()
        }

        findViewById<Button>(R.id.btnSpeedFast).setOnClickListener { setSpeed(800L, "빠름 (0.8초)") }
        findViewById<Button>(R.id.btnSpeedNormal).setOnClickListener { setSpeed(1500L, "보통 (1.5초)") }
        findViewById<Button>(R.id.btnSpeedSlow).setOnClickListener { setSpeed(2500L, "느림 (2.5초)") }

        findViewById<Button>(R.id.btnAddWord).setOnClickListener {
            if (isPlaying) pauseBlinker()
            startActivity(Intent(this, AddWordActivity::class.java))
        }

        findViewById<Button>(R.id.btnMemorized).setOnClickListener {
            if (activeWords.isEmpty()) return@setOnClickListener
            val current = activeWords[currentIndex]
            current.memorized = true
            currentGroup?.let { VocabStorage.updateGroupWords(this, it.id, groupWords) }
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
            tvPartOfSpeech.text = "빈 그룹"
            tvEnglishWord.text = "등록된 단어가 없습니다"
            tvKoreanMeaning.visibility = View.VISIBLE
            tvKoreanMeaning.text = "아래 '+ 내 단어 추가'로 단어를 등록하세요"
            tvExample.visibility = View.GONE
            tvCardTip.visibility = View.GONE
            tvPhaseHint.text = "그룹당 최대 ${VocabStorage.MAX_WORDS_PER_GROUP}개 단어를 등록할 수 있습니다"
            return
        }

        tvCardTip.visibility = View.VISIBLE
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

    private fun refreshActiveWords() {
        activeWords = groupWords.filter { !it.memorized }.toMutableList()
        if (activeWords.isEmpty() && groupWords.isNotEmpty()) {
            groupWords.forEach { it.memorized = false }
            currentGroup?.let { VocabStorage.updateGroupWords(this, it.id, groupWords) }
            activeWords = groupWords.toMutableList()
            Toast.makeText(this, "그룹 단어를 모두 암기하여 복습 모드로 초기화했습니다!", Toast.LENGTH_LONG).show()
        }
    }

    override fun onDestroy() {
        handler.removeCallbacks(flashRunnable)
        tts?.stop()
        tts?.shutdown()
        super.onDestroy()
    }
}
