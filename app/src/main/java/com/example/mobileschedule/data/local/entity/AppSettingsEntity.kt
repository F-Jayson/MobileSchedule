package com.example.mobileschedule.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** The DAO only addresses the singleton row with id = 0. */
@Entity(
    tableName = "app_settings",
    foreignKeys = [ForeignKey(
        entity = SemesterEntity::class, parentColumns = ["id"], childColumns = ["activeSemesterId"],
        onDelete = ForeignKey.SET_NULL,
    )],
    indices = [Index("activeSemesterId")],
)
data class AppSettingsEntity(@PrimaryKey val id: Int = 0, val activeSemesterId: Long?)
