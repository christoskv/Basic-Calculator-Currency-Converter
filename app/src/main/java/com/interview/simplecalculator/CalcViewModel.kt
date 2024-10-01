package com.interview.simplecalculator

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.launch
import java.util.Locale

class CalcViewModel(application: Application) : AndroidViewModel(application) {

    private val _equationText = MutableLiveData("0")
    val equationText: LiveData<String> = _equationText

    private val _prevCalcText = MutableLiveData("")
    val prevCalcText: LiveData<String> = _prevCalcText

    private var resultDisplayed = false  // Track if the last action was a result display
    private var conversionDisplayed = false // Track if the last action was a conversion display
    private var startedConverting = false
    private var infinityFlag = false
    private var networkFlag = false
    private var validAmount = true
    private var initialAmount = 0.0 // Amount to be converted

    private val _showCurrencyDialog = MutableLiveData(false)
    val showCurrencyDialog: LiveData<Boolean> get() = _showCurrencyDialog

    private var previousCurrency: String = "EUR"    // Default to Euro

    //Used for Exchange Rates persistence between app sessions
    private var cachedRates: CachedExchangeRates? = null
    private val preferences: SharedPreferences = application.getSharedPreferences("currency_cache", Context.MODE_PRIVATE)
    private val gson = Gson()

    init {
        // Load cached exchange rates, that were stored locally
        loadCachedRates()
    }

    fun onButtonClick(btn: String) {
        _equationText.value?.let { equation ->
            val lastChar = equation.lastOrNull()

            when {
                btn == "AC" -> resetToDefault()
                btn == "$" -> toggleCurrencyPopup(true)
                //Reset when equation/conversion can't continue
                conversionDisplayed || infinityFlag || networkFlag || !validAmount -> {
                    resetToDefault()
                    //Handle the current button press
                    if (!isOperator(btn) && !isMiscBtn(btn)) {
                        _equationText.value = btn
                    }
                }
                btn == "☺" -> return
                btn == "⌫" -> handleBackspace(equation)
                btn == "=" -> handleEquals(equation)
                btn == "-" -> handleMinus(equation, lastChar)
                // Reset the equation if the last action was a result display and the button is not an operator
                resultDisplayed && !isOperator(btn) -> {
                    _equationText.value = btn
                    _prevCalcText.value = ""
                    resultDisplayed = false
                }
                // Prevent multiple consecutive operators
                isOperator(btn) && (lastChar == null || isOperator(lastChar.toString())) -> return
                // Prevent starting with an operator
                (equation == "0" || equation.isEmpty()) && (btn == "×" || btn == "÷" || btn == "+") -> return
                //Remove default 0 value
                equation == "0" -> _equationText.value = btn
                else -> {
                    _equationText.value = equation + btn
                    resultDisplayed = false
                }
            }
        }
    }

    private fun handleBackspace(equation: String) {
        if (equation == "0" || resultDisplayed || conversionDisplayed) return
        _equationText.value = if (equation.isNotEmpty()) {
            equation.substring(0, equation.length - 1).ifEmpty { "0" }
        } else {
            "0"
        }
    }

    private fun handleEquals(equation: String) {
        if (isOperator(equation.lastOrNull().toString())) {
            _equationText.value = equation.dropLast(1)
        }
        _prevCalcText.value = _equationText.value

        //Format result based on its value
        val result = evaluateEquation(_equationText.value!!).toString().let {
            if (it.length > 9) { //Scientific format for large numbers
                String.format(Locale.US, "%.3e", it.toDouble()).trimEnd('0').trimEnd('.')
            } else { //else normal
                String.format(Locale.US, "%.5f", it.toDouble()).trimEnd('0').trimEnd('.')
            }.replace(".0", "") //Remove unnecessary .0 from end
        }

        _equationText.value = result
        resultDisplayed = true
    }

    private fun handleMinus(equation: String, lastChar: Char?) {
        // Allow a minus at the start of the equation
        if (equation.isEmpty() || equation == "0") {
            _equationText.value = "-"
        }
        // Check if the last character is an operator and prevent multiple '-'
        else if ((lastChar != null && isOperator(lastChar.toString()) && lastChar != '-')
            || !isOperator(lastChar.toString())){
            _equationText.value = "$equation-"
        }
        resultDisplayed = false
    }

    private fun isOperator(char: String): Boolean {
        return char in listOf("+", "-", "×", "÷", "=")
    }

    private fun isMiscBtn(char: String): Boolean {
        return char in listOf("☺", "⌫", "$")
    }

    private fun evaluateEquation(equation: String): Double {
        // Replace special characters × and ÷ with * and / for multiplication and division
        val normalizedEquation = equation.replace('×', '*').replace('÷', '/')

        // Tokenize the input (split into numbers and operators)
        val tokens = tokenize(normalizedEquation)

        // Convert the token list to postfix notation (to handle operator precedence)
        val postfixTokens = convertToPostfix(tokens)

        // Evaluate the postfix expression to get the final result
        return evaluatePostfix(postfixTokens)
    }

    private fun tokenize(equation: String): List<String> {
        val tokens = mutableListOf<String>()  // List to store tokens (numbers and operators)
        var currentNumber = ""  // To accumulate digits of a number
        var lastTokenWasOperator = true  // Keep track of whether the last token was an operator
        var expectExponent = false  // To track if we're inside a number with scientific notation

        for (char in equation) {
            when (char) {
                // Handle digits and decimal points
                in '0'..'9', '.' -> {
                    currentNumber += char
                    lastTokenWasOperator = false  // We are now building a number
                }

                // Handle scientific notation (e.g., 1.23e+10)
                'e', 'E' -> {
                    if (currentNumber.isNotEmpty()) {
                        currentNumber += char
                        expectExponent = true  // Expect an exponent part after 'e'
                    } else {
                        throw IllegalArgumentException("Invalid number format with 'e'")
                    }
                }

                // Handle sign for exponent part (e.g., 1.23e-10)
                '+', '-' -> {
                    if (expectExponent) {
                        currentNumber += char
                        expectExponent = false  // We have started building the exponent part
                    } else {
                        // Handle this as a regular operator if it's not part of an exponent
                        if (currentNumber.isNotEmpty()) {
                            tokens.add(currentNumber)
                            currentNumber = ""
                        }
                        if (char == '-' && lastTokenWasOperator) {
                            currentNumber += char  // Negative number at the start
                        } else {
                            tokens.add(char.toString())  // Add operator
                            lastTokenWasOperator = true
                        }
                    }
                }

                // Handle operators (not part of scientific notation)
                in listOf('+', '-', '*', '/') -> {
                    if (currentNumber.isNotEmpty()) {
                        tokens.add(currentNumber)  // Add the number token
                        currentNumber = ""
                        expectExponent = false  // Reset exponent tracking
                    }
                    tokens.add(char.toString())  // Add the operator token
                    lastTokenWasOperator = true
                }
                else -> throw IllegalArgumentException("Invalid character in equation: $char")
            }
        }

        // Add the last built number if there's any remaining
        if (currentNumber.isNotEmpty()) {
            tokens.add(currentNumber)
        }

        return tokens
    }

    private fun convertToPostfix(tokens: List<String>): List<String> {
        // Operator precedence map. Higher values -> higher precedence.
        val precedence = mapOf("+" to 1, "-" to 1, "*" to 2, "/" to 2)
        val output = mutableListOf<String>()
        val operators = mutableListOf<String>()

        // Loop through each token (number or operator)
        for (token in tokens) {
            when {
                // If it's a number, directly add it to the output
                token.isNumber() -> output.add(token)

                // If it's an operator, handle it based on precedence
                token in precedence -> {
                    // Pop operators with higher or equal precedence and add to the output
                    while (operators.isNotEmpty() && (precedence[operators.last()]
                            ?: 0) >= (precedence[token] ?: 0)
                    ) {
                        output.add(operators.removeAt(operators.size - 1))
                    }
                    // Push the current operator onto the operator stack
                    operators.add(token)
                }
            }
        }

        // Add any remaining operators in the stack to the output
        while (operators.isNotEmpty()) {
            output.add(operators.removeAt(operators.size - 1))
        }

        return output
    }

    private fun evaluatePostfix(tokens: List<String>): Double {
        val stack = mutableListOf<Double>()  // Stack to hold numbers during evaluation

        // Loop through each token in the postfix expression
        for (token in tokens) {
            when {
                // If it's a number, push it to the stack
                token.isNumber() -> stack.add(token.toDouble())

                // If it's an operator, pop two numbers from the stack and apply the operator
                token == "+" -> {
                    val b = stack.removeAt(stack.size - 1)
                    val a = stack.removeAt(stack.size - 1)
                    stack.add(a + b)
                }
                token == "-" -> {
                    val b = stack.removeAt(stack.size - 1)
                    val a = stack.removeAt(stack.size - 1)
                    stack.add(a - b)
                }
                token == "*" -> {
                    val b = stack.removeAt(stack.size - 1)
                    val a = stack.removeAt(stack.size - 1)
                    stack.add(a * b)
                }
                token == "/" -> {
                    val b = stack.removeAt(stack.size - 1)
                    val a = stack.removeAt(stack.size - 1)
                    stack.add(a / b)
                }
            }
        }

        if (stack[0] == Double.POSITIVE_INFINITY || stack[0] == Double.NEGATIVE_INFINITY) infinityFlag = true

        return stack[0]
    }

    // Extension function to check if a string can be converted to a number
    private fun String.isNumber(): Boolean {
        return this.toDoubleOrNull() != null
    }

    private fun updateConversion(amount: Double, rate: Double, targetCurrency: String) {
        val convertedAmount = amount * rate
        // Update equationText with converted amount and target currency symbol
        _equationText.value = String.format(Locale.US, "%.2f %s", convertedAmount, targetCurrency)
        conversionDisplayed = true
        previousCurrency = "EUR"
    }

    // Function to load cached rates from SharedPreferences
    private fun loadCachedRates() {
        val ratesJson = preferences.getString("cached_rates", null)
        val timestamp = preferences.getLong("timestamp", 0L)

        if (ratesJson != null && timestamp != 0L) {
            val type = object : TypeToken<Map<String, Double>>() {}.type
            val rates: Map<String, Double> = gson.fromJson(ratesJson, type)
            cachedRates = CachedExchangeRates(rates, timestamp)
        }
    }

    // Function to save cached rates to SharedPreferences
    private fun saveCachedRates(rates: Map<String, Double>, timestamp: Long) {
        val editor = preferences.edit()
        val ratesJson = gson.toJson(rates)
        editor.putString("cached_rates", ratesJson)
        editor.putLong("timestamp", timestamp)
        editor.apply()
        loadCachedRates()
    }

    private fun resetToDefault() {
        _equationText.value = "0"
        _prevCalcText.value = ""
        previousCurrency = "EUR"
        conversionDisplayed = false
        infinityFlag = false
        networkFlag = false
        startedConverting = false
        validAmount = true
    }

    // Helper function to extract the amount to be converted
    fun getCurrentAmount(): Double {
        // Extract the first number before any operator or space
        val amount = equationText.value?.split(" ")?.get(0)
            ?.toDoubleOrNull()?: 0.0
        return amount
    }

    fun toggleCurrencyPopup(isPopupVisible: Boolean) {
        _showCurrencyDialog.value = isPopupVisible  // Show the currency selection dialog
    }

    fun convertCurrency(amount: Double, targetCurrency: String) {
        if (amount == 0.0) {
            _equationText.value = "Not valid amount"
            validAmount = false
            return
        }

        viewModelScope.launch {
            try {
                val currentTime = System.currentTimeMillis()
                val eightHours = 8 * 60 * 60 * 1000 // 8 hours in milliseconds

                if (!startedConverting) {
                    initialAmount = amount
                    // Save the current equation in _prevCalcText before conversion
                    _prevCalcText.value = "$amount $previousCurrency"
                    startedConverting = true
                }


                // Check if cachedRates is still valid (every 8 hours)
                if (cachedRates != null && (currentTime - cachedRates!!.timestamp) < eightHours) {
                    // Use cached rates
                    val rate = cachedRates!!.rates[targetCurrency]
                    if (rate != null) {
                        updateConversion(initialAmount, rate, targetCurrency)
                    } else {
                        _equationText.value = "Invalid currency!"
                        networkFlag = true
                    }
                } else {
                    // Make API call to fetch new rates
                    val response = FixerAPI.api.getExchangeRates(baseCurrency = previousCurrency)

                    if (response.success) {
                        val rate = response.rates[targetCurrency]
                        if (rate != null) {
                            // Cache the fetched rates and timestamp
                            saveCachedRates(response.rates, currentTime)

                            // Update the conversion
                            updateConversion(initialAmount, rate, targetCurrency)
                        } else {
                            _equationText.value = "Invalid currency."
                            networkFlag = true
                        }
                    } else {
                        _equationText.value = "API failed"
                        networkFlag = true
                    }
                }
            } catch (e: Exception) {
                networkFlag = true
                _equationText.value = "No network"
                _prevCalcText.value = ""
            }
        }
    }
}