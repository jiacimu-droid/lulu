package me.rerere.rikkahub.ui.components.richtext

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.TextUnitType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.fastForEach
import androidx.core.graphics.toColorInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.mapLatest
import org.intellij.markdown.flavours.gfm.GFMFlavourDescriptor
import org.intellij.markdown.html.HtmlGenerator
import org.intellij.markdown.parser.MarkdownParser
import org.jsoup.Jsoup

private val INLINE_LATEX_REGEX = Regex("\\\\\\((.+?)\\\\\\)")
private val BLOCK_LATEX_REGEX = Regex("\\\\\\[(.+?)\\\\\\]", RegexOption.DOT_MATCHES_ALL)
private val CODE_BLOCK_REGEX = Regex("```[\\s\\S]*?```|`[^`\n]*`", RegexOption.DOT_MATCHES_ALL)

private fun preProcess(content: String): String {
    val codeBlocks = mutableListOf<IntRange>()
    CODE_BLOCK_REGEX.findAll(content).forEach { codeBlocks.add(it.range) }
    fun isInCodeBlock(pos: Int) = codeBlocks.any { pos in it }
    var result = INLINE_LATEX_REGEX.replace(content) { match ->
        if (isInCodeBlock(match.range.first)) match.value else "$" + match.groupValues[1] + "$"
    }
    result = BLOCK_LATEX_REGEX.replace(result) { match ->
        if (isInCodeBlock(match.range.first)) match.value else "$$" + match.groupValues[1] + "$$"
    }
    return result
}

private val flavour by lazy {
    GFMFlavourDescriptor(makeHttpsAutoLinks = true, useSafeLinks = true)
}
private val parser by lazy { MarkdownParser(flavour) }

private fun generateMarkdownHtml(content: String): String {
    val preprocessed = preProcess(content)
    val tree = parser.buildMarkdownTreeFromString(preprocessed)
    return HtmlGenerator(preprocessed, tree, flavour).generateHtml()
}

@Composable
fun MarkdownNew(
    content: String,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    onClickCitation: (String) -> Unit = {},
) {
    var html by remember { mutableStateOf(generateMarkdownHtml(content)) }
    val updatedContent by rememberUpdatedState(content)
    LaunchedEffect(Unit) {
        snapshotFlow { updatedContent }
            .distinctUntilChanged()
            .mapLatest { generateMarkdownHtml(it) }
            .catch { it.printStackTrace() }
            .flowOn(Dispatchers.Default)
            .collect { html = it }
    }
    val document = remember(html) {
        runCatching { Jsoup.parse(html) }.getOrElse { Jsoup.parse("") }
    }
    ProvideTextStyle(style) {
        Column(modifier = modifier.padding(start = 4.dp)) {
            document.body().childNodes().fastForEach { node ->
                HtmlBodyNode(node = node, onClickCitation = onClickCitation)
            }
        }
    }
}

internal fun parseInlineSpanStyle(
    style: String,
    density: Density,
    baseFontSize: TextUnit,
): SpanStyle? {
    val properties = parseCssDeclarations(style)
    var hasStyle = false
    var spanStyle = SpanStyle()
    properties["color"]?.let { value ->
        parseColor(value)?.let {
            spanStyle = spanStyle.merge(SpanStyle(color = it))
            hasStyle = true
        }
    }
    properties["background-color"]?.let { value ->
        parseColor(value)?.let {
            spanStyle = spanStyle.merge(SpanStyle(background = it))
            hasStyle = true
        }
    }
    properties["font-weight"]?.let { value ->
        parseFontWeight(value)?.let {
            spanStyle = spanStyle.merge(SpanStyle(fontWeight = it))
            hasStyle = true
        }
    }
    properties["font-style"]?.let { value ->
        parseFontStyle(value)?.let {
            spanStyle = spanStyle.merge(SpanStyle(fontStyle = it))
            hasStyle = true
        }
    }
    properties["font-family"]?.let { value ->
        parseFontFamily(value)?.let {
            spanStyle = spanStyle.merge(SpanStyle(fontFamily = it))
            hasStyle = true
        }
    }
    properties["font-size"]?.let { value ->
        parseFontSize(value, density, baseFontSize)?.let {
            spanStyle = spanStyle.merge(SpanStyle(fontSize = it))
            hasStyle = true
        }
    }
    properties["letter-spacing"]?.let { value ->
        parseSpacing(value, density, baseFontSize)?.let {
            spanStyle = spanStyle.merge(SpanStyle(letterSpacing = it))
            hasStyle = true
        }
    }
    properties["text-decoration"]?.let { value ->
        parseTextDecoration(value)?.let {
            spanStyle = spanStyle.merge(SpanStyle(textDecoration = it))
            hasStyle = true
        }
    }
    val backgroundValue = properties["background-color"] ?: properties["background"]
    backgroundValue?.let { value ->
        parseColor(value)?.let {
            spanStyle = spanStyle.merge(SpanStyle(background = it))
            hasStyle = true
        }
    }
    return spanStyle.takeIf { hasStyle }
}

internal fun parseBlockTextStyle(
    style: String,
    density: Density,
    baseTextStyle: TextStyle,
): TextStyle? {
    val properties = parseCssDeclarations(style)
    val inlineStyle = parseInlineSpanStyle(style, density, baseTextStyle.fontSize)
    var hasStyle = inlineStyle != null
    var textStyle = TextStyle(
        color = inlineStyle?.color ?: Color.Unspecified,
        fontSize = inlineStyle?.fontSize ?: TextUnit.Unspecified,
        fontWeight = inlineStyle?.fontWeight,
        fontStyle = inlineStyle?.fontStyle,
        fontFamily = inlineStyle?.fontFamily,
        letterSpacing = inlineStyle?.letterSpacing ?: TextUnit.Unspecified,
        background = inlineStyle?.background ?: Color.Unspecified,
        textDecoration = inlineStyle?.textDecoration,
    )
    properties["line-height"]?.let { value ->
        parseLineHeight(value, density, baseTextStyle.fontSize)?.let {
            textStyle = textStyle.merge(TextStyle(lineHeight = it))
            hasStyle = true
        }
    }
    properties["text-align"]?.let { value ->
        parseTextAlign(value)?.let {
            textStyle = textStyle.merge(TextStyle(textAlign = it))
            hasStyle = true
        }
    }
    return textStyle.takeIf { hasStyle }
}

internal fun parseCssDeclarations(style: String): Map<String, String> = style
    .split(";")
    .mapNotNull { property ->
        val parts = property.split(":", limit = 2)
        if (parts.size == 2) parts[0].trim().lowercase() to parts[1].trim() else null
    }
    .toMap()

private fun parseFontSize(
    fontSize: String,
    density: Density,
    baseFontSize: TextUnit,
): TextUnit? {
    val normalized = fontSize.trim().lowercase()
    if (normalized.isEmpty()) return null
    fun scaleBase(multiplier: Float): TextUnit? {
        if (!baseFontSize.isSpecified) return null
        return when (baseFontSize.type) {
            TextUnitType.Sp -> (baseFontSize.value * multiplier).sp
            TextUnitType.Em -> (baseFontSize.value * multiplier).em
            else -> null
        }
    }
    val keywordScale = when (normalized) {
        "xx-small" -> 0.6f
        "x-small" -> 0.75f
        "small" -> 0.89f
        "medium" -> 1f
        "large" -> 1.2f
        "x-large" -> 1.5f
        "xx-large" -> 2f
        "smaller" -> 0.833f
        "larger" -> 1.2f
        else -> null
    }
    if (keywordScale != null) return scaleBase(keywordScale)
    return when {
        normalized.endsWith("sp") -> normalized.removeSuffix("sp").trim().toFloatOrNull()?.sp
        normalized.endsWith("px") -> normalized.removeSuffix("px").trim().toFloatOrNull()?.let {
            with(density) { it.toSp() }
        }
        normalized.endsWith("em") -> normalized.removeSuffix("em").trim().toFloatOrNull()?.em
        normalized.endsWith("rem") -> normalized.removeSuffix("rem").trim().toFloatOrNull()?.let {
            if (baseFontSize.isSpecified && baseFontSize.type == TextUnitType.Sp) {
                (baseFontSize.value * it).sp
            } else {
                16.sp * it
            }
        }
        normalized.endsWith("%") -> normalized.removeSuffix("%").trim().toFloatOrNull()?.let {
            scaleBase(it / 100f)
        }
        else -> normalized.toFloatOrNull()?.let { with(density) { it.toSp() } }
    }
}

private fun parseSpacing(
    spacing: String,
    density: Density,
    baseFontSize: TextUnit,
): TextUnit? {
    val normalized = spacing.trim().lowercase()
    if (normalized.isEmpty()) return null
    return when {
        normalized.endsWith("sp") -> normalized.removeSuffix("sp").trim().toFloatOrNull()?.sp
        normalized.endsWith("px") -> normalized.removeSuffix("px").trim().toFloatOrNull()?.let {
            with(density) { it.toSp() }
        }
        normalized.endsWith("em") -> normalized.removeSuffix("em").trim().toFloatOrNull()?.em
        normalized.endsWith("rem") -> normalized.removeSuffix("rem").trim().toFloatOrNull()?.let {
            if (baseFontSize.isSpecified && baseFontSize.type == TextUnitType.Sp) {
                (baseFontSize.value * it).sp
            } else {
                16.sp * it
            }
        }
        normalized.endsWith("%") -> normalized.removeSuffix("%").trim().toFloatOrNull()?.let {
            if (!baseFontSize.isSpecified) return@let null
            when (baseFontSize.type) {
                TextUnitType.Sp -> (baseFontSize.value * it / 100f).sp
                TextUnitType.Em -> (baseFontSize.value * it / 100f).em
                else -> null
            }
        }
        else -> normalized.toFloatOrNull()?.let { with(density) { it.toSp() } }
    }
}

private fun parseLineHeight(
    lineHeight: String,
    density: Density,
    baseFontSize: TextUnit,
): TextUnit? {
    val normalized = lineHeight.trim().lowercase()
    if (normalized.isEmpty()) return null
    if (normalized.matches(Regex("[0-9]*\\.?[0-9]+"))) {
        if (!baseFontSize.isSpecified) return null
        return when (baseFontSize.type) {
            TextUnitType.Sp -> (baseFontSize.value * normalized.toFloat()).sp
            TextUnitType.Em -> (baseFontSize.value * normalized.toFloat()).em
            else -> null
        }
    }
    return parseFontSize(normalized, density, baseFontSize)
}

internal fun parseLegacyFontSize(
    fontSize: String,
    density: Density,
    baseFontSize: TextUnit,
): TextUnit? {
    val normalized = fontSize.trim()
    val legacyScale = when (normalized) {
        "1" -> 0.625f
        "2" -> 0.8125f
        "3" -> 1f
        "4" -> 1.125f
        "5" -> 1.5f
        "6" -> 2f
        "7" -> 3f
        else -> null
    }
    if (legacyScale != null) {
        return parseFontSize(
            fontSize = "${legacyScale * 100}%",
            density = density,
            baseFontSize = if (baseFontSize.isSpecified) baseFontSize else 16.sp,
        )
    }
    if ((normalized.startsWith("+") || normalized.startsWith("-")) && baseFontSize.isSpecified) {
        val delta = normalized.toIntOrNull() ?: return null
        return parseLegacyFontSize(
            fontSize = (3 + delta).coerceIn(1, 7).toString(),
            density = density,
            baseFontSize = baseFontSize,
        )
    }
    return parseFontSize(normalized, density, baseFontSize)
}

private fun parseFontFamily(fontFamily: String): FontFamily? {
    val normalized = fontFamily
        .split(",")
        .map { it.trim().trim('"', '\'').lowercase() }
        .firstOrNull()
        ?: return null
    return when {
        normalized.contains("mono") || normalized.contains("courier") -> FontFamily.Monospace
        normalized.contains("serif") || normalized.contains("georgia") || normalized.contains("times") -> FontFamily.Serif
        normalized.contains("sans") || normalized.contains("arial") || normalized.contains("helvetica") -> FontFamily.SansSerif
        normalized.contains("cursive") -> FontFamily.Cursive
        else -> null
    }
}

internal fun parseColor(colorString: String): Color? = try {
    when {
        colorString.startsWith("#") -> {
            val hex = colorString.removePrefix("#")
            when (hex.length) {
                6 -> Color("#$hex".toColorInt())
                3 -> {
                    val red = hex[0].toString().repeat(2)
                    val green = hex[1].toString().repeat(2)
                    val blue = hex[2].toString().repeat(2)
                    Color("#$red$green$blue".toColorInt())
                }
                else -> null
            }
        }
        colorString.startsWith("rgb(") -> {
            val values = colorString.removePrefix("rgb(").removeSuffix(")")
                .split(",").map { it.trim().toIntOrNull() }
            if (values.size == 3 && values.all { it != null && it in 0..255 }) {
                Color(values[0]!!, values[1]!!, values[2]!!)
            } else {
                null
            }
        }
        colorString.startsWith("rgba(") -> {
            val values = colorString.removePrefix("rgba(").removeSuffix(")")
                .split(",").map { it.trim() }
            if (values.size == 4) {
                val red = values[0].toIntOrNull()
                val green = values[1].toIntOrNull()
                val blue = values[2].toIntOrNull()
                val alpha = values[3].toFloatOrNull()
                if (
                    red != null && green != null && blue != null && alpha != null &&
                    red in 0..255 && green in 0..255 && blue in 0..255 && alpha in 0f..1f
                ) {
                    Color(red, green, blue, (alpha * 255).toInt())
                } else {
                    null
                }
            } else {
                null
            }
        }
        else -> when (colorString.lowercase()) {
            "red" -> Color.Red
            "green" -> Color.Green
            "blue" -> Color.Blue
            "black" -> Color.Black
            "white" -> Color.White
            "gray", "grey" -> Color.Gray
            "yellow" -> Color.Yellow
            "cyan" -> Color.Cyan
            "magenta" -> Color.Magenta
            "orange" -> Color(0xFFFFA500)
            "purple" -> Color(0xFF800080)
            "brown" -> Color(0xFFA52A2A)
            "pink" -> Color(0xFFFFC0CB)
            else -> null
        }
    }
} catch (_: Exception) {
    null
}

private fun parseFontWeight(weightString: String): FontWeight? = when (weightString.lowercase()) {
    "normal" -> FontWeight.Normal
    "bold" -> FontWeight.SemiBold
    "bolder" -> FontWeight.ExtraBold
    "lighter" -> FontWeight.Light
    "100" -> FontWeight.W100
    "200" -> FontWeight.W200
    "300" -> FontWeight.W300
    "400" -> FontWeight.W400
    "500" -> FontWeight.W500
    "600" -> FontWeight.W600
    "700" -> FontWeight.W700
    "800" -> FontWeight.W800
    "900" -> FontWeight.W900
    else -> null
}

private fun parseFontStyle(fontStyle: String): FontStyle? = when (fontStyle.lowercase()) {
    "italic", "oblique" -> FontStyle.Italic
    "normal" -> FontStyle.Normal
    else -> null
}

private fun parseTextDecoration(textDecoration: String): TextDecoration? {
    val parts = textDecoration.lowercase().split(Regex("\\s+")).filter { it.isNotBlank() }
    if (parts.isEmpty()) return null
    val decorations = buildList {
        if ("underline" in parts) add(TextDecoration.Underline)
        if ("line-through" in parts) add(TextDecoration.LineThrough)
    }
    return when (decorations.size) {
        0 -> null
        1 -> decorations.first()
        else -> TextDecoration.combine(decorations)
    }
}

private fun parseTextAlign(textAlign: String): TextAlign? = when (textAlign.trim().lowercase()) {
    "left", "start" -> TextAlign.Start
    "right", "end" -> TextAlign.End
    "center" -> TextAlign.Center
    "justify" -> TextAlign.Justify
    else -> null
}
