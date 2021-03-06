package com.ober.arctic.ui

import android.app.Activity
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.navigation.NavController
import androidx.navigation.Navigation.findNavController
import com.ober.arctic.App
import com.ober.arctic.ui.categories.CategoriesFragment
import com.ober.arctic.ui.change_key.ChangeEncryptionKeyFragment
import com.ober.arctic.ui.change_key.ChangeUnlockKeyFragment
import com.ober.arctic.ui.categories.entries.credentials.CredentialsFragment
import com.ober.arctic.ui.categories.entries.EntriesFragment
import com.ober.arctic.ui.settings.SettingsFragment
import com.ober.arctic.ui.unlock.UnlockFragment
import com.ober.arctic.util.AppExecutors
import com.ober.arctic.util.security.KeyManager
import com.ober.arcticpass.R
import com.ober.drawerxarrowdrawable.DrawerXArrowDrawable
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.nav_header.view.*
import net.grandcentrix.tray.AppPreferences
import java.util.*
import javax.inject.Inject

class MainActivity : AppCompatActivity() {

    @Inject
    lateinit var appPreferences: AppPreferences

    @Inject
    lateinit var appExecutors: AppExecutors

    @Inject
    lateinit var keyManager: KeyManager

    private var drawerIcon: DrawerXArrowDrawable? = null
    private var onBackPressedListener: OnBackPressedListener? = null
    var onImportFileListener: OnImportFileListener? = null
    var onSyncWithGoogleListener: OnSyncWithGoogleListener? = null
    private var navController: NavController? = null
    private var pauseTime: Date? = null
    private var screenBroadcastReceiver = ScreenBroadcastReceiver()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        App.appComponent?.inject(this)
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        setTheme()
        setContentView(R.layout.activity_main)
        setupToolbar()
        setupDrawerClickListeners()
        setupNavControllerListener()
        navController = findNavController(this, R.id.nav_host_fragment)
        registerReceiver(screenBroadcastReceiver, IntentFilter(Intent.ACTION_SCREEN_OFF))
    }

    override fun onPause() {
        super.onPause()
        pauseTime = Date()
    }

    override fun onResume() {
        super.onResume()
        pauseTime?.let {
            val now = Date()
            val timeout: Int = appPreferences.getInt(SettingsFragment.TIMEOUT, SettingsFragment.T_5_MINUTES)
            if (timeout != SettingsFragment.NO_TIMEOUT) {
                now.time = now.time - timeout
                if (it.before(now)) {
                    logout()
                }
            }
        }
        if (screenBroadcastReceiver.needToLogout) {
            if (appPreferences.getBoolean(SettingsFragment.SCREEN_LOCK, true)) {
                logout()
            } else {
                screenBroadcastReceiver.needToLogout = false
            }
        }
    }

    private fun logout() {
        screenBroadcastReceiver.needToLogout = false
        keyManager.clearKeys()
        navController?.navigate(R.id.reset)
    }

    private fun setTheme() {
        when (appPreferences.getString(
            THEME,
            LIGHT
        )) {
            LIGHT -> setTheme(R.style.AppTheme)
            DARK -> setTheme(R.style.AppThemeDark)
        }
    }

    private fun setupToolbar() {
        setSupportActionBar(toolbar)
        drawerIcon = DrawerXArrowDrawable(this, DrawerXArrowDrawable.Mode.DRAWER)
        drawerIcon?.color = ContextCompat.getColor(this, android.R.color.white)
        supportActionBar?.setDisplayShowTitleEnabled(true)
        enableDrawer()
    }

    private fun setupNavControllerListener() {
        findNavController(this, R.id.nav_host_fragment).addOnDestinationChangedListener { _, destination, _ ->
            appExecutors.mainThread().execute {
                when (destination.label) {
                    CategoriesFragment::class.java.simpleName -> {
                        toolbar?.navigationIcon = drawerIcon
                        enableDrawer()
                        toolbar_title.text = getString(R.string.categories)
                        drawer.setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED)
                    }
                    EntriesFragment::class.java.simpleName -> {
                        enableBackButton()
                        disableEditButton()
                        disableSaveButton()
                        onBackPressedListener = null
                        toolbar_title.text = getString(R.string.entries)
                        drawer.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED)
                    }
                    CredentialsFragment::class.java.simpleName -> toolbar_title.text = getString(R.string.credentials)
                    UnlockFragment::class.java.simpleName -> {
                        toolbar_title.text = ""
                        drawer.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED)
                        toolbar?.navigationIcon = null
                    }
                    ChangeEncryptionKeyFragment::class.java.simpleName -> {
                        toolbar_title.text = getString(R.string.change_encryption_key)
                        enableBackButton()
                        drawer.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED)
                    }
                    ChangeUnlockKeyFragment::class.java.simpleName -> {
                        toolbar_title.text = getString(R.string.change_unlock_key)
                        enableBackButton()
                        drawer.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED)
                    }
                    SettingsFragment::class.java.simpleName -> {
                        toolbar_title.text = getString(R.string.settings)
                        enableBackButton()
                        drawer.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED)
                    }
                }
                hideKeyboard()
            }
        }
    }

    private fun hideKeyboard() {
        val inputMethodManager = getSystemService(Activity.INPUT_METHOD_SERVICE) as InputMethodManager
        inputMethodManager.hideSoftInputFromWindow(window.decorView.windowToken, 0)
    }

    private fun enableBackButton() {
        drawerIcon?.setMode(DrawerXArrowDrawable.Mode.ARROW)
        toolbar?.setNavigationOnClickListener {
            onBackPressed()
        }
    }

    private fun enableDrawer() {
        drawerIcon?.setMode(DrawerXArrowDrawable.Mode.DRAWER)
        toolbar?.setNavigationOnClickListener {
            drawer.openDrawer(GravityCompat.START)
        }
    }

    override fun onBackPressed() {
        if (drawer != null && drawer.isDrawerOpen(GravityCompat.START)) {
            drawer.closeDrawers()
        } else if (onBackPressedListener == null || !onBackPressedListener!!.onBackPressed()) {
            super.onBackPressed()
        }
    }

    fun enableSaveButton(onSaveClickedListener: View.OnClickListener?, onBackPressedListener: OnBackPressedListener) {
        this.onBackPressedListener = onBackPressedListener
        disableEditButton()
        save_button.visibility = View.VISIBLE
        save_button.setOnClickListener(onSaveClickedListener)
    }

    private fun disableSaveButton() {
        save_button.visibility = View.GONE
        save_button.setOnClickListener(null)
    }

    fun enableEditButton(onEditClickedListener: View.OnClickListener?) {
        this.onBackPressedListener = null
        disableSaveButton()
        edit_button.visibility = View.VISIBLE
        edit_button.setOnClickListener(onEditClickedListener)
    }

    private fun disableEditButton() {
        edit_button.visibility = View.GONE
        edit_button.setOnClickListener(null)
    }

    private fun setupDrawerClickListeners() {
        val switch = nav_view.theme_switch
        when (appPreferences.getString(
            THEME,
            LIGHT
        )) {
            LIGHT -> switch.isChecked = false
            DARK -> switch.isChecked = true
        }
        switch.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                appPreferences.put(
                    THEME,
                    DARK
                )
            } else {
                appPreferences.put(
                    THEME,
                    LIGHT
                )
            }
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }
    }

    fun getDrawerView(): View {
        return nav_view
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == READ_REQUEST_CODE && resultCode == Activity.RESULT_OK) {
            data?.data?.also { uri ->
                onImportFileListener?.onFileSelected(uri)
            }
        } else if ((requestCode == GOOGLE_SIGN_IN_REQUEST_CODE || requestCode == GOOGLE_MAGIC_REQUEST_CODE) && resultCode == Activity.RESULT_OK) {
            onSyncWithGoogleListener?.onSyncComplete()
        }

    }

    override fun onSupportNavigateUp() = findNavController(this, R.id.nav_host_fragment).navigateUp()

    companion object {
        const val THEME = "theme"
        const val DARK = "dark"
        const val LIGHT = "light"
        const val READ_REQUEST_CODE = 32
        const val GOOGLE_SIGN_IN_REQUEST_CODE = 33
        const val GOOGLE_MAGIC_REQUEST_CODE = 65569
    }
}

interface OnBackPressedListener {
    fun onBackPressed(): Boolean
}

interface OnImportFileListener {
    fun onFileSelected(uri: Uri)
}

interface OnSyncWithGoogleListener {
    fun onSyncComplete()
}