package com.darbukapractice.app.data

import com.darbukapractice.app.model.Exercise

/**
 * Fixed exercise index. The notation artwork is stored locally per exercise and
 * cropped from the supplied book pages so the app never needs the PDF at runtime.
 * Titles are intentionally conservative where the scan does not provide a reliable
 * machine-readable transcription; the notation image remains the source of truth.
 */
object Exercises {
    private val bpm = listOf(
        60 to 120, 60 to 120, 60 to 120, 60 to 120, 60 to 90, 60 to 100,
        60 to 120, 60 to 100, 60 to 120, 60 to 120, 60 to 120, 60 to 120,
        60 to 150, 60 to 125, 60 to 170, 60 to 100, 60 to 130, 60 to 120,
        60 to 130, 60 to 150, 60 to 180, 60 to 130, 60 to 150, 60 to 100,
        60 to 90, 60 to 100, 60 to 60, 60 to 120, 60 to 120, 60 to 90,
        60 to 140, 60 to 140, 60 to 140
    )

    private val titles = mapOf(
        1 to "اجرای ضربات با کشش نت سیاه",
        2 to "اجرای ضربات با کشش نت چنگ",
        4 to "آشنایی با نت دولاچنگ و نحوه اجرای آن"
    )

    val all: List<Exercise> = (1..33).map { n ->
        val range = bpm[n - 1]
        Exercise(n, n, titles[n] ?: "تمرین $n", range.first, range.second, "exercise_%02d".format(n))
    }
}
