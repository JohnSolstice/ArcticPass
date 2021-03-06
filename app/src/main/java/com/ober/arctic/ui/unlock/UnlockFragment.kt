package com.ober.arctic.ui.unlock

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import android.view.inputmethod.EditorInfo
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProviders
import butterknife.OnClick
import com.ober.arctic.App
import com.ober.arctic.ui.BaseFragment
import com.ober.arctic.ui.DataViewModel
import com.ober.arctic.util.AppExecutors
import com.ober.arctic.util.security.*
import com.ober.arcticpass.R
import com.ober.vmrlink.Success
import kotlinx.android.synthetic.main.fragment_unlock.*
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import net.grandcentrix.tray.AppPreferences
import javax.inject.Inject

class UnlockFragment : BaseFragment() {

    @Inject
    lateinit var keyManager: KeyManager

    @Inject
    lateinit var appExecutors: AppExecutors

    @Inject
    lateinit var appPreferences: AppPreferences

    @Inject
    lateinit var fingerprintManager: FingerprintManager

    private lateinit var dataViewModel: DataViewModel

    private var fingerprintNeedsToReSave = false

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        App.appComponent!!.inject(this)
        dataViewModel = ViewModelProviders.of(mainActivity!!, viewModelFactory)[DataViewModel::class.java]
        if (keyManager.isUnlockKeyCorrect()) {
            navController?.navigate(R.id.action_unlockFragment_to_categoriesFragment)
        }
        return setAndBindContentView(inflater, container!!, R.layout.fragment_unlock)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupObserver()
        setupEditTextListeners()
    }

    override fun onResume() {
        super.onResume()
        setupFingerprintUnlock()
    }

    private fun setupEditTextListeners() {

        password_field.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                unlock_button.isEnabled = s.toString().trim().isNotEmpty()
            }

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        password_field.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE && unlock_button.isEnabled) {
                onUnlockClicked()
            }
            false
        }
    }

    private fun setupFingerprintUnlock() {
        if (fingerprintManager.isBiometricsAvailable(context!!)
            && fingerprintManager.isFingerprintEnabled()
        ) {
            showBiometricPrompt()
        } else {
            showPasswordLayout()
        }
    }

    private fun showBiometricPrompt() {
        fingerprintManager.authenticateAndSetUnlockKey2(
            context!!,
            this,
            object : FingerprintAuthenticatedCallback {
                override fun onSuccess() {
                    unlock_button.isEnabled = false
                    attemptUnlock()
                }

                override fun onInvalid() {
                    fingerprintNeedsToReSave = true
                    showPasswordLayout()
                }
            }
        )
    }

    private fun showPasswordLayout() {
        password_field.requestFocus()
        handler.post {
            showKeyboard()
        }
    }

    private fun isPasswordLayoutShowing(): Boolean {
        return password_layout.visibility == View.VISIBLE
    }

    @OnClick(R.id.unlock_button)
    fun onUnlockClicked() {
        unlock_button.isEnabled = false
        keyManager.unlockKey = password_field.text.toString().trim()
        attemptUnlock()
    }

    private fun attemptUnlock() {
        loading_spinner.visibility = View.VISIBLE
        GlobalScope.launch {
            val unlockKeyCorrect = keyManager.isUnlockKeyCorrect()
            appExecutors.mainThread().execute {
                if (unlockKeyCorrect) {
                    hideKeyboard()
                    dataViewModel.categoryCollectionLink.update()
                    if (fingerprintNeedsToReSave) {
                        fingerprintManager.enableFingerprint2(context!!)
                    }
                } else {
                    loading_spinner.visibility = View.GONE
                    if (isPasswordLayoutShowing()) {
                        val shake = AnimationUtils.loadAnimation(context, R.anim.shake)
                        password_field.startAnimation(shake)
                        password_field.setText("")
                    } else {
                        fingerprintNeedsToReSave = true
                        showPasswordLayout()
                    }
                }
            }
        }
    }

    private fun setupObserver() {
        dataViewModel.categoryCollectionLink.clear()
        dataViewModel.categoryCollectionLink.observe(viewLifecycleOwner, Observer {
            if (it is Success) {
                navController?.navigate(R.id.action_unlockFragment_to_categoriesFragment)
            }
        })
    }
}