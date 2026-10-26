package com.example.feature.intruder

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.data.database.TaalaDatabase
import com.example.core.data.model.IntruderEntity
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File

class IntruderViewModel(application: Application) : AndroidViewModel(application) {

    private val database = TaalaDatabase.getInstance(application)
    private val intruderDao = database.intruderDao()

    val records: StateFlow<List<IntruderEntity>> = intruderDao.getAllRecords()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun deleteRecord(id: String, photoUri: String?) {
        viewModelScope.launch {
            if (!photoUri.isNullOrBlank()) {
                try {
                    val file = File(photoUri)
                    if (file.exists()) file.delete()
                } catch (_: Exception) {}
            }
            intruderDao.deleteById(id)
        }
    }

    fun clearAllRecords() {
        viewModelScope.launch {
            val currentRecords = records.value
            currentRecords.forEach { record ->
                if (!record.photoUri.isNullOrBlank()) {
                    try {
                        val file = File(record.photoUri)
                        if (file.exists()) file.delete()
                    } catch (_: Exception) {}
                }
            }
            intruderDao.deleteAll()
        }
    }
}
