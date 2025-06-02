package com.example.dungappedit.extention

import android.content.Context
import android.widget.Toast

fun Context?.toast(mesId: Int, duration: Int = Toast.LENGTH_SHORT) {
    this?.let {
        Toast.makeText(this, getString(mesId), duration).show()
    }
}
