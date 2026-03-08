package com.example.mementoandroid.ui.album.components

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun TimeGradientLegend(
    startDate: LocalDate?,
    endDate: LocalDate?,
    modifier: Modifier = Modifier
) {
    if (startDate == null || endDate == null) return

    val formatter = DateTimeFormatter.ofPattern("MMM yyyy")

    val gradientWidth = 160.dp

    Column(
        modifier = modifier
            .background(
                color = Color(0xCC000000),
                shape = RoundedCornerShape(10.dp)
            )
            .padding(10.dp)
    ) {

        Box(
            modifier = Modifier
                .height(12.dp)
                .width(gradientWidth)
                .background(
                    brush = Brush.horizontalGradient(
                        listOf(
                            Color.Red,
                            Color.Blue
                        )
                    ),
                    shape = RoundedCornerShape(6.dp)
                )
        )

        Spacer(modifier = Modifier.height(6.dp))

        Row(
            modifier = Modifier.width(gradientWidth),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = startDate.format(formatter),
                color = Color.White,
                fontSize = 12.sp
            )

            Text(
                text = endDate.format(formatter),
                color = Color.White,
                fontSize = 12.sp
            )
        }
    }
}