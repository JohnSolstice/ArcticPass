package com.ober.arctic.ui.init

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import butterknife.OnClick
import com.ober.arctic.App
import com.ober.arctic.ui.BaseFragment
import com.ober.arctic.util.security.Encryption
import com.ober.arctic.util.security.KeyManager
import com.ober.arcticpass.R
import kotlinx.android.synthetic.main.fragment_init.*
import javax.inject.Inject

class InitFragment : BaseFragment() {

    @Inject
    lateinit var encryption: Encryption

    @Inject
    lateinit var keyManager: KeyManager

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        App.appComponent!!.inject(this)
        return setAndBindContentView(inflater, container!!, R.layout.fragment_init)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupEditTextListeners()
    }

    @OnClick(R.id.generate_button)
    fun generate() {
        encryption_field.setText(encryption.generateRandomKey(24))
    }

    @OnClick(R.id.done_button)
    fun onDoneClicked() {
        keyManager.unlockKey = unlock_password_field.text.toString().trim()
        keyManager.saveEncryptionKey(encryption_field.text.toString().trim())
        navController?.navigate(R.id.action_initFragment_to_categoriesFragment)
    }

    private fun setupEditTextListeners() {
        var recoveryKeyValid = false
        var unlockKeyValid = false

        encryption_field.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                recoveryKeyValid = !s.toString().trim().isEmpty()
                done_button.isEnabled = recoveryKeyValid && unlockKeyValid
            }

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        unlock_password_field.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                unlockKeyValid = !s.toString().trim().isEmpty()
                done_button.isEnabled = recoveryKeyValid && unlockKeyValid
            }

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        unlock_password_field.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE && done_button.isEnabled) {
                onDoneClicked()
            }
            false
        }
    }
}