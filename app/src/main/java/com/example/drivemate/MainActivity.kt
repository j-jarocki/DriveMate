package com.example.drivemate

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MainScreen(
                onStartDrive = { startActivity(Intent(this, DriveActivity::class.java)) },
                onGarage = { startActivity(Intent(this, GarageActivity::class.java)) },
                onClickerGame = { startActivity(Intent(this, ClickerActivity::class.java)) },
                onSettings = { startActivity(Intent(this, SettingsActivity::class.java)) }
            )
        }
    }
}

@Composable
fun MainScreen(
    onStartDrive: () -> Unit,
    onGarage: () -> Unit,
    onClickerGame: () -> Unit,
    onSettings: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Button(onClick = onStartDrive, modifier = Modifier.fillMaxWidth().padding(8.dp)) {
            Text(stringResource(R.string.start_drive))
        }
        Button(onClick = onGarage, modifier = Modifier.fillMaxWidth().padding(8.dp)) {
            Text(stringResource(R.string.garage))
        }
        Button(onClick = onClickerGame, modifier = Modifier.fillMaxWidth().padding(8.dp)) {
            Text(stringResource(R.string.clicker_game))
        }
        Button(onClick = onSettings, modifier = Modifier.fillMaxWidth().padding(8.dp)) {
            Text(stringResource(R.string.settings))
        }
    }
}