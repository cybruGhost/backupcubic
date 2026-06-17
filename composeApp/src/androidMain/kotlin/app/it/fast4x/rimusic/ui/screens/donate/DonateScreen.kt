package app.it.fast4x.rimusic.ui.screens.donate

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.CreditCard
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.VolunteerActivism
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.it.fast4x.rimusic.ui.styling.LocalAppearance

@Composable
fun DonateScreen(onBackClick: () -> Unit) {
    val uriHandler = LocalUriHandler.current
    val appearance = LocalAppearance.current
    val colors = appearance.colorPalette
    val typography = appearance.typography

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background0)
            .padding(horizontal = 18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Spacer(Modifier.height(12.dp))
            IconButton(
                onClick = onBackClick,
                modifier = Modifier
                    .size(42.dp)
                    .background(colors.background2, RoundedCornerShape(8.dp))
            ) {
                Icon(Icons.Outlined.ArrowBack, contentDescription = "Back", tint = colors.text)
            }
        }

        item {
            Column(modifier = Modifier.padding(vertical = 8.dp)) {
                Text(
                    text = "Support Cubic Music",
                    style = typography.xxl.copy(fontWeight = FontWeight.Bold),
                    color = colors.text
                )
                Text(
                    text = "Your contribution keeps playback fixes, infrastructure, and new releases moving.",
                    style = typography.s,
                    color = colors.textSecondary,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }

        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.background2, RoundedCornerShape(8.dp))
                    .padding(18.dp)
            ) {
                Column {
                    Text(
                        text = "Maintainer Pass",
                        style = typography.l.copy(fontWeight = FontWeight.Bold),
                        color = colors.text
                    )
                    Text(
                        text = "$4  |  $5  |  $15  |  $30",
                        style = typography.m.copy(fontWeight = FontWeight.SemiBold),
                        color = colors.accent,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                    Text(
                        text = "This is a donation. No physical or digital product is delivered.",
                        style = typography.xs,
                        color = colors.textSecondary,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
        }

        item {
            DonationMethod(
                title = "Fourthwall",
                subtitle = "PayPal, card, and other available methods",
                detail = "cyberghost-shop.fourthwall.com",
                icon = { Icon(Icons.Outlined.CreditCard, null, tint = colors.accent) },
                onClick = { uriHandler.openUri("https://cyberghost-shop.fourthwall.com") }
            )
        }

        item {
            DonationMethod(
                title = "Ko-fi",
                subtitle = "PayPal support from $2",
                detail = "ko-fi.com/anonghost40418",
                icon = { Icon(Icons.Outlined.VolunteerActivism, null, tint = colors.accent) },
                onClick = { uriHandler.openUri("https://ko-fi.com/anonghost40418") }
            )
        }

        item {
            DonationMethod(
                title = "M-Pesa",
                subtitle = "Local support from KSh 30",
                detail = "Support Pal Global",
                icon = { Icon(Icons.Outlined.Payments, null, tint = colors.accent) },
                onClick = { uriHandler.openUri("https://support-pal-global.lovable.app/") }
            )
        }

        item {
            HorizontalDivider(color = colors.background3)
            Text(
                text = "Thank you for helping Cubic Music stay independent and actively maintained.",
                style = typography.s,
                color = colors.textSecondary,
                modifier = Modifier.padding(vertical = 20.dp)
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun DonationMethod(
    title: String,
    subtitle: String,
    detail: String,
    icon: @Composable () -> Unit,
    onClick: () -> Unit
) {
    val appearance = LocalAppearance.current
    val colors = appearance.colorPalette
    val typography = appearance.typography

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.background1, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .background(colors.background3, RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center
        ) {
            icon()
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 14.dp)
        ) {
            Text(
                text = title,
                style = typography.m.copy(fontWeight = FontWeight.SemiBold),
                color = colors.text
            )
            Text(text = subtitle, style = typography.xs, color = colors.textSecondary)
            Text(
                text = detail,
                style = typography.xxs,
                color = colors.accent,
                modifier = Modifier.padding(top = 3.dp)
            )
        }
        Icon(Icons.Outlined.OpenInNew, contentDescription = "Open", tint = colors.textSecondary)
    }
}
