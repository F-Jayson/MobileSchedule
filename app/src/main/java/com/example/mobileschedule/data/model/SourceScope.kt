package com.example.mobileschedule.data.model

/** Stable source identity; sourceId is a channel, never an adapter version or batch ID. */
data class SourceScope(val schoolId: String, val sourceId: String, val sourceTermId: String)
