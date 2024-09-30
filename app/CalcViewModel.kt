package com.interview.simplecalculator

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import java.util.Locale

class CalcViewModel : ViewModel() {

    private val _equationText = MutableLiveData("0")
    val equationText: LiveData<String> = _equationText

    private val _prevCalcText = MutableLiveData("")
    val prevCalcText: LiveData<String> = _prevCalcText

    private var resultDisplayed = false  // Track if the last action was a result display
    private var conversionDisplayed = false
    private var infinityFlag = false
    private var networkFlag = false

    private val _showCurrencyDialog = MutableLiveData(false)
    val showCurrencyDialog: LiveData<Boolean> get() = _showCurrencyDialog

    private var previousCurrency: String = "EUR"    // Default to Euro

    fun onButtonClick(btn : String) {
        _equationText.value?.let {
            val lastChar = it.lastOrNull()

            if(btn == "AC"){
                resetToDefault()
                return
            }

            if(conversionDisplayed || infinityFlag || networkFlag) {
                resetToDefault()

                // Immediately handle the current button press (after resetting)
                if (!isOperator(btn) && !isMiscBtn(btn)) {
                    _equationText.value = btn // Display the button pressed
                }

                return
            }

            if(btn == "⌫"){
                if(it == "0" || resultDisplayed){
                    return
                }
                if(it.isNotEmpty()) {
                    _equationText.value = it.substring(0, it.length - 1)
                    if(_equationText.value == "") _equationText.value = "0"
                }
                return
            }

            if(btn == "="){
                if (isOperator(lastChar.toString())){
                    _equationText.value = it.substring(0, it.length - 1)
                }

                _prevCalcText.value = _equationText.value
                var result = evaluateEquation(_equationText.value!!).toString()

                // Format result based on its value
                result = if (result.length > 9) {
                    // Format using scientific notation
                    String.format(Locale.US, "%.3e", result.toDouble()).trimEnd('0').trimEnd('.')
                } else {
                    // Format as a normal number with up to 5 decimal places
                    String.format(Locale.US, "%.5f", result.toDouble()).trimEnd('0').trimEnd('.')
                }

                //Remove unnecessary .0 from the end of the result
                if(result.endsWith(".0")){
                    result = result.replace(".0", "")
                }
                _equationText.value = result
                resultDisplayed = true  // Mark that a result was displayed
                return
            }

            if(btn == "$"){
                toggleCurrencyPopup(true)
                return
            }

            if(btn == "☺"){
                return
            }

            // Reset the equation if the last action was a result display and the button is not an operator
            if (resultDisplayed && !isOperator(btn)) {
                _equationText.value = btn
                _prevCalcText.value = ""
                resultDisplayed = false
                return
            }

            // Allow a negative sign at the start or after an operator, but prevent multiple consecutive '-'
            if (btn == "-") {
                // Allow a minus at the start of the equation
                if (it.isEmpty() || it == "0") {
                    _equationText.value = btn
                    return
                }

                // Check if the last character is an operator and prevent multiple '-'
                if (lastChar != null && isOperator(lastChar.toString()) && lastChar != '-') {
                    _equationText.value = it + btn
                    return
                }
            }

            // Prevent multiple consecutive operators
            if (isOperator(btn) && (lastChar == null || isOperator(lastChar.toString()))) {
                return
            }

            // Prevent starting with an operator
            if ((it == "0" ||it.isEmpty()) && (btn == "×" || btn == "÷" || btn == "+")) {
                return
            }

            if(it == "0"){
                _equationText.value = btn
            }else{
                _equationText.value = it + btn
                resultDisplayed = false
            }
        }
    }

    private fun isOperator(char: String): Boolean {
        return char in listOf("+", "-", "×", "÷")
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
            when {
                // Handle digits and decimal points
                char in '0'..'9' || char == '.' -> {
                    currentNumber += char
                    lastTokenWasOperator = false  // We are now building a number
                }

                // Handle scientific notation (e.g., 1.23e+10)
                char == 'e' || char == 'E' -> {
                    if (currentNumber.isNotEmpty()) {
                        currentNumber += char
                        expectExponent = true  // Expect an exponent part after 'e'
                    } else {
                        throw IllegalArgumentException("Invalid number format with 'e'")
                    }
                }

                // Handle sign for exponent part (e.g., 1.23e-10)
                char == '+' || char == '-' -> {
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
                char in listOf('+', '-', '*', '/') -> {
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

    fun toggleCurrencyPopup(toggle: Boolean) {
        _showCurrencyDialog.value =toggle  // Show the currency selection dialog
    }

    fun convertCurrency(amount: Double, targetCurrency: String) {
        viewModelScope.launch {
            try {
                // Save the current equation in _prevCalcText before conversion
                _prevCalcText.value = "$amount $previousCurrency"

                // Fetch conversion rates from Fixer.io API
                val response = FixerAPI.api.getExchangeRates(baseCurrency = previousCurrency, targetCurrencies = targetCurrency)

                if (response.success) {
                    val rate = response.rates[targetCurrency]
                    if (rate != null) {
                        val convertedAmount = amount * rate
                        // Update equationText with converted amount and target currency symbol
                        _equationText.value = String.format(Locale.US, "%.2e %s", convertedAmount, targetCurrency)
                        conversionDisplayed = true
                    }
                    previousCurrency = targetCurrency
                }
                else{
                    _equationText.value = "Only EUR"
                }
            } catch (e: Exception) {
                networkFlag = true
                _equationText.value = "No network"
                _prevCalcText.value = ""
            }
        }
    }

    fun resetToDefault() {
        _equationText.value = "0"
        _prevCalcText.value = ""
        previousCurrency = "EUR"
        conversionDisplayed = false
        infinityFlag = false
        networkFlag = false
    }
}
