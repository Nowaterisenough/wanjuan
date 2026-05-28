package io.wanjuan.app.ui.book.info.edit

import android.net.Uri
import android.os.Bundle
import android.text.InputType
import android.view.Menu
import android.view.MenuItem
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.viewModels
import androidx.core.view.WindowInsetsCompat
import io.wanjuan.app.R
import io.wanjuan.app.base.VMBaseActivity
import io.wanjuan.app.constant.BookType
import io.wanjuan.app.data.entities.Book
import io.wanjuan.app.databinding.ActivityBookInfoEditBinding
import io.wanjuan.app.help.book.BookTagHelper
import io.wanjuan.app.help.book.BookHelp
import io.wanjuan.app.help.book.addType
import io.wanjuan.app.help.book.isAudio
import io.wanjuan.app.help.book.isImage
import io.wanjuan.app.help.book.isLocal
import io.wanjuan.app.help.book.isVideo
import io.wanjuan.app.help.book.removeType
import io.wanjuan.app.lib.dialogs.alert
import io.wanjuan.app.ui.book.changecover.ChangeCoverDialog
import io.wanjuan.app.ui.file.HandleFileContract
import io.wanjuan.app.utils.dpToPx
import io.wanjuan.app.utils.FileUtils
import io.wanjuan.app.utils.MD5Utils
import io.wanjuan.app.utils.externalFiles
import io.wanjuan.app.utils.inputStream
import io.wanjuan.app.utils.readUri
import io.wanjuan.app.utils.setOnApplyWindowInsetsListenerCompat
import io.wanjuan.app.utils.showDialogFragment
import io.wanjuan.app.utils.toastOnUi
import io.wanjuan.app.utils.viewbindingdelegate.viewBinding
import splitties.init.appCtx
import splitties.views.bottomPadding
import java.io.FileOutputStream

class BookInfoEditActivity :
    VMBaseActivity<ActivityBookInfoEditBinding, BookInfoEditViewModel>(),
    ChangeCoverDialog.CallBack {

    private val selectCover = registerForActivityResult(HandleFileContract()) {
        it.uri?.let { uri ->
            coverChangeTo(uri)
        }
    }

    override val binding by viewBinding(ActivityBookInfoEditBinding::inflate)
    override val viewModel by viewModels<BookInfoEditViewModel>()

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        viewModel.bookData.observe(this) { upView(it) }
        if (viewModel.bookData.value == null) {
            intent.getStringExtra("bookUrl")?.let {
                viewModel.loadBook(it)
            }
        }
        initView()
        initEvent()
    }

    override fun onCompatCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.book_info_edit, menu)
        return super.onCompatCreateOptionsMenu(menu)
    }

    override fun onCompatOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.menu_save -> saveData()
        }
        return super.onCompatOptionsItemSelected(item)
    }

    private fun initView() {
        binding.root.setOnApplyWindowInsetsListenerCompat { view, windowInsets ->
            val typeMask = WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.ime()
            val insets = windowInsets.getInsets(typeMask)
            view.bottomPadding = insets.bottom
            windowInsets
        }
    }

    private fun initEvent() = binding.run {
        tvChangeCover.setOnClickListener {
            viewModel.bookData.value?.let {
                showDialogFragment(
                    ChangeCoverDialog(it.name, it.author)
                )
            }
        }
        tvSelectCover.setOnClickListener {
            selectCover.launch {
                mode = HandleFileContract.IMAGE
            }
        }
        tvRefreshCover.setOnClickListener {
            viewModel.book?.customCoverUrl = tieCoverUrl.text?.toString()
            upCover()
        }
        tvEditTags.setOnClickListener {
            viewModel.book?.let { showTagEditDialog(it) }
        }
    }

    private fun upView(book: Book) = binding.run {
        tieBookName.setText(book.name)
        tieBookAuthor.setText(book.author)
        spType.setSelection(
            when {
                book.isVideo -> 4
                book.isImage -> 2
                book.isAudio -> 1
                else -> 0
            }
        )
        tieCoverUrl.setText(book.getDisplayCover())
        tieBookIntro.setText(book.getDisplayIntro())
        upBookTags(book)
        upCover()
    }

    private fun upBookTags(book: Book) = binding.run {
        val tags = BookTagHelper.parse(book.customTag)
        tvBookTags.text = tags.joinToString(" · ").ifBlank {
            getString(R.string.bookshelf_tag_none)
        }
    }

    private fun showTagEditDialog(book: Book) {
        viewModel.loadTagCandidates(book) { candidates ->
            val currentTags = BookTagHelper.parse(book.customTag)
            val allTags = (currentTags + candidates).distinct()
            val checkBoxes = mutableListOf<Pair<String, CheckBox>>()
            val container = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(16.dpToPx(), 4.dpToPx(), 16.dpToPx(), 0)
            }
            if (allTags.isEmpty()) {
                container.addView(TextView(this).apply {
                    setText(R.string.bookshelf_tag_none)
                })
            } else {
                allTags.forEach { tag ->
                    val checkBox = CheckBox(this).apply {
                        text = tag
                        isChecked = currentTags.any { it.equals(tag, ignoreCase = true) }
                    }
                    checkBoxes += tag to checkBox
                    container.addView(checkBox)
                }
            }
            val newTagEdit = EditText(this).apply {
                hint = getString(R.string.bookshelf_tag_new_hint)
                inputType = InputType.TYPE_CLASS_TEXT
                setSingleLine(false)
                minLines = 1
            }
            container.addView(newTagEdit)
            alert(titleResource = R.string.bookshelf_tag_edit) {
                customView { container }
                okButton {
                    val selected = checkBoxes
                        .filter { it.second.isChecked }
                        .map { it.first } + BookTagHelper.parse(newTagEdit.text?.toString())
                    book.customTag = BookTagHelper.join(selected)
                    upBookTags(book)
                }
                cancelButton()
            }
        }
    }

    private fun upCover() {
        viewModel.book?.let {
            binding.ivCover.load(it, false)
        }
    }

    private fun saveData() = binding.run {
        val book = viewModel.book ?: return@run
        val oldBook = book.copy()
        book.name = tieBookName.text?.toString() ?: ""
        book.author = tieBookAuthor.text?.toString() ?: ""
        val local = if (book.isLocal) BookType.local else 0
        val bookType = when (spType.selectedItemPosition) {
            4 -> BookType.video or local
            2 -> BookType.image or local
            1 -> BookType.audio or local
            else -> BookType.text or local
        }
        book.removeType(BookType.video, BookType.local, BookType.image, BookType.audio, BookType.text)
        book.addType(bookType)
        val customCoverUrl = tieCoverUrl.text?.toString()
        book.customCoverUrl = if (customCoverUrl == book.coverUrl) null else customCoverUrl
        val customIntro = tieBookIntro.text?.toString()
        book.customIntro = if (customIntro == book.intro) null else customIntro
        BookHelp.updateCacheFolder(oldBook, book)
        viewModel.saveBook(book) {
            setResult(RESULT_OK)
            finish()
        }
    }

    override fun coverChangeTo(coverUrl: String) {
        viewModel.book?.customCoverUrl = coverUrl
        binding.tieCoverUrl.setText(coverUrl)
        upCover()
    }

    private fun coverChangeTo(uri: Uri) {
        if (uri.scheme?.lowercase() in listOf("http", "https")) {
            coverChangeTo(uri.toString())
            return
        }
        readUri(uri) { fileDoc, inputStream ->
            runCatching {
                inputStream.use {
                    var file = this.externalFiles
                    val suffix = if (fileDoc.name.contains(".9.png", true)) {
                        ".9.png"
                    } else {
                        "." + fileDoc.name.substringAfterLast(".")
                    }
                    val fileName = uri.inputStream(this).getOrThrow().use {
                        MD5Utils.md5Encode(it) + suffix
                    }
                    file = FileUtils.createFileIfNotExist(file, "covers", fileName)
                    FileOutputStream(file).use { outputStream ->
                        inputStream.copyTo(outputStream)
                    }
                    coverChangeTo(file.absolutePath)
                }
            }.onFailure {
                appCtx.toastOnUi(it.localizedMessage)
            }
        }
    }

}
