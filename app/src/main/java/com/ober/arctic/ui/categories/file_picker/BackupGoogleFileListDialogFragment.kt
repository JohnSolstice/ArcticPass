package com.ober.arctic.ui.categories.file_picker

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProviders
import androidx.recyclerview.widget.LinearLayoutManager
import butterknife.OnClick
import com.google.api.services.drive.model.File
import com.ober.arctic.ui.BaseDialogFragment
import com.ober.arctic.ui.DataViewModel
import com.ober.arctic.util.ui.ViewState
import com.ober.arcticpass.BuildConfig
import com.ober.arcticpass.R
import com.ober.vmrlink.Error
import com.ober.vmrlink.Loading
import com.ober.vmrlink.Success
import kotlinx.android.synthetic.main.fragment_file_list.*

class BackupGoogleFileListDialogFragment : BaseDialogFragment(), FileSelectedListener {

    private lateinit var adapter: FileListAdapter

    private lateinit var dataViewModel: DataViewModel

    private lateinit var callback: (file: File) -> Unit

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view: View = setAndBindContentView(inflater, container, R.layout.fragment_file_list)
        dataViewModel = ViewModelProviders.of(this, viewModelFactory)[DataViewModel::class.java]
        dialog?.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        return view
    }

    @Suppress("ConstantConditionIf")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        setupRecyclerView()
        if (BuildConfig.BUILD_TYPE == "debug") {
            setupTestList()
        } else {
            setupObservers()
        }
        import_button.isEnabled = false
    }

    override fun onFileSelected() {
        import_button.isEnabled = true
    }

    @OnClick(R.id.cancel_button)
    fun onCancelClicked() {
        dismiss()
    }

    @OnClick(R.id.import_button)
    fun onImportClicked() {
        adapter.selectedFile?.let(callback)
        dismiss()
    }

    private fun setupRecyclerView() {
        adapter = FileListAdapter(this)
        files_recycler_view.adapter = adapter
        files_recycler_view.layoutManager = LinearLayoutManager(context)
    }

    private fun setupObservers() {
        dataViewModel.backupFilesLink.observe(viewLifecycleOwner, Observer {
            it.data?.let { fileList ->
                adapter.files = fileList
            }

            when (it) {
                is Success -> {
                    if (adapter.files.isEmpty()) {
                        setViewState(ViewState.EMPTY)
                    } else {
                        setViewState(ViewState.DATA)
                    }
                }
                is Loading -> {
                    if (adapter.files.isEmpty()) {
                        setViewState(ViewState.LOADING)
                    } else {
                        setViewState(ViewState.DATA)
                    }
                }
                is Error -> {
                    if (adapter.files.isEmpty()) {
                        setViewState(ViewState.ERROR)
                    } else {
                        setViewState(ViewState.DATA)
                    }
                }
            }
        })
        dataViewModel.backupFilesLink.update()
    }

    private fun setupTestList() {
        val fileList = mutableListOf<File>()
        for (i in 0 until 10) {
            val file = File()
            file.name = i.toString()
            fileList.add(file)
        }
        adapter.files = fileList
        setViewState(ViewState.DATA)
    }

    private fun setViewState(viewState: ViewState) {
        files_recycler_view.visibility = View.GONE
        empty_text.visibility = View.GONE
        error_text.visibility = View.GONE
        loading_spinner.visibility = View.GONE

        when (viewState) {
            ViewState.DATA -> {
                files_recycler_view.visibility = View.VISIBLE
            }
            ViewState.EMPTY -> {
                empty_text.visibility = View.VISIBLE
            }
            ViewState.ERROR -> {
                error_text.visibility = View.VISIBLE
            }
            ViewState.LOADING -> {
                loading_spinner.visibility = View.VISIBLE
            }
        }
    }

    companion object {
        fun newInstance(callback: (file: File) -> Unit): BackupGoogleFileListDialogFragment {
            val fragment = BackupGoogleFileListDialogFragment()
            fragment.callback = callback
            return fragment
        }
    }
}