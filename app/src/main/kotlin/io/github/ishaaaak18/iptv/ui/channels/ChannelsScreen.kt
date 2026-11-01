package io.github.ishaaaak18.iptv.ui.channels

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import io.github.ishaaaak18.iptv.IptvApp
import io.github.ishaaaak18.iptv.R
import io.github.ishaaaak18.iptv.ads.BannerAd
import io.github.ishaaaak18.iptv.core.model.Channel
import io.github.ishaaaak18.iptv.player.PlayerActivity
import io.github.ishaaaak18.iptv.ui.theme.tvClickable

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChannelsScreen(playlistId: String, onBack: () -> Unit, vm: ChannelsViewModel = viewModel()) {
    LaunchedEffect(playlistId) { vm.open(playlistId) }
    val context = LocalContext.current
    val load by vm.load.collectAsState()
    val visible by vm.visible.collectAsState()
    val groups by vm.groups.collectAsState()
    val selected by vm.group.collectAsState()
    val query by vm.query.collectAsState()
    val favs by vm.favoriteUrls.collectAsState()
    val title = IptvApp.instance.repository.playlist(playlistId)?.name.orEmpty()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) }
                },
                actions = {
                    IconButton(onClick = vm::refresh) { Icon(Icons.Default.Refresh, stringResource(R.string.refresh)) }
                },
            )
        },
        bottomBar = { BannerAd() },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            OutlinedTextField(
                value = query,
                onValueChange = { vm.query.value = it },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                placeholder = { Text(stringResource(R.string.search)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            )
            val pseudo = listOf(
                GROUP_ALL to stringResource(R.string.all),
                GROUP_FAVORITES to stringResource(R.string.favorites),
                GROUP_RECENT to stringResource(R.string.recent),
            )
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(pseudo + groups.map { it to it }, key = { it.first }) { (key, label) ->
                    FilterChip(
                        selected = selected == key,
                        onClick = { vm.group.value = key },
                        label = { Text(label, maxLines = 1) },
                    )
                }
            }

            when (val l = load) {
                ChannelsViewModel.Load.Loading -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
                is ChannelsViewModel.Load.Error -> Column(
                    Modifier.fillMaxSize().padding(32.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(stringResource(R.string.error_loading, l.message))
                    Spacer(Modifier.size(12.dp))
                    Button(onClick = vm::refresh) { Text(stringResource(R.string.retry)) }
                }
                ChannelsViewModel.Load.Ready -> {
                    Text(
                        stringResource(R.string.channels_count, visible.size),
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                    )
                    if (visible.isEmpty()) {
                        Box(Modifier.fillMaxSize(), Alignment.Center) { Text(stringResource(R.string.no_channels)) }
                    } else {
                        LazyColumn(
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            itemsIndexed(visible, key = { i, c -> "$i|${c.url}" }) { index, channel ->
                                ChannelRow(
                                    channel = channel,
                                    favorite = channel.url in favs,
                                    onPlay = {
                                        context.startActivity(PlayerActivity.intent(context, vm.currentPlaylistId, visible, index))
                                    },
                                    onFavorite = { vm.toggleFavorite(channel) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChannelRow(channel: Channel, favorite: Boolean, onPlay: () -> Unit, onFavorite: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface)
            .tvClickable(onPlay)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(48.dp).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            if (channel.logo.isNotBlank()) {
                AsyncImage(
                    model = channel.logo,
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize().padding(4.dp),
                )
            } else {
                Text(channel.name.take(2), style = MaterialTheme.typography.titleMedium)
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(channel.name, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleSmall)
            if (channel.group.isNotBlank()) {
                Text(
                    channel.group, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                )
            }
        }
        IconButton(onClick = onFavorite) {
            Icon(
                if (favorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                stringResource(R.string.favorites),
                tint = if (favorite) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}
