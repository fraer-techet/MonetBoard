package dev.monetboard.ui

import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateDpAsState
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.monetboard.ai.AiClient
import dev.monetboard.data.DEFAULT_SYSTEM_PROMPT
import dev.monetboard.data.Prefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

enum class Screen(val title: String) {
    HOME("Monet Board"), COLORS("Цвета и фон"), KEYBOARD("Раскладка и отклик"),
    TEXT("Ввод текста"), AI("ИИ-агент"),
}

fun isImeEnabled(ctx: Context): Boolean =
    ctx.getSystemService(InputMethodManager::class.java).enabledInputMethodList.any { it.packageName == ctx.packageName }

fun isImeSelected(ctx: Context): Boolean {
    val id = Settings.Secure.getString(ctx.contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD) ?: return false
    return id.startsWith(ctx.packageName + "/")
}

@Composable
fun SettingsApp(prefs: Prefs, tick: Int) {
    var screenName by rememberSaveable { mutableStateOf(Screen.HOME.name) }
    val screen = Screen.valueOf(screenName)
    BackHandler(enabled = screen != Screen.HOME) { screenName = Screen.HOME.name }
    val cs = MaterialTheme.colorScheme

    Column(Modifier.fillMaxSize().safeDrawingPadding()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (screen != Screen.HOME) {
                Box(
                    Modifier.size(48.dp).clip(CircleShape).background(cs.primaryContainer)
                        .clickable { screenName = Screen.HOME.name },
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = cs.onPrimaryContainer) }
            }
            Text(screen.title, fontSize = 30.sp, fontWeight = FontWeight.Bold, color = cs.onBackground)
        }
        TestField()
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            when (screen) {
                Screen.HOME -> HomePage(tick) { screenName = it.name }
                Screen.COLORS -> ColorsPage(prefs)
                Screen.KEYBOARD -> KeyboardPage(prefs)
                Screen.TEXT -> TextPage(prefs)
                Screen.AI -> AiPage(prefs)
            }
            Spacer(Modifier.height(24.dp))
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
            Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(cs.primaryContainer).padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            CookieButton(Icons.Default.Check, cs.primary, cs.onPrimary, size = 56.dp) {}
            Column {
                Text("Всё готово", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = cs.onPrimaryContainer)
                Text("Клавиатура включена и выбрана", color = cs.onPrimaryContainer)
            }
        }
    } else {
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(cs.surfaceContainer).padding(18.dp),
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
    NavCard(Icons.Default.Menu, "Раскладка и отклик", "Размер клавиш, форма, вибрация, ряд цифр") { go(Screen.KEYBOARD) }
    NavCard(Icons.Default.Edit, "Ввод текста", "Автоисправление, подсказки, знаки, заглавные") { go(Screen.TEXT) }
    NavCard(KbIcons.Spark, "ИИ-агент", "Ключ, модель, провайдер, промпт") { go(Screen.AI) }
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
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Accents.forEachIndexed { i, a ->
                Box(
                    Modifier.weight(1f).aspectRatio(1f).clip(CircleShape).background(accentSwatch(a.hue))
                        .clickable { p.accent = i },
                    contentAlignment = Alignment.Center,
                ) { if (p.accent == i) Icon(Icons.Default.Check, null, tint = androidx.compose.ui.graphics.Color.Black.copy(alpha = .7f)) }
            }
        }
    }

    SwitchRow(Icons.Default.Face, "AMOLED чёрный", "Чисто чёрный фон в тёмной теме", p.amoled) { p.amoled = it }

    SectionLabel("Предпросмотр")
    KeyboardPreview(p)
}

@Composable
private fun KeyboardPage(p: Prefs) {
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
        Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(MaterialTheme.colorScheme.surfaceContainer).padding(16.dp),
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
private fun AiPage(p: Prefs) {
    val cs = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()
    var status by remember { mutableStateOf<String?>(null) }
    var testing by remember { mutableStateOf(false) }

    SwitchRow(KbIcons.Spark, "ИИ в клавиатуре", "Кнопка ✦ слева от подсказок", p.aiEnabled) { p.aiEnabled = it }

    SectionLabel("Провайдер (любой OpenAI-совместимый)")
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
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

    SectionLabel("Перевод: на какой язык")
    SegmentedRow(listOf("Авто", "Русский", "English"), p.aiTarget) { p.aiTarget = it }

    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(cs.surfaceContainer).padding(16.dp),
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
    PillButton("Вернуть промпт по умолчанию", cs.secondaryContainer, cs.onSecondaryContainer, height = 44.dp) {
        p.aiSystemPrompt = DEFAULT_SYSTEM_PROMPT
    }

    PillButton(if (testing) "Проверяю…" else "Проверить соединение", cs.primary, cs.onPrimary, Modifier.fillMaxWidth(), height = 52.dp) {
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
    status?.let { Text(it, color = if (it.startsWith("Ошибка")) cs.error else cs.primary, fontSize = 14.sp) }
}

// ------------------------------------------------------------------ building blocks

@Composable
private fun SectionLabel(text: String) {
    Text(text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 8.dp, top = 10.dp))
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
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(cs.surfaceContainer)
            .clickable(onClick = onClick).padding(16.dp),
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

@Composable
private fun SwitchRow(icon: ImageVector, title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    val cs = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(cs.surfaceContainer)
            .clickable { onChange(!checked) }.padding(16.dp),
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

@Composable
private fun SliderRow(title: String, value: Int, min: Int, max: Int, step: Int, unit: String, onChange: (Int) -> Unit) {
    val cs = MaterialTheme.colorScheme
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(cs.surfaceContainer).padding(16.dp),
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
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        options.forEachIndexed { i, o ->
            val sel = i == selected
            val r by animateDpAsState(if (sel) 26.dp else 14.dp, label = "seg")
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
        visualTransformation = if (secret) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
        keyboardOptions = KeyboardOptions(keyboardType = type),
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth(),
    )
}

/** Static mock of the live keyboard so theme/radius changes are visible without leaving the page. */
@Composable
private fun KeyboardPreview(p: Prefs) {
    val cs = MaterialTheme.colorScheme
    val r = p.keyRadius.dp
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(cs.surfaceContainer).padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            CookieButton(KbIcons.Spark, cs.primary, cs.onPrimary) {}
            PillButton("как", cs.secondaryContainer, cs.onSecondaryContainer, Modifier.weight(1f)) {}
            PillButton("привет", cs.primary, cs.onPrimary, Modifier.weight(1f)) {}
            PillButton("дела", cs.secondaryContainer, cs.onSecondaryContainer, Modifier.weight(1f)) {}
            CookieButton(Icons.Default.Settings, cs.primary, cs.onPrimary) {}
        }
        Row(horizontalArrangement = Arrangement.spacedBy(p.keyGap.dp)) {
            "п р и в е т".split(" ").forEach { ch ->
                val hi = ch == "и"
                Box(
                    Modifier.weight(1f).height(p.keyHeight.dp).clip(RoundedCornerShape(if (hi) r + 12.dp else r))
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
                Text("Русский", color = cs.onSurfaceVariant, fontSize = 14.sp)
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
