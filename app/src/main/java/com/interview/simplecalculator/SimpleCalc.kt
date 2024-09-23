package com.interview.simplecalculator

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import com.google.accompanist.systemuicontroller.rememberSystemUiController

val calcButtons = listOf(
    "AC", "⌫", "$", "÷",
    "7", "8", "9", "×",
    "4", "5", "6", "-",
    "1", "2", "3", "+",
    "☺", "0", ".", "="
)

val currencies = listOf(
    "Pound Sterling (GBP)" to "GBP",
    "US Dollar (USD)" to "USD",
    "Japanese Yen (JPY)" to "JPY",
    "Turkish Lira (TRY)" to "TRY",
    "Swiss Franc (CHF)" to "CHF"
)

@Composable
fun SimpleCalc(modifier: Modifier = Modifier, viewModel: CalcViewModel) {

    //Ensure that navigation and status bar are the same color as the background
    val systemUiController = rememberSystemUiController()

    systemUiController.setSystemBarsColor(
        color = Color.Black,
        darkIcons = false // Ensures light icons for black background
    )

    // Observe the dialog state
    val showCurrencyDialog by viewModel.showCurrencyDialog.observeAsState(false)

    val equationText = viewModel.equationText.observeAsState()
    val prevCalcText = viewModel.prevCalcText.observeAsState()

    Box(modifier = modifier) {
        Column(
            modifier = modifier.fillMaxSize(),
            horizontalAlignment = Alignment.End
        ) {
            Spacer(modifier = Modifier.weight(1f))

            Text(
                text = prevCalcText.value?:"",
                Modifier.padding(
                    end = 20.dp
                ),
                style = TextStyle(
                    color = Color(0xFF505050),
                    fontSize = 30.sp,
                    textAlign = TextAlign.End
                ),
                maxLines = 5,
                overflow = TextOverflow.Ellipsis
            )

            Text(
                text = equationText.value?:"0",
                Modifier.padding(
                    end = 20.dp
                ),
                style = TextStyle(
                    color = Color.White,
                    fontSize = 80.sp,
                    textAlign = TextAlign.End,
                    fontWeight = FontWeight.Bold
                ),
                maxLines = 2,
            )

            Spacer(modifier = Modifier.height(10.dp))

            LazyVerticalGrid(
                columns = GridCells.Fixed(4),
            ) {
                items(calcButtons) {
                    CalcButton(btn = it, onClick = {
                        viewModel.onButtonClick(it)
                    })
                }
            }
        }
    }

    CurrencyConverterPopup(viewModel, showCurrencyDialog)
}

@Composable
fun CalcButton(btn : String, onClick: ()-> Unit){
    Box(modifier = Modifier.padding(5.dp)) {
        FloatingActionButton(
            onClick = onClick,
            modifier = Modifier.size(85.dp),
            shape = CircleShape,
            contentColor = Color.White,
            containerColor = getColor(btn)
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center // Center the text inside the FAB
            ) {
                if (btn == "⌫") {
                    Text(
                        text = btn,
                        fontSize = 35.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        fontFamily = FontFamily.Monospace,  // Using a monospace font for alignment
                        modifier = Modifier.offset(x = (-3).dp) // Slightly adjust offset for centering
                    )
                } else {
                    Text(
                        text = btn,
                        fontSize = 35.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        fontFamily = FontFamily.Monospace // Using the same monospace font for consistency
                    )
                }
            }
        }
    }
}

fun getColor(btn : String) : Color{
    if(btn == "AC" || btn == "⌫" || btn == "+/-" || btn == "$")
        return Color(0xFF505050)
    if(btn == "÷" || btn == "×" || btn == "-" || btn == "+" || btn == "=")
        return Color(0xFFFF9500)
    return Color(0xFF1C1C1C)
}

@Composable
fun CurrencyConverterPopup(viewModel: CalcViewModel, toggle: Boolean) {

    if (toggle) {
        Popup(
            alignment = Alignment.Center,
            onDismissRequest = { viewModel.toggleCurrencyPopup(false) }
        ) {
            Box(
                modifier = Modifier
                    .size(350.dp)
                    .background(Color(0xFF2C2C2C), shape = RoundedCornerShape(10.dp))
                    .border(2.dp, Color.White, RoundedCornerShape(10.dp))
                    .padding(16.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xFF2C2C2C))
                        .padding(8.dp)
                ) {
                    Text(
                        text = "Convert Amount (EUR) to which currency?",
                        style = TextStyle(
                            color = Color.White,
                            fontSize = 20.sp,
                            textAlign = TextAlign.Center,
                            fontWeight = FontWeight.Bold
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    currencies.forEach { (currency, symbol) ->
                        Text(
                            text = currency,
                            style = TextStyle(
                                color = Color.White,
                                fontSize = 20.sp,
                                textAlign = TextAlign.Left,
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    if (currency == "Reset") {
                                        viewModel.resetToDefault()
                                    } else {
                                        val amount = viewModel.equationText.value?.split(" ")?.get(0)
                                            ?.toDoubleOrNull()?: 0.0
                                        viewModel.convertCurrency(amount, symbol)
                                    }
                                    viewModel.toggleCurrencyPopup(false)
                                }
                                .padding(12.dp)
                        )
                    }
                }
            }
        }
    }
}

