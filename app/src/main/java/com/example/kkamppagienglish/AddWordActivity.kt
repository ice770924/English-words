package com.example.kkamppagienglish

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
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

class AddWordActivity : AppCompatActivity() {

    private lateinit var sectionManual: ScrollView
    private lateinit var sectionCamera: LinearLayout
    private lateinit var tvScanStatus: TextView
    private lateinit var ivPreview: ImageView
    private lateinit var containerScannedWords: LinearLayout
    private lateinit var btnSaveScanned: Button

    private var currentPhotoUri: Uri? = null
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
                ivPreview.visibility = View.VISIBLE
                ivPreview.setImageURI(uri)
                processImageWithMlKit(uri)
            }
        }

    private val pickGalleryLauncher =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            if (uri != null) {
                ivPreview.visibility = View.VISIBLE
                ivPreview.setImageURI(uri)
                processImageWithMlKit(uri)
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContentView(R.layout.activity_add_word)

        applySystemBarInsets()
        initViews()
    }

    private fun applySystemBarInsets() {
        val rootView = findViewById<LinearLayout>(R.id.rootAddWord)
        val basePaddingPx = (18 * resources.displayMetrics.density).toInt()
        ViewCompat.setOnApplyWindowInsetsListener(rootView) { view, windowInsets ->
            val insets = windowInsets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.ime()
            )
            view.setPadding(
                basePaddingPx + insets.left,
                basePaddingPx + insets.top,
                basePaddingPx + insets.right,
                basePaddingPx + insets.bottom
            )
            WindowInsetsCompat.CONSUMED
        }
    }

    private fun initViews() {
        sectionManual = findViewById(R.id.sectionManual)
        sectionCamera = findViewById(R.id.sectionCamera)
        tvScanStatus = findViewById(R.id.tvScanStatus)
        ivPreview = findViewById(R.id.ivPreview)
        containerScannedWords = findViewById(R.id.containerScannedWords)
        btnSaveScanned = findViewById(R.id.btnSaveScanned)

        findViewById<Button>(R.id.btnBack).setOnClickListener { finish() }

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

        // 1. 직접 입력 저장
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

            VocabStorage.prependWords(
                this,
                listOf(VocabItem(eng, kor, "custom", ex, false))
            )
            Toast.makeText(this, "'$eng' 단어가 추가되었습니다!", Toast.LENGTH_SHORT).show()
            finish()
        }

        // 2. 카메라 촬영 및 앨범 선택
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

            VocabStorage.prependWords(this, itemsToAdd.reversed())
            Toast.makeText(this, "${itemsToAdd.size}개의 영단어가 단어장에 추가되었습니다!", Toast.LENGTH_SHORT).show()
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
                        tvScanStatus.text = "총 ${extractedWords.size}개의 영단어를 찾았습니다. 원하는 단어의 뜻을 입력하고 추가하세요."
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
        // 알파벳 2글자 이상 영단어 추출 및 중복 제거
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
        for (word in words) {
            val row = inflater.inflate(R.layout.item_scanned_word, containerScannedWords, false)
            val etEng = row.findViewById<EditText>(R.id.etScannedEnglish)
            etEng.setText(word)
            containerScannedWords.addView(row)
            scannedRowViews.add(row)
        }
        btnSaveScanned.visibility = View.VISIBLE
    }
}
