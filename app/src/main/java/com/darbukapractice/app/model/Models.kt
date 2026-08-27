package com.darbukapractice.app.model

data class Exercise(
    val id: Int,
    val number: Int,
    val title: String,
    val bpmMin: Int,
    val bpmMax: Int,
    val asset: String
)
