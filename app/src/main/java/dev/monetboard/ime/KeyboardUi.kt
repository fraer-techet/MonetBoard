package dev.monetboard.ime

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.monetboard.ui.CookieButton
import dev.monetboard.ui.KbIcons
import dev.monetboard.ui.MonetTheme
import dev.monetboard.ui.PillButton
import kotlin.math.abs
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun KeyboardRoot(c: KeyboardController) {
    val view = LocalView.current
    SideEffect { c.hostView = view }
    MonetTheme(c.prefs) {
        val p = c.prefs
        val keysH = (p.keyHeight * 4 + p.keyGap * 3).dp
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainer,
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                Modifier
                    .windowInsetsPadding(WindowInsets.navigationBars.only(WindowInsetsSides.Bottom))
                    .padding(start = 6.dp, end = 6.dp, top = 8.dp, bottom = 6.dp),
            ) {
                if (c.ai.open) AiBar(c) else TopBar(c)
                Spacer(Modifier.height(6.dp))
                when {
                    c.ai.open && c.ai.showCard -> ResultCard(c, Modifier.fillMaxWidth().height(keysH))
                    c.state.page == Page.EMOJI -> EmojiPanel(c, keysH)
                    else -> KeyRows(c)
                }
            }
        }
    }
}

// ------------------------------------------------------------------ top bars

@Composable
private fun TopBar(c: KeyboardController) {
    val st = c.state
    val cs = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().height(44.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        CookieButton(KbIcons.Spark, cs.primary, cs.onPrimary) { c.toggleAi() }
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            if (st.altKeys.isNotEmpty()) {
                st.altKeys.forEach { a ->
                    PillButton(a, cs.secondaryContainer, cs.onSecondaryContainer, Modifier.weight(1f)) { c.onAltPick(a) }
                }
            } else {
                val s = st.suggestions
                val order = listOf(s.getOrNull(1), s.getOrNull(0), s.getOrNull(2))
                order.forEachIndexed { i, w ->
                    if (w == null) Spacer(Modifier.weight(1f)) else {
                        val hi = i == 1
                        PillButton(
                            w,
                            if (hi) cs.primary else cs.secondaryContainer,
                            if (hi) cs.onPrimary else cs.onSecondaryContainer,
                            Modifier.weight(1f),
                        ) { c.feedback(); c.onSuggestion(w) }
                    }
                }
            }
        }
        CookieButton(Icons.Default.Settings, cs.primary, cs.onPrimary) { c.openSettings() }
    }
}

@Composable
private fun AiBar(c: KeyboardController) {
    val ai = c.ai
    val cs = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            Modifier.fillMaxWidth().height(44.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            CookieButton(Icons.Default.Close, cs.primary, cs.onPrimary) { ai.close() }
            LazyRow(
                Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                items(AiAction.entries) { a ->
                    PillButton(a.label, cs.secondaryContainer, cs.onSecondaryContainer) { ai.runAction(a) }
                }
                item { PillButton("Сброс чата", cs.surfaceContainerHigh, cs.onSurfaceVariant) { ai.resetChat() } }
            }
        }
        Row(
            Modifier.fillMaxWidth().height(46.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            PillButton(
                "Поле",
                if (ai.useContext) cs.primaryContainer else cs.surfaceContainerHigh,
                if (ai.useContext) cs.onPrimaryContainer else cs.onSurfaceVariant,
            ) { ai.useContext = !ai.useContext }
            Box(
                Modifier.weight(1f).fillMaxHeight()
                    .border(1.5.dp, cs.outline, RoundedCornerShape(50))
                    .padding(horizontal = 16.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                if (ai.prompt.isEmpty()) {
                    Text("Спроси или попроси…", color = cs.onSurfaceVariant, fontSize = 15.sp, maxLines = 1)
                } else {
                    Text(ai.prompt.takeLast(90), color = cs.onSurface, fontSize = 15.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
            CookieButton(Icons.AutoMirrored.Filled.Send, cs.primary, cs.onPrimary, size = 44.dp) { ai.send() }
        }
    }
}

@Composable
private fun ResultCard(c: KeyboardController, modifier: Modifier) {
    val ai = c.ai
    val cs = MaterialTheme.colorScheme
    val res = ai.result
    val err = ai.error
    Column(modifier.clip(RoundedCornerShape(24.dp)).background(cs.surfaceContainerHigh).padding(14.dp)) {
        Box(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())) {
            when {
                ai.busy -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    CircularProgressIndicator(Modifier.size(20.dp), color = cs.primary, strokeWidth = 2.5.dp)
                    Text("Думаю…", color = cs.onSurfaceVariant, fontSize = 15.sp)
                }
                err != null -> Text(err, color = cs.error, fontSize = 15.sp)
                res != null -> Text(res, color = cs.onSurface, fontSize = 16.sp)
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (res != null) {
                PillButton(ai.applyLabel, cs.primary, cs.onPrimary) { ai.apply() }
                PillButton("Ещё раз", cs.secondaryContainer, cs.onSecondaryContainer) { ai.retry() }
            }
            if (err != null) {
                PillButton("Настройки", cs.primary, cs.onPrimary) { c.openSettings() }
                PillButton("Ещё раз", cs.secondaryContainer, cs.onSecondaryContainer) { ai.retry() }
            }
            Spacer(Modifier.weight(1f))
            PillButton("Назад", cs.surfaceContainerHighest, cs.onSurface) { ai.dismissCard() }
        }
    }
}

// ------------------------------------------------------------------ keys

@Composable
private fun KeyRows(c: KeyboardController) {
    val st = c.state
    val p = c.prefs
    val numRow = p.numberRow
    val rows = remember(st.page, st.langIndex, numRow) { Layouts.rows(st.page, st.lang, numRow) }
    val gap = p.keyGap.dp
    Column(verticalArrangement = Arrangement.spacedBy(gap)) {
        rows.forEach { row ->
            Row(
                Modifier.fillMaxWidth().height(p.keyHeight.dp),
                horizontalArrangement = Arrangement.spacedBy(gap),
            ) {
                row.forEach { k -> KeyView(k, c, Modifier.weight(k.weight).fillMaxHeight()) }
            }
        }
    }
}

@Composable
private fun KeyView(k: KeyDef, c: KeyboardController, modifier: Modifier) {
    val cs = MaterialTheme.colorScheme
    val st = c.state
    val radius = c.prefs.keyRadius.dp
    when (k.type) {
        KType.SPACER -> Spacer(modifier)

        KType.CHAR -> {
            val shifted = st.page == Page.LETTERS && st.shift != Shift.OFF
            val label = if (shifted) k.label.uppercase() else k.label
            val downShift = remember { BooleanArray(1) }
            KeyBase(
                modifier, cs.secondaryContainer, cs.onSecondaryContainer, radius, c,
                onPress = { downShift[0] = shifted; c.onChar(label) },
                onLong = if (k.alts.isNotEmpty()) ({ c.onAltLong(k.alts, downShift[0]) }) else null,
            ) {
                Text(label, fontSize = if (k.label.length == 1 && st.lang.code == "RU") 20.sp else 22.sp, fontWeight = FontWeight.Medium)
            }
        }

        KType.SHIFT -> {
            val bg = when (st.shift) { Shift.CAPS -> cs.tertiary; Shift.ON -> cs.primary; else -> cs.tertiaryContainer }
            val fg = when (st.shift) { Shift.CAPS -> cs.onTertiary; Shift.ON -> cs.onPrimary; else -> cs.onTertiaryContainer }
            KeyBase(modifier, bg, fg, radius, c, onPress = { c.onShift() }) {
                Icon(KbIcons.Shift, null, Modifier.size(24.dp))
            }
        }

        KType.BACKSPACE -> KeyBase(
            modifier, cs.tertiaryContainer, cs.onTertiaryContainer, radius, c,
            onPress = { c.onBackspace() }, onRepeat = { c.onBackspace(true) },
        ) { Icon(KbIcons.Backspace, null, Modifier.size(24.dp)) }

        KType.ENTER -> KeyBase(modifier, cs.primary, cs.onPrimary, radius, c, onPress = { c.onEnter() }) {
            Icon(enterIcon(st.enter), null, Modifier.size(24.dp))
        }

        KType.SYM1, KType.SYM2, KType.LETTERS -> {
            val target = when (k.type) { KType.SYM1 -> Page.SYM1; KType.SYM2 -> Page.SYM2; else -> Page.LETTERS }
            KeyBase(modifier, cs.tertiaryContainer, cs.onTertiaryContainer, radius, c, onPress = { c.setPage(target) }) {
                Text(k.label, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            }
        }

        KType.EMOJI -> KeyBase(modifier, cs.tertiaryContainer, cs.onTertiaryContainer, radius, c, onPress = { c.setPage(Page.EMOJI) }) {
            Text(k.label, fontSize = 20.sp)
        }

        KType.LANG -> KeyBase(modifier, cs.tertiaryContainer, cs.onTertiaryContainer, radius, c, onPress = { c.nextLang() }) {
            Text(st.lang.code, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        }

        KType.SPACE -> SpaceKey(modifier, c)
    }
}

private fun enterIcon(k: EnterKind): ImageVector = when (k) {
    EnterKind.RETURN -> KbIcons.Enter
    EnterKind.SEND -> Icons.AutoMirrored.Filled.Send
    EnterKind.SEARCH -> Icons.Default.Search
    EnterKind.GO, EnterKind.NEXT -> Icons.AutoMirrored.Filled.ArrowForward
    EnterKind.DONE -> Icons.Default.Check
}

/**
 * Fires on touch-down (no release latency). Long-press: alternates picker (once) or auto-repeat (backspace).
 * Pressed key morphs to a rounder shape + tinted fill.
 */
@Composable
private fun KeyBase(
    modifier: Modifier,
    container: Color,
    content: Color,
    radius: Dp,
    c: KeyboardController,
    onPress: () -> Unit,
    onLong: (() -> Unit)? = null,
    onRepeat: (() -> Unit)? = null,
    body: @Composable BoxScope.() -> Unit,
) {
    var pressed by remember { mutableStateOf(false) }
    val r by animateDpAsState(if (pressed) radius + 12.dp else radius, spring(stiffness = Spring.StiffnessMediumLow), label = "r")
    val bg by animateColorAsState(if (pressed) lerp(container, content, 0.22f) else container, tween(50), label = "bg")
    val scope = rememberCoroutineScope()
    val press by rememberUpdatedState(onPress)
    val long by rememberUpdatedState(onLong)
    val rep by rememberUpdatedState(onRepeat)
    Box(
        modifier
            .clip(RoundedCornerShape(r))
            .background(bg)
            .pointerInput(Unit) {
                detectTapGestures(onPress = {
                    pressed = true
                    c.feedback()
                    press()
                    val job = if (long != null || rep != null) scope.launch {
                        delay(c.prefs.longPressMs.toLong())
                        val rp = rep
                        val lp = long
                        if (rp != null) {
                            while (true) { rp(); delay(45) }
                        } else if (lp != null) {
                            c.feedback(); lp()
                        }
                    } else null
                    tryAwaitRelease()
                    job?.cancel()
                    pressed = false
                })
            },
        contentAlignment = Alignment.Center,
    ) {
        CompositionLocalProvider(LocalContentColor provides content) { body() }
    }
}

/** Space: tap = space (on release), horizontal drag = cursor move. */
@Composable
private fun SpaceKey(modifier: Modifier, c: KeyboardController) {
    val cs = MaterialTheme.colorScheme
    var pressed by remember { mutableStateOf(false) }
    val base = cs.surfaceContainerHighest
    val bg by animateColorAsState(if (pressed) lerp(base, cs.onSurface, 0.2f) else base, tween(50), label = "space")
    val swipe = c.prefs.swipeCursor
    Box(
        modifier
            .clip(RoundedCornerShape(c.prefs.keyRadius.dp))
            .background(bg)
            .pointerInput(swipe) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    pressed = true
                    var dragged = false
                    var acc = 0f
                    val step = 12.dp.toPx()
                    while (true) {
                        val ev = awaitPointerEvent()
                        val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
                        if (!ch.pressed) {
                            if (!dragged) { c.feedback(); c.onSpace() }
                            break
                        }
                        if (swipe) {
                            acc += ch.positionChange().x
                            while (abs(acc) >= step) {
                                val d = if (acc > 0) 1 else -1
                                c.moveCursor(d)
                                acc -= d * step
                                dragged = true
                            }
                            if (dragged) ch.consume()
                        }
                    }
                    pressed = false
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(c.state.lang.name, color = cs.onSurfaceVariant, fontSize = 14.sp, maxLines = 1)
    }
}

// ------------------------------------------------------------------ emoji

@Composable
private fun EmojiPanel(c: KeyboardController, height: Dp) {
    val cs = MaterialTheme.colorScheme
    val st = c.state
    val p = c.prefs
    val radius = p.keyRadius.dp
    Column(Modifier.fillMaxWidth().height(height)) {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(44.dp),
            modifier = Modifier.weight(1f).fillMaxWidth(),
        ) {
            items(Emojis.cats[st.emojiCat.coerceIn(0, Emojis.cats.lastIndex)].second) { e ->
                Box(
                    Modifier.height(44.dp).clip(CircleShape).clickable { c.feedback(); c.typeRaw(e) },
                    contentAlignment = Alignment.Center,
                ) { Text(e, fontSize = 26.sp) }
            }
        }
        Row(
            Modifier.fillMaxWidth().height(p.keyHeight.dp),
            horizontalArrangement = Arrangement.spacedBy(p.keyGap.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            KeyBase(Modifier.weight(1.3f).fillMaxHeight(), cs.tertiaryContainer, cs.onTertiaryContainer, radius, c, onPress = { c.setPage(Page.LETTERS) }) {
                Text("ABC", fontSize = 15.sp, fontWeight = FontWeight.Medium)
            }
            Row(Modifier.weight(6f).fillMaxHeight(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                Emojis.cats.forEachIndexed { i, cat ->
                    val sel = i == st.emojiCat
                    Box(
                        Modifier.size(36.dp).clip(CircleShape)
                            .background(if (sel) cs.primaryContainer else Color.Transparent)
                            .clickable { st.emojiCat = i },
                        contentAlignment = Alignment.Center,
                    ) { Text(cat.first, fontSize = 18.sp) }
                }
            }
            KeyBase(
                Modifier.weight(1.3f).fillMaxHeight(), cs.tertiaryContainer, cs.onTertiaryContainer, radius, c,
                onPress = { c.onBackspace() }, onRepeat = { c.onBackspace(true) },
            ) { Icon(KbIcons.Backspace, null, Modifier.size(24.dp)) }
        }
    }
}
