package com.example.kkamppagienglish

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Rect
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.tabs.TabLayout
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.io.File
import java.util.Locale

class AddWordActivity : AppCompatActivity() {

    private lateinit var tvTargetGroupInfo: TextView
    private lateinit var sectionManual: ScrollView
    private lateinit var sectionCamera: LinearLayout
    private lateinit var scrollCameraContent: ScrollView
    private lateinit var tvScanStatus: TextView
    private lateinit var ivPreview: ImageView
    private lateinit var containerScannedWords: LinearLayout
    private lateinit var btnSaveScanned: Button

    private var currentPhotoUri: Uri? = null
    private var hasPreviewImage = false
    private val scannedRowViews = mutableListOf<View>()

    private val requestCameraPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                launchCameraCapture()
            } else {
                Toast.makeText(this, "카메라 권한이 필요합니다. 설정에서 허용해 주세요.", Toast.LENGTH_SHORT).show()
            }
        }

    private val takePictureLauncher =
        registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
            val uri = currentPhotoUri
            if (success && uri != null) {
                hasPreviewImage = true
                ivPreview.visibility = View.VISIBLE
                ivPreview.setImageURI(uri)
                processImageWithMlKit(uri)
            }
        }

    private val pickGalleryLauncher =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            if (uri != null) {
                hasPreviewImage = true
                ivPreview.visibility = View.VISIBLE
                ivPreview.setImageURI(uri)
                processImageWithMlKit(uri)
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContentView(R.layout.activity_add_word)

        initViews()
        applySystemBarInsets()
        updateTargetGroupBanner()
    }

    override fun onResume() {
        super.onResume()
        updateTargetGroupBanner()
    }

    private fun updateTargetGroupBanner() {
        val group = VocabStorage.getCurrentGroup(this)
        val count = group.words.size
        val max = VocabStorage.MAX_WORDS_PER_GROUP
        if (count >= max) {
            tvTargetGroupInfo.text = "${group.name} ($count/$max 가득 참) · 새 그룹을 추가하세요"
            tvTargetGroupInfo.setTextColor(ContextCompat.getColor(this, R.color.accent_red))
        } else {
            tvTargetGroupInfo.text = "대상 그룹: ${group.name} ($count / $max단어 · ${max - count}개 가능)"
            tvTargetGroupInfo.setTextColor(ContextCompat.getColor(this, R.color.text_primary))
        }
    }

    private fun applySystemBarInsets() {
        val rootView = findViewById<LinearLayout>(R.id.rootAddWord)
        val basePaddingPx = (18 * resources.displayMetrics.density).toInt()
        ViewCompat.setOnApplyWindowInsetsListener(rootView) { view, windowInsets ->
            val sysBars = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            val imeInsets = windowInsets.getInsets(WindowInsetsCompat.Type.ime())
            val isKeyboardOpen = imeInsets.bottom > sysBars.bottom

            view.setPadding(
                basePaddingPx + sysBars.left,
                basePaddingPx + sysBars.top,
                basePaddingPx + sysBars.right,
                basePaddingPx + maxOf(sysBars.bottom, imeInsets.bottom)
            )

            if (sectionCamera.visibility == View.VISIBLE) {
                if (isKeyboardOpen) {
                    btnSaveScanned.visibility = View.GONE
                    ivPreview.visibility = View.GONE
                    scrollFocusedFieldIntoView()
                } else {
                    if (scannedRowViews.isNotEmpty()) {
                        btnSaveScanned.visibility = View.VISIBLE
                    }
                    if (hasPreviewImage) {
                        ivPreview.visibility = View.VISIBLE
                    }
                }
            }

            WindowInsetsCompat.CONSUMED
        }
    }

    private fun scrollFocusedFieldIntoView() {
        val focused = currentFocus ?: return
        scrollCameraContent.post {
            val rect = Rect(0, 0, focused.width, focused.height)
            focused.requestRectangleOnScreen(rect, false)
        }
    }

    private fun initViews() {
        tvTargetGroupInfo = findViewById(R.id.tvTargetGroupInfo)
        sectionManual = findViewById(R.id.sectionManual)
        sectionCamera = findViewById(R.id.sectionCamera)
        scrollCameraContent = findViewById(R.id.scrollCameraContent)
        tvScanStatus = findViewById(R.id.tvScanStatus)
        ivPreview = findViewById(R.id.ivPreview)
        containerScannedWords = findViewById(R.id.containerScannedWords)
        btnSaveScanned = findViewById(R.id.btnSaveScanned)

        findViewById<Button>(R.id.btnBack).setOnClickListener { finish() }

        findViewById<Button>(R.id.btnSwitchGroupFromAdd).setOnClickListener {
            startActivity(Intent(this, GroupManageActivity::class.java))
        }

        val tabLayout = findViewById<TabLayout>(R.id.tabLayoutMode)
        tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                if (tab?.position == 0) {
                    sectionManual.visibility = View.VISIBLE
                    sectionCamera.visibility = View.GONE
                } else {
                    sectionManual.visibility = View.GONE
                    sectionCamera.visibility = View.VISIBLE
                }
            }
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })

        val etEnglish = findViewById<EditText>(R.id.etEnglish)
        val etKorean = findViewById<EditText>(R.id.etKorean)
        val etExample = findViewById<EditText>(R.id.etExample)

        findViewById<Button>(R.id.btnSaveManual).setOnClickListener {
            val eng = etEnglish.text.toString().trim()
            val kor = etKorean.text.toString().trim()
            val ex = etExample.text.toString().trim()

            if (eng.isEmpty() || kor.isEmpty()) {
                Toast.makeText(this, "영단어와 한국어 뜻을 모두 입력해 주세요.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val result = VocabStorage.prependWordsToCurrentGroup(
                this,
                listOf(VocabItem(eng, kor, "custom", ex, false))
            )

            if (result.addedCount == 0) {
                Toast.makeText(
                    this,
                    "그룹당 최대 ${VocabStorage.MAX_WORDS_PER_GROUP}개까지만 등록할 수 있습니다. 새 그룹을 만들어 주세요.",
                    Toast.LENGTH_LONG
                ).show()
                return@setOnClickListener
            }

            Toast.makeText(this, "'$eng' 단어가 추가되었습니다!", Toast.LENGTH_SHORT).show()
            finish()
        }

        findViewById<Button>(R.id.btnTakePhoto).setOnClickListener {
            val granted = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED

            if (granted) {
                launchCameraCapture()
            } else {
                requestCameraPermission.launch(Manifest.permission.CAMERA)
            }
        }

        findViewById<Button>(R.id.btnPickGallery).setOnClickListener {
            pickGalleryLauncher.launch("image/*")
        }

        btnSaveScanned.setOnClickListener {
            val itemsToAdd = mutableListOf<VocabItem>()
            for (row in scannedRowViews) {
                val cb = row.findViewById<CheckBox>(R.id.cbSelectWord)
                val etEng = row.findViewById<EditText>(R.id.etScannedEnglish)
                val etKor = row.findViewById<EditText>(R.id.etScannedKorean)

                if (cb.isChecked) {
                    val eng = etEng.text.toString().trim()
                    if (eng.isNotEmpty()) {
                        val kor = etKor.text.toString().trim().ifEmpty { "뜻 미입력 (카메라 스캔)" }
                        itemsToAdd.add(VocabItem(eng, kor, "scan", "", false))
                    }
                }
            }

            if (itemsToAdd.isEmpty()) {
                Toast.makeText(this, "추가할 영단어를 1개 이상 선택해 주세요.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val result = VocabStorage.prependWordsToCurrentGroup(this, itemsToAdd.reversed())
            if (result.addedCount == 0) {
                Toast.makeText(
                    this,
                    "현재 그룹이 가득 찼습니다(최대 ${VocabStorage.MAX_WORDS_PER_GROUP}개). 새 그룹을 만들어 추가해 주세요.",
                    Toast.LENGTH_LONG
                ).show()
                return@setOnClickListener
            }

            if (result.skippedCount > 0) {
                Toast.makeText(
                    this,
                    "그룹당 ${VocabStorage.MAX_WORDS_PER_GROUP}개 제한으로 ${result.addedCount}개만 추가되었습니다. (${result.skippedCount}개 제외)",
                    Toast.LENGTH_LONG
                ).show()
            } else {
                Toast.makeText(
                    this,
                    "${result.addedCount}개의 영단어가 그룹에 추가되었습니다!",
                    Toast.LENGTH_SHORT
                ).show()
            }
            finish()
        }
    }

    private fun launchCameraCapture() {
        val imageDir = File(cacheDir, "images").apply { mkdirs() }
        val photoFile = File(imageDir, "scan_${System.currentTimeMillis()}.jpg")
        val uri = FileProvider.getUriForFile(
            this,
            "${packageName}.fileprovider",
            photoFile
        )
        currentPhotoUri = uri
        takePictureLauncher.launch(uri)
    }

    private fun processImageWithMlKit(uri: Uri) {
        tvScanStatus.text = "사진에서 영단어를 분석하는 중입니다..."
        containerScannedWords.removeAllViews()
        scannedRowViews.clear()
        btnSaveScanned.visibility = View.GONE

        try {
            val image = InputImage.fromFilePath(this, uri)
            val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

            recognizer.process(image)
                .addOnSuccessListener { visionText ->
                    val extractedWords = extractEnglishWords(visionText.text)
                    if (extractedWords.isEmpty()) {
                        tvScanStatus.text = "영단어를 찾지 못했습니다. 글자가 선명하게 보이도록 다시 촬영해 주세요."
                    } else {
                        tvScanStatus.text = "총 ${extractedWords.size}개의 영단어를 찾았습니다. 뜻을 입력한 뒤 키보드를 내리면 일괄 추가 버튼이 나타납니다."
                        populateScannedWords(extractedWords)
                    }
                }
                .addOnFailureListener { e ->
                    tvScanStatus.text = "이미지 인식 중 오류가 발생했습니다: ${e.localizedMessage}"
                }
        } catch (e: Exception) {
            tvScanStatus.text = "이미지를 불러오지 못했습니다: ${e.localizedMessage}"
        }
    }

    private fun extractEnglishWords(rawText: String): List<String> {
        val regex = Regex("[A-Za-z]{2,}")
        val seen = LinkedHashSet<String>()
        for (match in regex.findAll(rawText)) {
            val word = match.value.lowercase(Locale.US)
            if (word.length in 2..28) {
                seen.add(word)
            }
        }
        return seen.toList()
    }

    private fun populateScannedWords(words: List<String>) {
        val inflater = LayoutInflater.from(this)
        for ((index, word) in words.withIndex()) {
            val row = inflater.inflate(R.layout.item_scanned_word, containerScannedWords, false)
            val etEng = row.findViewById<EditText>(R.id.etScannedEnglish)
            val etKor = row.findViewById<EditText>(R.id.etScannedKorean)
            etEng.setText(word)

            etKor.imeOptions = if (index == words.lastIndex) {
                EditorInfo.IME_ACTION_DONE
            } else {
                EditorInfo.IME_ACTION_NEXT
            }

            val focusListener = View.OnFocusChangeListener { v, hasFocus ->
                if (hasFocus) {
                    scrollCameraContent.postDelayed({
                        val rect = Rect(0, 0, v.width, v.height)
                        v.requestRectangleOnScreen(rect, false)
                    }, 150)
                }
            }
            etEng.onFocusChangeListener = focusListener
            etKor.onFocusChangeListener = focusListener

            containerScannedWords.addView(row)
            scannedRowViews.add(row)
        }
        btnSaveScanned.visibility = View.VISIBLE
    }
}
