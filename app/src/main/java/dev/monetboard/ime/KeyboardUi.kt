package dev.monetboard.ime

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.Crossfade
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.monetboard.ai.TrLangs
import dev.monetboard.ui.CookieButton
import dev.monetboard.ui.KbIcons
import dev.monetboard.ui.MonetTheme
import dev.monetboard.ui.PillButton
import dev.monetboard.ui.StaggerIn
import dev.monetboard.ui.TapTile
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

// ------------------------------------------------------------------ transitions

private fun none() = ContentTransform(EnterTransition.None, ExitTransition.None)

private fun barSwap(anim: Boolean): ContentTransform =
    if (!anim) none()
    else (fadeIn(tween(180, delayMillis = 50)) + slideInVertically(tween(220)) { -it / 4 }) togetherWith
        (fadeOut(tween(90)) + slideOutVertically(tween(120)) { it / 4 })

private fun areaSwap(anim: Boolean): ContentTransform =
    if (!anim) none()
    else (fadeIn(tween(200, delayMillis = 70)) +
        scaleIn(initialScale = 0.94f, animationSpec = tween(240, delayMillis = 70)) +
        slideInVertically(tween(260)) { it / 12 }) togetherWith
        (fadeOut(tween(100)) + scaleOut(targetScale = 0.97f, animationSpec = tween(110)))

// ------------------------------------------------------------------ root

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun KeyboardRoot(c: KeyboardController) {
    val view = LocalView.current
    SideEffect { c.hostView = view }
    MonetTheme(c.prefs) {
        val p = c.prefs
        val anim = p.animations
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
                TopArea(c, anim)
                Spacer(Modifier.height(6.dp))
                MainArea(c, anim, keysH)
            }
        }
    }
}

@Composable
private fun TopArea(c: KeyboardController, anim: Boolean) {
    val key = when {
        c.ai.open -> "ai"
        c.state.panel == Panel.TRANSLATE -> "tr"
        else -> c.state.panel.name
    }
    AnimatedContent(
        targetState = key,
        transitionSpec = { barSwap(anim) },
        modifier = Modifier.fillMaxWidth(),
        label = "bar",
    ) { k ->
        when (k) {
            "ai" -> AiBar(c)
            "tr" -> TranslateBar(c)
            Panel.KEYS.name -> SuggestBar(c)
            else -> TitleBar(c, k)
        }
    }
}

@Composable
private fun MainArea(c: KeyboardController, anim: Boolean, keysH: Dp) {
    val st = c.state
    val key = when {
        c.ai.open && c.ai.showCard -> "ai-card"
        st.panel == Panel.TRANSLATE && c.translator.showCard -> "tr-card"
        st.panel == Panel.KEYS || st.panel == Panel.TRANSLATE -> "keys"
        else -> st.panel.name
    }
    AnimatedContent(
        targetState = key,
        transitionSpec = { areaSwap(anim) },
        modifier = Modifier.fillMaxWidth(),
        label = "area",
    ) { k ->
        when (k) {
            "ai-card" -> {
                val ai = c.ai
                CardPanel(
                    Modifier.fillMaxWidth().height(keysH), ai.busy, ai.error, ai.result, ai.applyLabel,
                    onApply = { ai.apply() }, onRetry = { ai.retry() }, onBack = { ai.dismissCard() },
                    onSettings = { c.openSettings() },
                )
            }
            "tr-card" -> {
                val tr = c.translator
                CardPanel(
                    Modifier.fillMaxWidth().height(keysH), tr.busy, tr.error, tr.result, tr.applyLabel,
                    onApply = { tr.apply() }, onRetry = { tr.run() }, onBack = { tr.dismissCard() },
                    onSettings = { c.openSettings() },
                    extra = {
                        val cs = MaterialTheme.colorScheme
                        PillButton("Копировать", cs.secondaryContainer, cs.onSecondaryContainer) { tr.copy() }
                    },
                )
            }
            "keys" -> KeysArea(c, anim)
            "EMOJI" -> EmojiPanel(c, keysH)
            "TOOLS" -> ToolsPanel(c, keysH, anim)
            "CLIP" -> ClipPanel(c, keysH)
            "VOICE" -> VoicePanel(c, keysH)
            else -> {}
        }
    }
}

// ------------------------------------------------------------------ top bars

@Composable
private fun MicCookie(c: KeyboardController) {
    val cs = MaterialTheme.colorScheme
    val on = c.state.voiceListening
    CookieButton(
        KbIcons.Mic,
        if (on) cs.error else cs.primary,
        if (on) cs.onError else cs.onPrimary,
        pulse = on,
    ) { c.toggleMic() }
}

@Composable
private fun SuggestBar(c: KeyboardController) {
    val st = c.state
    val cs = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().height(44.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        CookieButton(KbIcons.Spark, cs.primary, cs.onPrimary) { c.openPanel(Panel.TOOLS) }
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
                        Crossfade(w, modifier = Modifier.weight(1f), animationSpec = tween(100), label = "sg$i") { word ->
                            PillButton(
                                word,
                                if (hi) cs.primary else cs.secondaryContainer,
                                if (hi) cs.onPrimary else cs.onSecondaryContainer,
                                Modifier.fillMaxWidth(),
                            ) { c.feedback(); c.onSuggestion(word) }
                        }
                    }
                }
            }
        }
        MicCookie(c)
    }
}

@Composable
private fun TitleBar(c: KeyboardController, key: String) {
    val cs = MaterialTheme.colorScheme
    val title = when (key) {
        "TOOLS" -> "Инструменты"
        "CLIP" -> "Буфер обмена"
        "EMOJI" -> "Эмодзи"
        "VOICE" -> "Голосовой ввод"
        else -> ""
    }
    Row(
        Modifier.fillMaxWidth().height(44.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        CookieButton(Icons.Default.Close, cs.primary, cs.onPrimary) { c.closeAny() }
        Text(
            title, Modifier.weight(1f), color = cs.onSurface, fontSize = 17.sp,
            fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center,
        )
        when (key) {
            "CLIP" -> CookieButton(Icons.Default.Delete, cs.secondaryContainer, cs.onSecondaryContainer) { c.clipboard.clear() }
            "VOICE" -> Spacer(Modifier.size(40.dp))
            else -> MicCookie(c)
        }
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
            InputPill(ai.prompt, "Спроси или попроси…", Modifier.weight(1f))
            CookieButton(Icons.AutoMirrored.Filled.Send, cs.primary, cs.onPrimary, size = 44.dp) { ai.send() }
        }
    }
}

@Composable
private fun TranslateBar(c: KeyboardController) {
    val tr = c.translator
    val p = c.prefs
    val cs = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            Modifier.fillMaxWidth().height(44.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            CookieButton(Icons.Default.Close, cs.primary, cs.onPrimary) { c.closeAny() }
            LazyRow(
                Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                item {
                    PillButton(
                        if (p.trProvider == 0) "Google" else "Яндекс",
                        cs.tertiaryContainer, cs.onTertiaryContainer,
                    ) { p.trProvider = 1 - p.trProvider; if (tr.result != null) tr.run() }
                }
                items(TrLangs.size) { i ->
                    val sel = p.trTarget == i
                    PillButton(
                        TrLangs[i].name,
                        if (sel) cs.primary else cs.secondaryContainer,
                        if (sel) cs.onPrimary else cs.onSecondaryContainer,
                    ) { p.trTarget = i; if (tr.result != null) tr.run() }
                }
            }
        }
        Row(
            Modifier.fillMaxWidth().height(46.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            InputPill(tr.input, "Текст для перевода (или возьму из поля)", Modifier.weight(1f))
            CookieButton(Icons.AutoMirrored.Filled.Send, cs.primary, cs.onPrimary, size = 44.dp) { tr.run() }
        }
    }
}

@Composable
private fun InputPill(text: String, hint: String, modifier: Modifier) {
    val cs = MaterialTheme.colorScheme
    Box(
        modifier.fillMaxHeight().border(1.5.dp, cs.outline, RoundedCornerShape(50)).padding(horizontal = 16.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        if (text.isEmpty()) {
            Text(hint, color = cs.onSurfaceVariant, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        } else {
            Text(text.takeLast(90), color = cs.onSurface, fontSize = 15.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

// ------------------------------------------------------------------ cards

@Composable
private fun Dots(color: Color) {
    val t = rememberInfiniteTransition(label = "dots")
    Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
        repeat(3) { i ->
            val s by t.animateFloat(
                0.4f, 1f,
                infiniteRepeatable(tween(520, easing = FastOutSlowInEasing), RepeatMode.Reverse, initialStartOffset = StartOffset(i * 140)),
                label = "d$i",
            )
            Box(Modifier.size(9.dp).graphicsLayer { scaleX = s; scaleY = s; alpha = s }.clip(CircleShape).background(color))
        }
    }
}

@Composable
private fun CardPanel(
    modifier: Modifier,
    busy: Boolean,
    err: String?,
    res: String?,
    applyLabel: String,
    onApply: () -> Unit,
    onRetry: () -> Unit,
    onBack: () -> Unit,
    onSettings: () -> Unit,
    extra: (@Composable () -> Unit)? = null,
) {
    val cs = MaterialTheme.colorScheme
    val mode = when { busy -> 0; err != null -> 1; else -> 2 }
    Column(modifier.clip(RoundedCornerShape(24.dp)).background(cs.surfaceContainerHigh).padding(14.dp)) {
        Box(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())) {
            Crossfade(mode, animationSpec = tween(180), label = "card") { m ->
                when (m) {
                    0 -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Dots(cs.primary)
                        Text("Думаю…", color = cs.onSurfaceVariant, fontSize = 15.sp)
                    }
                    1 -> Text(err.orEmpty(), color = cs.error, fontSize = 15.sp)
                    else -> Text(res.orEmpty(), color = cs.onSurface, fontSize = 16.sp)
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (!busy && res != null && err == null) {
                PillButton(applyLabel, cs.primary, cs.onPrimary) { onApply() }
                extra?.invoke()
                PillButton("Ещё раз", cs.secondaryContainer, cs.onSecondaryContainer) { onRetry() }
            }
            if (!busy && err != null) {
                PillButton("Настройки", cs.primary, cs.onPrimary) { onSettings() }
                PillButton("Ещё раз", cs.secondaryContainer, cs.onSecondaryContainer) { onRetry() }
            }
            Spacer(Modifier.weight(1f))
            PillButton("Назад", cs.surfaceContainerHighest, cs.onSurface) { onBack() }
        }
    }
}

// ------------------------------------------------------------------ keys

@Composable
private fun KeysArea(c: KeyboardController, anim: Boolean) {
    val st = c.state
    AnimatedContent(
        targetState = st.page to st.langIndex,
        transitionSpec = {
            if (!anim) none()
            else (fadeIn(tween(150)) + scaleIn(initialScale = 0.96f, animationSpec = tween(150))) togetherWith fadeOut(tween(90))
        },
        modifier = Modifier.fillMaxWidth(),
        label = "keys",
    ) { (page, li) -> KeyRows(c, page, li) }
}

@Composable
private fun KeyRows(c: KeyboardController, page: Page, langIndex: Int) {
    val p = c.prefs
    val numRow = p.numberRow
    val rows = remember(page, langIndex, numRow) { Layouts.rows(page, Layouts.langs[langIndex], numRow) }
    val gap = p.keyGap.dp
    Column(verticalArrangement = Arrangement.spacedBy(gap)) {
        rows.forEach { row ->
            Row(
                Modifier.fillMaxWidth().height(p.keyHeight.dp),
                horizontalArrangement = Arrangement.spacedBy(gap),
            ) {
                row.forEach { k -> KeyView(k, c, page, langIndex, Modifier.weight(k.weight).fillMaxHeight()) }
            }
        }
    }
}

@Composable
private fun KeyView(k: KeyDef, c: KeyboardController, page: Page, langIndex: Int, modifier: Modifier) {
    val cs = MaterialTheme.colorScheme
    val st = c.state
    val radius = c.prefs.keyRadius.dp
    when (k.type) {
        KType.SPACER, KType.LANG -> Spacer(modifier)

        KType.CHAR -> {
            val shifted = page == Page.LETTERS && st.shift != Shift.OFF
            val label = if (shifted) k.label.uppercase() else k.label
            val downShift = remember { BooleanArray(1) }
            val ru = Layouts.langs[langIndex].code == "RU"
            KeyBase(
                modifier, cs.secondaryContainer, cs.onSecondaryContainer, radius, c,
                onPress = { downShift[0] = shifted; c.onChar(label) },
                onLong = if (k.alts.isNotEmpty()) ({ c.onAltLong(k.alts, downShift[0]) }) else null,
            ) {
                Text(label, fontSize = if (ru && page == Page.LETTERS) 20.sp else 22.sp, fontWeight = FontWeight.Medium)
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
            Crossfade(st.enter, animationSpec = tween(160), label = "enter") { Icon(enterIcon(it), null, Modifier.size(24.dp)) }
        }

        KType.SYM1, KType.SYM2, KType.LETTERS -> {
            val target = when (k.type) { KType.SYM1 -> Page.SYM1; KType.SYM2 -> Page.SYM2; else -> Page.LETTERS }
            KeyBase(modifier, cs.tertiaryContainer, cs.onTertiaryContainer, radius, c, onPress = { c.setPage(target) }) {
                Text(k.label, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            }
        }

        KType.EMOJI -> KeyBase(modifier, cs.tertiaryContainer, cs.onTertiaryContainer, radius, c, onPress = { c.openPanel(Panel.EMOJI) }) {
            Text(k.label, fontSize = 20.sp)
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

/** Space: tap = space (on release), horizontal drag = cursor move, centered slider switches RU/EN. */
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
        LangSlider(c)
    }
}

/** Pill with a springy thumb: tap a language or drag across it. Consumes its own touches so the space bar doesn't type. */
@Composable
private fun LangSlider(c: KeyboardController) {
    val cs = MaterialTheme.colorScheme
    val langs = Layouts.langs
    val idx = c.state.langIndex
    val segW = 44.dp
    val h = 32.dp
    val thumbX by animateDpAsState(segW * idx, spring(dampingRatio = 0.55f, stiffness = 380f), label = "thumb")
    Box(
        Modifier
            .width(segW * langs.size)
            .height(h)
            .clip(RoundedCornerShape(h / 2))
            .background(cs.surfaceContainer)
            .pointerInput(langs.size) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    down.consume()
                    val segPx = segW.toPx()
                    fun pick(x: Float) {
                        val i = (x / segPx).toInt().coerceIn(0, langs.lastIndex)
                        if (i != c.state.langIndex) { c.setLang(i); c.feedback() }
                    }
                    pick(down.position.x)
                    while (true) {
                        val ev = awaitPointerEvent()
                        val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
                        if (!ch.pressed) break
                        pick(ch.position.x)
                        ch.consume()
                    }
                }
            },
    ) {
        Box(Modifier.offset(x = thumbX).size(segW, h).clip(RoundedCornerShape(h / 2)).background(cs.primary))
        Row(Modifier.fillMaxSize()) {
            langs.forEachIndexed { i, l ->
                val col by animateColorAsState(if (i == idx) cs.onPrimary else cs.onSurfaceVariant, tween(180), label = "lc$i")
                Box(Modifier.width(segW).fillMaxHeight(), contentAlignment = Alignment.Center) {
                    Text(l.code, color = col, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
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
            KeyBase(Modifier.weight(1.3f).fillMaxHeight(), cs.tertiaryContainer, cs.onTertiaryContainer, radius, c, onPress = { c.openPanel(Panel.KEYS) }) {
                Text("ABC", fontSize = 15.sp, fontWeight = FontWeight.Medium)
            }
            Row(Modifier.weight(6f).fillMaxHeight(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                Emojis.cats.forEachIndexed { i, cat ->
                    val sel = i == st.emojiCat
                    val bg by animateColorAsState(if (sel) cs.primaryContainer else Color.Transparent, tween(180), label = "ec$i")
                    Box(
                        Modifier.size(36.dp).clip(CircleShape).background(bg).clickable { st.emojiCat = i },
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

// ------------------------------------------------------------------ tools menu

@Composable
private fun ToolsPanel(c: KeyboardController, height: Dp, anim: Boolean) {
    val cs = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth().height(height), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ToolTile(0, anim, Modifier.weight(1f).fillMaxHeight(), "Буфер обмена", "история копий",
                cs.secondaryContainer, cs.onSecondaryContainer, { c.feedback(); c.openPanel(Panel.CLIP) }) {
                Icon(KbIcons.Clipboard, null, Modifier.size(28.dp))
            }
            ToolTile(1, anim, Modifier.weight(1f).fillMaxHeight(), "Исправить", "ИИ · текст в поле",
                cs.primaryContainer, cs.onPrimaryContainer, { c.feedback(); c.openAiFix() }) {
                Icon(KbIcons.Spark, null, Modifier.size(28.dp))
            }
            ToolTile(2, anim, Modifier.weight(1f).fillMaxHeight(), "Переводчик", "Google · Яндекс",
                cs.tertiaryContainer, cs.onTertiaryContainer, { c.feedback(); c.openPanel(Panel.TRANSLATE) }) {
                Text("文A", fontSize = 22.sp, fontWeight = FontWeight.Bold)
            }
        }
        Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ToolTile(3, anim, Modifier.weight(1f).fillMaxHeight(), "Эмодзи", "смайлы и жесты",
                cs.secondaryContainer, cs.onSecondaryContainer, { c.feedback(); c.openPanel(Panel.EMOJI) }) {
                Text("😊", fontSize = 26.sp)
            }
            ToolTile(4, anim, Modifier.weight(1f).fillMaxHeight(), "ИИ-режим", "чат и действия",
                cs.primaryContainer, cs.onPrimaryContainer, { c.feedback(); c.openAi() }) {
                Text("AI", fontSize = 24.sp, fontWeight = FontWeight.Bold)
            }
            ToolTile(5, anim, Modifier.weight(1f).fillMaxHeight(), "Настройки", "цвета, ИИ, голос",
                cs.tertiaryContainer, cs.onTertiaryContainer, { c.openSettings() }) {
                Icon(Icons.Default.Settings, null, Modifier.size(28.dp))
            }
        }
    }
}

@Composable
private fun ToolTile(
    index: Int,
    anim: Boolean,
    modifier: Modifier,
    label: String,
    sub: String,
    container: Color,
    content: Color,
    onClick: () -> Unit,
    icon: @Composable () -> Unit,
) {
    StaggerIn(index, anim, modifier) {
        TapTile(Modifier.fillMaxSize(), container, 24.dp, onClick) {
            CompositionLocalProvider(LocalContentColor provides content) {
                Column(
                    Modifier.fillMaxSize().padding(6.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(Modifier.height(32.dp), contentAlignment = Alignment.Center) { icon() }
                    Spacer(Modifier.height(4.dp))
                    Text(label, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(sub, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, color = content.copy(alpha = 0.7f))
                }
            }
        }
    }
}

// ------------------------------------------------------------------ clipboard

@Composable
private fun ClipPanel(c: KeyboardController, height: Dp) {
    val cs = MaterialTheme.colorScheme
    val list = c.clipboard.items
    Box(Modifier.fillMaxWidth().height(height)) {
        if (list.isEmpty()) {
            Column(Modifier.fillMaxSize(), Arrangement.Center, Alignment.CenterHorizontally) {
                Icon(KbIcons.Clipboard, null, tint = cs.onSurfaceVariant, modifier = Modifier.size(34.dp))
                Spacer(Modifier.height(8.dp))
                Text(
                    if (c.prefs.clipEnabled) "Пока пусто. Скопируй текст, он появится здесь"
                    else "История буфера выключена в настройках",
                    color = cs.onSurfaceVariant, fontSize = 14.sp, textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 24.dp),
                )
            }
        } else {
            LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(list, key = { it.text }) { clip -> ClipRow(clip, c, Modifier.animateItem()) }
            }
        }
    }
}

@Composable
private fun ClipRow(clip: Clip, c: KeyboardController, modifier: Modifier) {
    val cs = MaterialTheme.colorScheme
    TapTile(
        modifier.fillMaxWidth(),
        if (clip.pinned) cs.primaryContainer else cs.surfaceContainerHigh,
        18.dp,
        onClick = { c.feedback(); c.insertClip(clip.text) },
    ) {
        Row(
            Modifier.fillMaxWidth().padding(start = 14.dp, top = 6.dp, bottom = 6.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                clip.text, Modifier.weight(1f), fontSize = 15.sp, maxLines = 3, overflow = TextOverflow.Ellipsis,
                color = if (clip.pinned) cs.onPrimaryContainer else cs.onSurface,
            )
            IconTap(Icons.Default.Star, if (clip.pinned) cs.primary else cs.onSurfaceVariant) { c.clipboard.togglePin(clip) }
            IconTap(Icons.Default.Close, cs.onSurfaceVariant) { c.clipboard.remove(clip) }
        }
    }
}

@Composable
private fun IconTap(icon: ImageVector, tint: Color, onClick: () -> Unit) {
    Box(Modifier.size(38.dp).clip(CircleShape).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(20.dp))
    }
}

// ------------------------------------------------------------------ voice

@Composable
private fun VoicePanel(c: KeyboardController, height: Dp) {
    val cs = MaterialTheme.colorScheme
    val st = c.state
    val level by animateFloatAsState(st.voiceLevel, tween(90), label = "lvl")
    val t = rememberInfiniteTransition(label = "voice")
    val ph by t.animateFloat(0f, 1f, infiniteRepeatable(tween(1400, easing = LinearEasing)), label = "ph")
    val ring by t.animateFloat(0f, 1f, infiniteRepeatable(tween(1700, easing = LinearEasing)), label = "ring")
    val listening = st.voiceListening
    val ringColor = cs.primary
    Column(
        Modifier.fillMaxWidth().height(height).clip(RoundedCornerShape(24.dp)).background(cs.surfaceContainerHigh).padding(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.fillMaxWidth().height(44.dp), contentAlignment = Alignment.Center) {
            val err = st.voiceError
            when {
                err != null -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(err, color = cs.error, fontSize = 14.sp, maxLines = 2, textAlign = TextAlign.Center, modifier = Modifier.weight(1f, fill = false))
                    if (st.voiceNeedsPermission) PillButton("Дать доступ", cs.primary, cs.onPrimary) { c.openPermission() }
                }
                st.voiceText.isNotEmpty() -> Text(st.voiceText, color = cs.onSurface, fontSize = 16.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
                else -> Text(if (listening) "Говорите…" else "Нажми на микрофон", color = cs.onSurfaceVariant, fontSize = 15.sp)
            }
        }
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                if (listening) {
                    for (k in 0..1) {
                        val pr = (ring + k * 0.5f) % 1f
                        drawCircle(
                            color = ringColor.copy(alpha = (1f - pr) * (0.18f + 0.3f * level)),
                            radius = 30.dp.toPx() + pr * (34.dp.toPx() + 22.dp.toPx() * level),
                        )
                    }
                }
            }
            CookieButton(
                KbIcons.Mic,
                if (listening) cs.error else cs.primary,
                if (listening) cs.onError else cs.onPrimary,
                size = 64.dp,
                modifier = Modifier.graphicsLayer { val s = 1f + level * 0.12f; scaleX = s; scaleY = s },
            ) { if (listening) c.voice.stop() else c.voice.start() }
        }
        Row(
            Modifier.fillMaxWidth().height(36.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            repeat(13) { i ->
                val wave = 0.5f + 0.5f * sin(2.0 * PI * (ph + i * 0.08f)).toFloat()
                val amp = if (listening) (0.12f + 0.88f * level) * (0.35f + 0.65f * wave) else 0.06f
                Box(
                    Modifier.width(5.dp).height(4.dp + 30.dp * amp).clip(RoundedCornerShape(3.dp))
                        .background(if (listening) cs.primary else cs.outlineVariant),
                )
            }
        }
    }
}
