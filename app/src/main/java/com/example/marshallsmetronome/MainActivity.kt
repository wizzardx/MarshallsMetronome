package com.example.marshallsmetronome

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.marshallsmetronome.ui.theme.MarshallsMetronomeTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MarshallsMetronomeTheme {
                // A surface container using the 'background' color from the theme
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MarshallsMetronome()
                }
            }
        }
    }
}

@Composable
fun MarshallsMetronome(modifier: Modifier = Modifier) {

    // Column of controls, centered:

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .padding(30.dp)
    ) {
        // Total time remaining
        Text(
            text = "04:00",
            fontSize = 50.sp,
            modifier = modifier
        )

        // Seconds remaining in current round
        Text(
            text = "12",
            fontSize = 90.sp,
            modifier = modifier
        )

        // Current round number
        Text(
            text = "1/2",
            fontSize = 90.sp,
            modifier = modifier
        )

        // Start/Pause and Reset button, next to each other
        Row {
            Button(
                onClick = {}
            ) {
                Text(
                    text = "Start",
                    modifier = modifier
                )
            }

            Spacer(modifier = modifier.width(10.dp))

            Button(
                onClick = {}
            ) {
                Text(
                    text = "Reset",
                    modifier = modifier
                )
            }
        }

        // Configure rounds (eg, 8):
        TextField(
            value = "8",
            onValueChange = {},
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
            label = { Text(text = "Rounds") },
            modifier = modifier.padding(top = 20.dp),
        )

        // Configure Work (seconds, eg 20):
        TextField(
            value = "20",
            onValueChange = {},
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
            label = { Text(text = "Work") },
            modifier = modifier.padding(top = 20.dp),
        )

        // Configure Rest (seconds, eg 10):
        TextField(
            value = "10",
            onValueChange = {},
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
            label = { Text(text = "Rest") },
            modifier = modifier.padding(top = 20.dp),
        )

    }

}

@Preview(showSystemUi = true)
@Composable
fun MarshallsMetronomePreview() {
    MarshallsMetronomeTheme {
        MarshallsMetronome()
    }
}