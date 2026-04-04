package com.example.ownmyway

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
private fun LoginScreen() {

    val backgroundPurple = Color(0xFF9889B9)
    val darkInputBackground = Color(0xFF121841)
    val buttonPurple = Color(0xFF4300B1)
    val whiteText = Color.White

    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundPurple)
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(40.dp))

        Box(modifier = Modifier.fillMaxWidth()) {
            IconButton(onClick = { /* Ação de voltar */ }) {
                Icon(
                    painter = painterResource(id = android.R.drawable.ic_menu_revert),
                    contentDescription = "Voltar",
                    tint = Color.Black
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        Surface(
            modifier = Modifier
                .size(220.dp, 160.dp),
            shape = RoundedCornerShape(60.dp),
            color = Color(0xFFE6E0F3) // Roxo muito claro próximo ao branco
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                // Substitua pelo seu recurso de imagem real: R.drawable.omw_logo
                Text(
                    text = "OWN\nMY WAY",
                    style = TextStyle(
                        color = Color(0xFF3B2A82),
                        fontWeight = FontWeight.Bold,
                        fontSize = 28.sp
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(40.dp))


        Column(modifier = Modifier.fillMaxWidth()) {
            Text(text = "E-mail", color = whiteText, fontSize = 14.sp)
            Spacer(modifier = Modifier.height(8.dp))
            TextField(
                value = email,
                onValueChange = { email = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("exemplo123@gmail.com", color = Color.LightGray) },
                shape = RoundedCornerShape(12.dp),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = darkInputBackground,
                    unfocusedContainerColor = darkInputBackground,
                    focusedTextColor = whiteText,
                    unfocusedTextColor = whiteText,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent
                )
            )
        }

        Spacer(modifier = Modifier.height(20.dp))


        Column(modifier = Modifier.fillMaxWidth()) {
            Text(text = "Senha", color = whiteText, fontSize = 14.sp)
            Spacer(modifier = Modifier.height(8.dp))
            TextField(
                value = password,
                onValueChange = { password = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Digite pelo menos 8 caracteres", color = Color.LightGray) },
                visualTransformation = PasswordVisualTransformation(),
                shape = RoundedCornerShape(12.dp),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = darkInputBackground,
                    unfocusedContainerColor = darkInputBackground,
                    focusedTextColor = whiteText,
                    unfocusedTextColor = whiteText,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent
                )
            )
        }

        // Esqueci minha senha
        TextButton(
            onClick = { /* Ação */ },
            modifier = Modifier.align(Alignment.End)
        ) {
            Text(
                text = "ESQUECI MINHA SENHA",
                color = Color.White.copy(alpha = 0.8f),
                fontSize = 10.sp
            )
        }

        Spacer(modifier = Modifier.height(40.dp))

        // Botão Entrar
        Button(
            onClick = { /* Logica de login */ },
            modifier = Modifier
                .fillMaxWidth()
                .height(55.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = buttonPurple)
        ) {
            Text(text = "ENTRAR", color = whiteText, fontWeight = FontWeight.Bold)
        }
    }
}