package dev.monetboard.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import dev.monetboard.ai.AiClient
import dev.monetboard.ai.TrLangs
import dev.monetboard.data.DEFAULT_SYSTEM_PROMPT
import dev.monetboard.data.Prefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

enum class Screen(val title: String) {
    HOME("Monet Board"), COLORS("Цвета и фон"), KEYBOARD("Раскладка и отклик"),
    TEXT("Ввод текста"), TOOLS("Инструменты"), AI("ИИ-агент"),
}

fun isImeEnabled(ctx: Context): Boolean =
    ctx.getSystemService(InputMethodManager::class.java).enabledInputMethodList.any { it.packageName == ctx.packageName }

fun isImeSelected(ctx: Context): Boolean {
    val id = Settings.Secure.getString(ctx.contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD) ?: return false
    return id.startsWith(ctx.packageName + "/")
}

private fun micGranted(ctx: Context) =
    ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

// ------------------------------------------------------------------ shell

@Composable
fun SettingsApp(prefs: Prefs, tick: Int) {
    var screenName by rememberSaveable { mutableStateOf(Screen.HOME.name) }
    val screen = Screen.valueOf(screenName)
    val anim = prefs.animations
    BackHandler(enabled = screen != Screen.HOME) { screenName = Screen.HOME.name }
    val cs = MaterialTheme.colorScheme

    CompositionLocalProvider(LocalAnimations provides anim) {
        Column(Modifier.fillMaxSize().safeDrawingPadding()) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AnimatedVisibility(
                    visible = screen != Screen.HOME,
                    enter = scaleIn(spring(0.6f, 400f)) + fadeIn() + expandHorizontally(),
                    exit = scaleOut() + fadeOut() + shrinkHorizontally(),
                ) {
                    Box(
                        Modifier.padding(end = 14.dp).size(48.dp).clip(CircleShape).background(cs.primaryContainer)
                            .clickable { screenName = Screen.HOME.name },
                        contentAlignment = Alignment.Center,
                    ) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = cs.onPrimaryContainer) }
                }
                AnimatedContent(
                    targetState = screen.title,
                    transitionSpec = {
                        if (!anim) ContentTransform(EnterTransition.None, ExitTransition.None)
                        else (slideInVertically(tween(260)) { it / 2 } + fadeIn(tween(220))) togetherWith
                            (slideOutVertically(tween(200)) { -it / 2 } + fadeOut(tween(120)))
                    },
                    label = "title",
                ) { title ->
                    Text(title, fontSize = 30.sp, fontWeight = FontWeight.Bold, color = cs.onBackground)
                }
            }
            TestField()
            AnimatedContent(
                targetState = screen,
                transitionSpec = {
                    if (!anim) ContentTransform(EnterTransition.None, ExitTransition.None)
                    else if (targetState != Screen.HOME)
                        (slideInHorizontally(tween(340, easing = FastOutSlowInEasing)) { it / 3 } + fadeIn(tween(260))) togetherWith
                            (slideOutHorizontally(tween(260)) { -it / 5 } + fadeOut(tween(160)))
                    else
                        (slideInHorizontally(tween(340, easing = FastOutSlowInEasing)) { -it / 3 } + fadeIn(tween(260))) togetherWith
                            (slideOutHorizontally(tween(260)) { it / 5 } + fadeOut(tween(160)))
                },
                modifier = Modifier.weight(1f).fillMaxWidth(),
                label = "page",
            ) { s ->
                val rc = remember { RevealCounter() }
                CompositionLocalProvider(LocalReveal provides rc) {
                    Column(
                        Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        when (s) {
                            Screen.HOME -> HomePage(tick) { screenName = it.name }
                            Screen.COLORS -> ColorsPage(prefs)
                            Screen.KEYBOARD -> KeyboardPage(prefs)
                            Screen.TEXT -> TextPage(prefs)
                            Screen.TOOLS -> ToolsPage(prefs, tick)
                            Screen.AI -> AiPage(prefs)
                        }
                        Spacer(Modifier.height(24.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun TestField() {
    var t by remember { mutableStateOf("") }
    OutlinedTextField(
        value = t, onValueChange = { t = it },
        placeholder = { Text("Проверьте клавиатуру здесь…") },
        leadingIcon = { Icon(Icons.Default.Edit, null) },
        shape = RoundedCornerShape(28.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
    )
}

// ------------------------------------------------------------------ pages

@Composable
private fun HomePage(tick: Int, go: (Screen) -> Unit) {
    val ctx = LocalContext.current
    val cs = MaterialTheme.colorScheme
    val enabled = remember(tick) { isImeEnabled(ctx) }
    val selected = remember(tick) { isImeSelected(ctx) }

    if (enabled && selected) {
        Row(
            Modifier.reveal().fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(cs.primaryContainer).padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            SpinCookie(Icons.Default.Check)
            Column {
                Text("Всё готово", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = cs.onPrimaryContainer)
                Text("Клавиатура включена и выбрана", color = cs.onPrimaryContainer)
            }
        }
    } else {
        Column(
            Modifier.reveal().fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(cs.surfaceContainer).padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("Подключение клавиатуры", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = cs.onSurface)
            StepRow("1. Включить в системе", enabled) {
                ctx.startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
            }
            StepRow("2. Выбрать по умолчанию", selected) {
                ctx.getSystemService(InputMethodManager::class.java).showInputMethodPicker()
            }
        }
    }

    NavCard(Icons.Default.Star, "Цвета и фон", "Monet, акцент, AMOLED, тема") { go(Screen.COLORS) }
    NavCard(Icons.Default.Menu, "Раскладка и отклик", "Размер клавиш, форма, вибрация, анимации") { go(Screen.KEYBOARD) }
    NavCard(Icons.Default.Edit, "Ввод текста", "Автоисправление, подсказки, знаки, заглавные") { go(Screen.TEXT) }
    NavCard(Icons.Default.Notifications, "Инструменты", "Переводчик, голос, буфер обмена") { go(Screen.TOOLS) }
    NavCard(dev.monetboard.ui.KbIcons.Spark, "ИИ-агент", "Ключ, модель, провайдер, промпт") { go(Screen.AI) }
}

@Composable
private fun SpinCookie(icon: ImageVector) {
    val cs = MaterialTheme.colorScheme
    val rot by rememberInfiniteTransition(label = "spin").animateFloat(
        0f, 360f, infiniteRepeatable(tween(14000, easing = LinearEasing)), label = "rot",
    )
    Box(Modifier.size(56.dp), contentAlignment = Alignment.Center) {
        Box(Modifier.fillMaxSize().graphicsLayer { rotationZ = rot }.clip(CookieShape).background(cs.primary))
        Icon(icon, null, tint = cs.onPrimary, modifier = Modifier.size(28.dp))
    }
}

@Composable
private fun StepRow(text: String, done: Boolean, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        PillButton(
            text,
            if (done) cs.secondaryContainer else cs.primary,
            if (done) cs.onSecondaryContainer else cs.onPrimary,
            Modifier.weight(1f), height = 44.dp, onClick = onClick,
        )
        if (done) Icon(Icons.Default.Check, null, tint = cs.primary)
    }
}

@Composable
private fun ColorsPage(p: Prefs) {
    val cs = MaterialTheme.colorScheme
    val monetOk = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    SectionLabel("Тема")
    SegmentedRow(listOf("Системная", "Светлая", "Тёмная"), p.themeMode) { p.themeMode = it }

    SectionLabel("Источник цвета")
    SegmentedRow(listOf("Material You", "Свой акцент"), if (monetOk) p.colorSource else 1, enabled = monetOk) { p.colorSource = it }
    if (!monetOk) Text("Monet из обоев доступен с Android 12", color = cs.onSurfaceVariant, fontSize = 13.sp)

    if (!monetOk || p.colorSource == 1) {
        SectionLabel("Акцент")
        Row(Modifier.reveal().fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Accents.forEachIndexed { i, a ->
                val sel = p.accent == i
                val pad by animateDpAsState(if (sel) 0.dp else 5.dp, spring(0.55f, 420f), label = "acc$i")
                Box(Modifier.weight(1f).aspectRatio(1f), contentAlignment = Alignment.Center) {
                    Box(
                        Modifier.fillMaxSize().padding(pad).clip(CircleShape).background(accentSwatch(a.hue))
                            .clickable { p.accent = i },
                        contentAlignment = Alignment.Center,
                    ) { if (sel) Icon(Icons.Default.Check, null, tint = Color.Black.copy(alpha = .7f)) }
                }
            }
        }
    }

    SwitchRow(Icons.Default.Face, "AMOLED чёрный", "Чисто чёрный фон в тёмной теме", p.amoled) { p.amoled = it }

    SectionLabel("Предпросмотр")
    KeyboardPreview(p)
}

@Composable
private fun KeyboardPage(p: Prefs) {
    SwitchRow(Icons.Default.Star, "Анимации", "Переходы, пружинки, волны. Выключи на слабом телефоне", p.animations) { p.animations = it }
    SliderRow("Высота клавиш", p.keyHeight, 40, 64, 1, "dp") { p.keyHeight = it }
    SliderRow("Расстояние между клавишами", p.keyGap, 2, 8, 1, "dp") { p.keyGap = it }
    SliderRow("Скругление клавиш", p.keyRadius, 6, 28, 1, "dp") { p.keyRadius = it }
    SwitchRow(Icons.Default.Menu, "Ряд цифр", "Отдельный ряд 1–0 над буквами", p.numberRow) { p.numberRow = it }
    SwitchRow(Icons.Default.Menu, "Курсор по пробелу", "Свайп по пробелу двигает курсор", p.swipeCursor) { p.swipeCursor = it }
    SwitchRow(Icons.Default.Notifications, "Вибрация", "Отклик при нажатии", p.haptics) { p.haptics = it }
    SwitchRow(Icons.Default.Notifications, "Звук клавиш", "Системный звук нажатия", p.sound) { p.sound = it }
    SliderRow("Долгое нажатие", p.longPressMs, 200, 600, 50, "мс") { p.longPressMs = it }
}

@Composable
private fun TextPage(p: Prefs) {
    SwitchRow(Icons.Default.Check, "Автоисправление", "Исправляет опечатки по пробелу, только если слова нет в словаре. ⌫ сразу после — отмена, и слово запоминается", p.autocorrect) { p.autocorrect = it }
    Column(
        Modifier.reveal().fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(MaterialTheme.colorScheme.surfaceContainer).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("Насколько смело исправлять", fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
        Text(
            if (p.autocorrectLevel == 0) "Только явные опечатки: одна буква не так, первая буква совпадает"
            else "Активно: до двух ошибок в длинных словах, в т.ч. перестановки",
            color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp,
        )
        SegmentedRow(listOf("Осторожно", "Активно"), p.autocorrectLevel) { p.autocorrectLevel = it }
    }
    SwitchRow(Icons.Default.Info, "Подсказки слов", "Три варианта над клавиатурой", p.suggestions) { p.suggestions = it }
    SwitchRow(Icons.Default.Edit, "Знаки после пробела", "«слово ,» → «слово, » — пробел переезжает за запятую или точку", p.punctSpace) { p.punctSpace = it }
    SwitchRow(Icons.Default.Edit, "Заглавная в начале предложения", "Авто-шифт после точки и в начале поля", p.autoCap) { p.autoCap = it }
    SwitchRow(Icons.Default.Edit, "Точка двойным пробелом", "Два пробела подряд → «. »", p.doubleSpacePeriod) { p.doubleSpacePeriod = it }
    NavCard(Icons.Default.Delete, "Сбросить выученные слова", "Сейчас в словаре: ${p.learnedCount()}") { p.clearLearned() }
}

@Composable
private fun ToolsPage(p: Prefs, tick: Int) {
    val ctx = LocalContext.current
    val cs = MaterialTheme.colorScheme
    val mic = remember(tick) { micGranted(ctx) }

    SectionLabel("Переводчик")
    SegmentedRow(listOf("Google", "Яндекс"), p.trProvider) { p.trProvider = it }
    Text(
        if (p.trProvider == 0) "Google: без ключа, через открытый endpoint translate.googleapis.com. Неофициальный, при частых запросах может притормозить."
        else "Яндекс: официальный Yandex Cloud Translate. Нужен API-ключ сервисного аккаунта (роль ai.translate.user) и Folder ID.",
        color = cs.onSurfaceVariant, fontSize = 13.sp, modifier = Modifier.reveal().padding(horizontal = 8.dp),
    )
    if (p.trProvider == 1) {
        FieldRow("Yandex API-ключ", p.yandexKey, KeyboardType.Password, secret = true) { p.yandexKey = it }
        FieldRow("Folder ID", p.yandexFolder, KeyboardType.Ascii) { p.yandexFolder = it }
    }
    SectionLabel("Язык перевода по умолчанию")
    Row(Modifier.reveal().fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        TrLangs.forEachIndexed { i, l ->
            val on = p.trTarget == i
            PillButton(l.name, if (on) cs.primary else cs.secondaryContainer, if (on) cs.onPrimary else cs.onSecondaryContainer, height = 40.dp) {
                p.trTarget = i
            }
        }
    }

    SectionLabel("Голосовой ввод")
    SwitchRow(Icons.Default.Notifications, "Непрерывная диктовка", "После фразы микрофон слушает дальше, пока не нажмёшь стоп", p.voiceContinuous) { p.voiceContinuous = it }
    if (mic) {
        NavCard(Icons.Default.Check, "Микрофон разрешён", "Можно диктовать из клавиатуры") {}
    } else {
        NavCard(Icons.Default.Info, "Дать доступ к микрофону", "Нужен для голосового ввода") {
            ctx.startActivity(Intent(ctx, PermissionActivity::class.java))
        }
        NavCard(Icons.Default.Settings, "Настройки приложения", "Если окно разрешения не появляется") {
            ctx.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${ctx.packageName}")))
        }
    }

    SectionLabel("Буфер обмена")
    SwitchRow(Icons.Default.Edit, "История буфера", "Запоминает скопированный текст (пароли из менеджеров не сохраняются)", p.clipEnabled) { p.clipEnabled = it }
    SliderRow("Сколько хранить", p.clipMax, 10, 60, 5, "шт") { p.clipMax = it }
    NavCard(Icons.Default.Delete, "Очистить историю", "Удалит всё, включая закреплённое") { p.clipsJson = "[]" }
}

@Composable
private fun AiPage(p: Prefs) {
    val cs = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()
    var status by remember { mutableStateOf<String?>(null) }
    var testing by remember { mutableStateOf(false) }

    SwitchRow(dev.monetboard.ui.KbIcons.Spark, "ИИ в клавиатуре", "Меню ✦ → «Исправить» и «ИИ-режим»", p.aiEnabled) { p.aiEnabled = it }

    SectionLabel("Провайдер (любой OpenAI-совместимый)")
    Row(Modifier.reveal().fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        listOf(
            Triple("OpenAI", "https://api.openai.com/v1", "gpt-4o-mini"),
            Triple("OpenRouter", "https://openrouter.ai/api/v1", "openai/gpt-4o-mini"),
            Triple("DeepSeek", "https://api.deepseek.com/v1", "deepseek-chat"),
            Triple("Groq", "https://api.groq.com/openai/v1", "llama-3.3-70b-versatile"),
            Triple("Ollama", "http://127.0.0.1:11434/v1", "llama3.2"),
        ).forEach { (n, url, model) ->
            val on = p.aiBaseUrl == url
            PillButton(n, if (on) cs.primary else cs.secondaryContainer, if (on) cs.onPrimary else cs.onSecondaryContainer, height = 40.dp) {
                p.aiBaseUrl = url; p.aiModel = model
            }
        }
    }
    FieldRow("Base URL", p.aiBaseUrl, KeyboardType.Uri) { p.aiBaseUrl = it }
    FieldRow("API-ключ", p.aiApiKey, KeyboardType.Password, secret = true) { p.aiApiKey = it }
    FieldRow("Модель", p.aiModel, KeyboardType.Ascii) { p.aiModel = it }

    SectionLabel("ИИ-перевод: на какой язык")
    SegmentedRow(listOf("Авто", "Русский", "English"), p.aiTarget) { p.aiTarget = it }

    Column(
        Modifier.reveal().fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(cs.surfaceContainer).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text("Температура: ${"%.1f".format(p.aiTemperature)}", fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
        Slider(
            value = p.aiTemperature,
            onValueChange = { p.aiTemperature = (it * 10).roundToInt() / 10f },
            valueRange = 0f..1.5f, steps = 14,
        )
    }

    FieldRow("Системный промпт", p.aiSystemPrompt, KeyboardType.Text, singleLine = false) { p.aiSystemPrompt = it }
    PillButton("Вернуть промпт по умолчанию", cs.secondaryContainer, cs.onSecondaryContainer, Modifier.reveal(), height = 44.dp) {
        p.aiSystemPrompt = DEFAULT_SYSTEM_PROMPT
    }

    PillButton(if (testing) "Проверяю…" else "Проверить соединение", cs.primary, cs.onPrimary, Modifier.reveal().fillMaxWidth(), height = 52.dp) {
        if (testing) return@PillButton
        testing = true; status = null
        val cfg = p.aiConfig()
        scope.launch {
            val r = withContext(Dispatchers.IO) {
                runCatching { AiClient.chat(cfg, listOf(AiClient.Msg("user", "Reply with the single word: ok"))) }
            }
            testing = false
            status = r.fold({ "Работает: ${it.take(60)}" }, { "Ошибка: ${it.message}" })
        }
    }
    AnimatedVisibility(visible = status != null, enter = fadeIn() + slideInVertically { it / 2 }) {
        val s = status.orEmpty()
        Text(s, color = if (s.startsWith("Ошибка")) cs.error else cs.primary, fontSize = 14.sp)
    }
}

// ------------------------------------------------------------------ building blocks

@Composable
private fun SectionLabel(text: String) {
    Text(
        text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.reveal().padding(start = 8.dp, top = 10.dp),
    )
}

@Composable
private fun IconCookie(icon: ImageVector) {
    val cs = MaterialTheme.colorScheme
    Box(Modifier.size(46.dp).clip(CookieShape).background(cs.secondaryContainer), contentAlignment = Alignment.Center) {
        Icon(icon, null, tint = cs.onSecondaryContainer, modifier = Modifier.size(24.dp))
    }
}

@Composable
private fun NavCard(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    TapTile(Modifier.reveal().fillMaxWidth(), cs.surfaceContainer, 24.dp, onClick) {
        Row(
            Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            IconCookie(icon)
            Column(Modifier.weight(1f)) {
                Text(title, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = cs.onSurface)
                Text(subtitle, fontSize = 14.sp, color = cs.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun SwitchRow(icon: ImageVector, title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    val cs = MaterialTheme.colorScheme
    TapTile(Modifier.reveal().fillMaxWidth(), cs.surfaceContainer, 24.dp, { onChange(!checked) }) {
        Row(
            Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            IconCookie(icon)
            Column(Modifier.weight(1f)) {
                Text(title, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = cs.onSurface)
                Text(subtitle, fontSize = 14.sp, color = cs.onSurfaceVariant)
            }
            Switch(checked = checked, onCheckedChange = onChange)
        }
    }
}

@Composable
private fun SliderRow(title: String, value: Int, min: Int, max: Int, step: Int, unit: String, onChange: (Int) -> Unit) {
    val cs = MaterialTheme.colorScheme
    Column(
        Modifier.reveal().fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(cs.surfaceContainer).padding(16.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(title, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = cs.onSurface)
            Text("$value $unit", fontSize = 15.sp, color = cs.primary, fontWeight = FontWeight.Medium)
        }
        Slider(
            value = value.toFloat(),
            onValueChange = { onChange(((it - min) / step).roundToInt() * step + min) },
            valueRange = min.toFloat()..max.toFloat(),
            steps = (max - min) / step - 1,
        )
    }
}

@Composable
private fun SegmentedRow(options: List<String>, selected: Int, enabled: Boolean = true, onSelect: (Int) -> Unit) {
    val cs = MaterialTheme.colorScheme
    Row(Modifier.reveal().fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        options.forEachIndexed { i, o ->
            val sel = i == selected
            val r by animateDpAsState(if (sel) 26.dp else 14.dp, spring(0.6f, 420f), label = "seg")
            Box(
                Modifier.weight(1f).height(52.dp).clip(RoundedCornerShape(r))
                    .background(if (sel) cs.primary else cs.secondaryContainer)
                    .clickable(enabled = enabled) { onSelect(i) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    o, fontWeight = FontWeight.SemiBold, fontSize = 15.sp,
                    color = (if (sel) cs.onPrimary else cs.onSecondaryContainer).copy(alpha = if (enabled) 1f else 0.4f),
                )
            }
        }
    }
}

@Composable
private fun FieldRow(label: String, value: String, type: KeyboardType, secret: Boolean = false, singleLine: Boolean = true, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value, onValueChange = onChange,
        label = { Text(label) },
        singleLine = singleLine,
        visualTransformation = if (secret) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(keyboardType = type),
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.reveal().fillMaxWidth(),
    )
}

/** Mock of the live keyboard with a looping "typing" animation so theme/radius changes are visible right here. */
@Composable
private fun KeyboardPreview(p: Prefs) {
    val cs = MaterialTheme.colorScheme
    val r = p.keyRadius.dp
    val pos by rememberInfiniteTransition(label = "typing").animateFloat(
        0f, 6f, infiniteRepeatable(tween(4800, easing = LinearEasing), RepeatMode.Restart), label = "pos",
    )
    val active = pos.toInt().coerceIn(0, 5)
    Column(
        Modifier.reveal().fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(cs.surfaceContainer).padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            CookieButton(KbIcons.Spark, cs.primary, cs.onPrimary) {}
            PillButton("как", cs.secondaryContainer, cs.onSecondaryContainer, Modifier.weight(1f)) {}
            PillButton("привет", cs.primary, cs.onPrimary, Modifier.weight(1f)) {}
            PillButton("дела", cs.secondaryContainer, cs.onSecondaryContainer, Modifier.weight(1f)) {}
            CookieButton(KbIcons.Mic, cs.primary, cs.onPrimary) {}
        }
        Row(horizontalArrangement = Arrangement.spacedBy(p.keyGap.dp)) {
            "п р и в е т".split(" ").forEachIndexed { i, ch ->
                val hi = i == active
                val rad by animateDpAsState(if (hi) r + 12.dp else r, spring(0.5f, 500f), label = "pk$i")
                Box(
                    Modifier.weight(1f).height(p.keyHeight.dp).clip(RoundedCornerShape(rad))
                        .background(if (hi) cs.primaryContainer else cs.secondaryContainer),
                    contentAlignment = Alignment.Center,
                ) { Text(ch, fontSize = 22.sp, fontWeight = FontWeight.Medium, color = if (hi) cs.onPrimaryContainer else cs.onSecondaryContainer) }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(p.keyGap.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1.2f).height(p.keyHeight.dp).clip(RoundedCornerShape(r)).background(cs.primary), contentAlignment = Alignment.Center) {
                Icon(KbIcons.Shift, null, tint = cs.onPrimary, modifier = Modifier.size(24.dp))
            }
            Box(Modifier.weight(3f).height(p.keyHeight.dp).clip(RoundedCornerShape(r)).background(cs.surfaceContainerHighest), contentAlignment = Alignment.Center) {
                Row(
                    Modifier.height(32.dp).clip(RoundedCornerShape(16.dp)).background(cs.surfaceContainer),
                ) {
                    Box(Modifier.size(44.dp, 32.dp).clip(RoundedCornerShape(16.dp)).background(cs.primary), contentAlignment = Alignment.Center) {
                        Text("RU", color = cs.onPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }
                    Box(Modifier.size(44.dp, 32.dp), contentAlignment = Alignment.Center) {
                        Text("EN", color = cs.onSurfaceVariant, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
            Box(Modifier.weight(1f).height(p.keyHeight.dp).clip(RoundedCornerShape(r)).background(cs.tertiaryContainer), contentAlignment = Alignment.Center) {
                Icon(KbIcons.Backspace, null, tint = cs.onTertiaryContainer, modifier = Modifier.size(24.dp))
            }
            Box(Modifier.weight(1.3f).height(p.keyHeight.dp).clip(RoundedCornerShape(r)).background(cs.primary), contentAlignment = Alignment.Center) {
                Icon(KbIcons.Enter, null, tint = cs.onPrimary, modifier = Modifier.size(24.dp))
            }
        }
    }
}
