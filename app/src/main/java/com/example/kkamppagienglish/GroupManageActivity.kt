package com.example.kkamppagienglish

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.card.MaterialCardView

class GroupManageActivity : AppCompatActivity() {

    private lateinit var tvGroupHeaderCount: TextView
    private lateinit var etNewGroupName: EditText
    private lateinit var btnCreateGroup: Button
    private lateinit var containerGroups: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContentView(R.layout.activity_group_manage)

        applySystemBarInsets()
        initViews()
        renderGroups()
    }

    private fun applySystemBarInsets() {
        val rootView = findViewById<LinearLayout>(R.id.rootGroupManage)
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
        tvGroupHeaderCount = findViewById(R.id.tvGroupHeaderCount)
        etNewGroupName = findViewById(R.id.etNewGroupName)
        btnCreateGroup = findViewById(R.id.btnCreateGroup)
        containerGroups = findViewById(R.id.containerGroups)

        findViewById<Button>(R.id.btnGroupBack).setOnClickListener { finish() }

        btnCreateGroup.setOnClickListener {
            val currentGroups = VocabStorage.loadGroups(this)
            if (currentGroups.size >= VocabStorage.MAX_GROUPS) {
                Toast.makeText(
                    this,
                    "단어 그룹은 최대 ${VocabStorage.MAX_GROUPS}개까지만 만들 수 있습니다.",
                    Toast.LENGTH_SHORT
                ).show()
                return@setOnClickListener
            }

            val inputName = etNewGroupName.text.toString().trim()
            val created = VocabStorage.createGroup(this, inputName)
            if (created != null) {
                etNewGroupName.setText("")
                Toast.makeText(
                    this,
                    "'${created.name}' 그룹이 생성되어 선택되었습니다.",
                    Toast.LENGTH_SHORT
                ).show()
                renderGroups()
            }
        }
    }

    private fun renderGroups() {
        val groups = VocabStorage.loadGroups(this)
        val selectedId = VocabStorage.getSelectedGroupId(this)

        tvGroupHeaderCount.text = "단어 그룹 관리 (${groups.size} / ${VocabStorage.MAX_GROUPS})"
        etNewGroupName.hint = "그룹 이름 입력 (예: ${groups.size + 1}번 그룹)"

        containerGroups.removeAllViews()
        val inflater = LayoutInflater.from(this)

        for (group in groups) {
            val row = inflater.inflate(R.layout.item_word_group, containerGroups, false)
            val card = row.findViewById<MaterialCardView>(R.id.cardGroupItem)
            val tvTitle = row.findViewById<TextView>(R.id.tvGroupTitle)
            val tvBadge = row.findViewById<TextView>(R.id.tvActiveBadge)
            val tvCount = row.findViewById<TextView>(R.id.tvGroupWordCount)
            val btnRename = row.findViewById<Button>(R.id.btnRenameGroup)
            val btnDelete = row.findViewById<Button>(R.id.btnDeleteGroup)

            val isSelected = group.id == selectedId
            tvTitle.text = group.name
            tvCount.text = "등록 단어: ${group.words.size} / ${VocabStorage.MAX_WORDS_PER_GROUP}개"

            if (isSelected) {
                tvBadge.visibility = View.VISIBLE
                card.strokeColor = ContextCompat.getColor(this, R.color.accent_blue)
                card.strokeWidth = (2 * resources.displayMetrics.density).toInt()
            } else {
                tvBadge.visibility = View.GONE
                card.strokeColor = ContextCompat.getColor(this, R.color.divider_color)
                card.strokeWidth = (1 * resources.displayMetrics.density).toInt()
            }

            card.setOnClickListener {
                VocabStorage.setSelectedGroupId(this, group.id)
                Toast.makeText(this, "'${group.name}' 그룹을 선택했습니다.", Toast.LENGTH_SHORT).show()
                finish()
            }

            btnRename.setOnClickListener {
                showRenameDialog(group)
            }

            btnDelete.setOnClickListener {
                showDeleteDialog(group)
            }

            containerGroups.addView(row)
        }
    }

    private fun showRenameDialog(group: WordGroup) {
        val editText = EditText(this).apply {
            setText(group.name)
            setSelection(group.name.length)
            hint = "새 그룹 이름 입력"
            setPadding(48, 32, 48, 32)
        }

        AlertDialog.Builder(this)
            .setTitle("그룹 이름 변경")
            .setView(editText)
            .setPositiveButton("저장") { _, _ ->
                val newName = editText.text.toString().trim()
                if (newName.isEmpty()) {
                    Toast.makeText(this, "그룹 이름을 입력해 주세요.", Toast.LENGTH_SHORT).show()
                } else {
                    VocabStorage.renameGroup(this, group.id, newName)
                    Toast.makeText(this, "그룹 이름이 '$newName'(으)로 변경되었습니다.", Toast.LENGTH_SHORT).show()
                    renderGroups()
                }
            }
            .setNegativeButton("취소", null)
            .show()
    }

    private fun showDeleteDialog(group: WordGroup) {
        AlertDialog.Builder(this)
            .setTitle("단어 그룹 삭제")
            .setMessage("'${group.name}' 그룹과 등록된 단어(${group.words.size}개)를 모두 삭제하시겠습니까?")
            .setPositiveButton("삭제") { _, _ ->
                VocabStorage.deleteGroup(this, group.id)
                Toast.makeText(this, "'${group.name}' 그룹이 삭제되었습니다.", Toast.LENGTH_SHORT).show()
                renderGroups()
            }
            .setNegativeButton("취소", null)
            .show()
    }
}
