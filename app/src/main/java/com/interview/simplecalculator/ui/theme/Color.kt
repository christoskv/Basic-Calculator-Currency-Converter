package com.interview.simplecalculator.ui.theme

import androidx.compose.ui.graphics.Color

val Purple80 = Color(0xFFD0BCFF)
val PurpleGrey80 = Color(0xFFCCC2DC)
val Pink80 = Color(0xFFEFB8C8)

val Purple40 = Color(0xFF6650a4)
val PurpleGrey40 = Color(0xFF625b71)
val Pink40 = Color(0xFF7D5260)

fun getColor(btn : String) : Color{
    if(btn == "AC" || btn == "⌫" || btn == "+/-" || btn == "$")
        return Color(0xFF505050)
    if(btn == "÷" || btn == "×" || btn == "-" || btn == "+" || btn == "=")
        return Color(0xFFFF9500)
    return Color(0xFF1C1C1C)
}