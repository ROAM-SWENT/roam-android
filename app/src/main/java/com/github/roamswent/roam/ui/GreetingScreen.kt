package com.github.se.bootcamp.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

object GreetingScreenTestTags {
  const val NAME_INPUT = "GreetingScreenNameInput"
  const val BUTTON = "GreetingScreenButton"
  const val GREETING_MESSAGE = "GreetingScreenGreetingMessage"
}
/**
 * GreetingScreen composable that displays a text field to input a name, a button to validate the
 * name and a GreetingScreen message.
 */
@Composable
fun GreetingScreen() {
  // A surface container using the 'background' color from the theme
  Scaffold(
      modifier = Modifier.fillMaxSize(),
      content = { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
          // State for user input text field, updated upon input change
          var inputText by remember { mutableStateOf("") }

          // State for displayed name after button click
          var name by remember { mutableStateOf("") }

          // Text field to input the name
          TextField(
              modifier = Modifier.padding(16.dp).testTag(GreetingScreenTestTags.NAME_INPUT),
              value = inputText,
              onValueChange = { inputText = it },
              label = { Text("Enter your name") })

          // Button to update the displayed name
          Button(
              modifier = Modifier.padding(16.dp).testTag(GreetingScreenTestTags.BUTTON),
              onClick = {
                // Update the name state to the input text when button is clicked
                name = inputText
              }) {
                Row {
                  Icon(
                      imageVector = Icons.Filled.CheckCircle,
                      contentDescription = null,
                      modifier = Modifier.padding(end = 4.dp))
                  Text("validate", modifier = Modifier.padding(end = 4.dp))
                }
              }

          // Display the GreetingScreen message
          Text(
              modifier = Modifier.padding(16.dp).testTag(GreetingScreenTestTags.GREETING_MESSAGE),
              text = if (name.isEmpty()) "What's your name ?" else "Hi $name")
        }
      })
}

/*
The main activity should look like this :
package com.github.se.bootcamp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.github.se.bootcamp.ui.GreetingScreen
import com.github.se.bootcamp.ui.theme.BootcampTheme

class MainActivity : ComponentActivity() {

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    setContent { BootcampTheme { GreetingScreen() } }
  }
}
 */
