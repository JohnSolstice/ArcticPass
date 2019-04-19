package com.ober.arctic.repository

import android.annotation.SuppressLint
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.ober.arctic.App
import com.ober.arctic.data.cache.LiveDataHolder
import com.ober.arctic.data.database.MainDatabase
import com.ober.arctic.data.model.Category
import com.ober.arctic.data.model.CategoryCollection
import com.ober.arctic.data.model.EncryptedDataHolder
import com.ober.arctic.util.AppExecutors
import com.ober.arctic.util.security.Encryption
import com.ober.arctic.util.security.KeyManager
import com.ober.arcticpass.R
import javax.inject.Inject
import java.util.Collections.singletonList
import com.google.api.client.http.ByteArrayContent
import com.google.api.services.drive.Drive
import com.google.api.services.drive.model.File
import com.ober.arctic.util.DriveServiceHolder
import com.ober.arctic.util.FileUtil
import com.ober.vmrlink.Resource
import com.ober.vmrlink.Source
import java.io.BufferedReader
import java.io.InputStreamReader
import java.lang.Exception

class DataRepository @Inject constructor(
    private var mainDatabase: MainDatabase,
    private var keyManager: KeyManager,
    private var liveDataHolder: LiveDataHolder,
    private var gson: Gson,
    private var encryption: Encryption,
    private var appExecutors: AppExecutors,
    private var driveServiceHolder: DriveServiceHolder
) {

    @SuppressLint("SimpleDateFormat")
    fun saveCategoryCollection(categoryCollection: CategoryCollection) {
        liveDataHolder.setCategoryCollection(categoryCollection)
        appExecutors.diskIO().execute {
            val encryptedDataHolder: EncryptedDataHolder =
                encryption.encryptStringData(gson.toJson(categoryCollection), keyManager.getEncryptionKey()!!)
            mainDatabase.encryptedDataHolderDao().insert(encryptedDataHolder)
            createFile(gson.toJson(encryptedDataHolder))
        }
    }

    fun loadCategoryCollection(createDefaultsIfNecessary: Boolean) {
        if (liveDataHolder.getCategoryCollection().value == null) {
            val source = mainDatabase.encryptedDataHolderDao().getEncryptedDataHolder()
            liveDataHolder.getCategoryCollectionLiveData().addSource(source) { encryptedDataHolder ->
                liveDataHolder.getCategoryCollectionLiveData().removeSource(source)
                when {
                    encryptedDataHolder != null -> {
                        appExecutors.miscellaneousThread().execute {
                            val categoryCollection: CategoryCollection = gson.fromJson(
                                encryption.decryptStringData(
                                    encryptedDataHolder.encryptedJson,
                                    encryptedDataHolder.salt,
                                    keyManager.getEncryptionKey()!!
                                ),
                                genericType<CategoryCollection>()
                            )
                            appExecutors.mainThread().execute {
                                liveDataHolder.setCategoryCollection(categoryCollection)
                            }
                        }
                    }
                    createDefaultsIfNecessary -> {
                        val domainList = arrayListOf<Category>()
                        domainList.add(Category(App.app!!.getString(R.string.business), arrayListOf()))
                        domainList.add(Category(App.app!!.getString(R.string.personal), arrayListOf()))
                        val domainCollection = CategoryCollection(domainList)
                        saveCategoryCollection(domainCollection)
                    }
                    else -> appExecutors.mainThread().execute {
                        liveDataHolder.setCategoryCollection(null)
                    }
                }
            }
        }
    }

    private fun createFile(content: String) {
        driveServiceHolder.getDriveService()?.let { drive ->
            appExecutors.networkIO().execute {
                val folderId = getFolderId(drive)

                val fileMetaData = File()
                    .setParents(singletonList(folderId))
                    .setMimeType("text/plain")
                    .setName(FileUtil.buildFileName())

                val inputStream = ByteArrayContent.fromString("text/plain", content)

                drive.files().create(fileMetaData, inputStream).execute()
            }
        }

    }

    private fun getFolderId(drive: Drive): String? {
        val list = drive.files().list().execute()
        for (file in list.files) {
            if (file.name == App.app!!.getString(R.string.folder_name)) {
                return file.id
            }
        }
        return createFolder(drive)
    }

    private fun createFolder(drive: Drive): String? {
        val folderMetaData = File()
            .setName(App.app!!.getString(R.string.folder_name))
            .setMimeType("application/vnd.google-apps.folder")
        val folder = drive.files().create(folderMetaData)
            .setFields("id")
            .execute()

        return folder.id
    }

    fun getBackupFiles(): MutableLiveData<Resource<List<File>>> {
        val liveData = MutableLiveData<Resource<List<File>>>()
        driveServiceHolder.getDriveService()?.let { drive ->
            appExecutors.networkIO().execute {
                try {
                    val files = arrayListOf<File>()
                    val list = drive.files().list().execute()
                    for (file in list.files) {
                        if (file.name.contains(App.app!!.getString(R.string.backup))) {
                            files.add(file)
                        }
                    }
                    appExecutors.mainThread().execute {
                        liveData.value = Resource.success(files, Source.NETWORK)
                    }
                } catch (e: Exception) {
                    appExecutors.mainThread().execute {
                        liveData.value = Resource.error("error", null)
                    }
                }

            }
        }
        return liveData
    }

    fun getSingleBackupFile(file: File): MutableLiveData<Resource<String>> {
        val liveData = MutableLiveData<Resource<String>>()
        driveServiceHolder.getDriveService()?.let { drive ->
            appExecutors.networkIO().execute {
                try {
                    val inputStream = drive.files().get(file.id).executeMediaAsInputStream()
                    val reader = BufferedReader(InputStreamReader(inputStream))
                    val stringBuilder = StringBuilder()

                    var line: String? = reader.readLine()
                    while (line != null) {
                        stringBuilder.append(line)
                        line = reader.readLine()
                    }

                    appExecutors.mainThread().execute {
                        liveData.value = Resource.success(stringBuilder.toString(), Source.NETWORK)
                    }
                } catch (e: Exception) {
                    appExecutors.mainThread().execute {
                        liveData.value = Resource.error("error", null)
                    }
                }

            }
        }
        return liveData
    }

    fun getCategoryCollectionLiveData(): LiveData<CategoryCollection> {
        return liveDataHolder.getCategoryCollection()
    }

    private inline fun <reified T> genericType() = object : TypeToken<T>() {}.type
}