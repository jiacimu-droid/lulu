package me.rerere.rikkahub.ui.components.richtext

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastForEach
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Tick01
import me.rerere.rikkahub.ui.components.table.DataTable
import me.rerere.rikkahub.ui.context.LocalSettings
import me.rerere.rikkahub.utils.toDp
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node

@Composable
internal fun HtmlParagraph(
    element: Element,
    onClickCitation: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val baseTextStyle = LocalTextStyle.current
    val density = LocalDensity.current
    val paragraphStyle = remember(element.attr("style"), density, baseTextStyle) {
        element.attr("style").takeIf { it.isNotBlank() }?.let {
            parseBlockTextStyle(style = it, density = density, baseTextStyle = baseTextStyle)
        }
    }
    if (paragraphStyle != null) {
        ProvideTextStyle(baseTextStyle.merge(paragraphStyle)) {
            HtmlParagraphContent(element, onClickCitation, density, modifier)
        }
    } else {
        HtmlParagraphContent(element, onClickCitation, density, modifier)
    }
}

@Composable
private fun HtmlParagraphContent(
    element: Element,
    onClickCitation: (String) -> Unit,
    density: Density,
    modifier: Modifier = Modifier,
) {
    val hasImages = element.select("img").isNotEmpty()
    val hasBlockMath = element.select("span.math").any { it.attr("inline") != "true" }
    if (hasImages || hasBlockMath) {
        FlowRow(
            modifier = modifier.fillMaxWidth(),
            itemVerticalAlignment = Alignment.CenterVertically,
        ) {
            element.childNodes().fastForEach { child -> HtmlInlineAsComposable(child, onClickCitation) }
        }
        return
    }
    val enableLatexRendering = LocalSettings.current.displaySetting.enableLatexRendering
    val hasInlineMath = element.select("span.math").any { it.attr("inline") == "true" }
    val colorScheme = MaterialTheme.colorScheme
    val textStyle = LocalTextStyle.current
    val (annotatedString, inlineContents) = remember(
        element.outerHtml(),
        enableLatexRendering,
        colorScheme,
        density,
        textStyle,
        onClickCitation,
    ) {
        val contents = mutableMapOf<String, InlineTextContent>()
        val text = buildAnnotatedString {
            element.childNodes().forEach { child ->
                appendHtmlInlineNode(
                    node = child,
                    colorScheme = colorScheme,
                    inlineContents = contents,
                    density = density,
                    style = textStyle,
                    enableLatexRendering = enableLatexRendering,
                    onClickCitation = onClickCitation,
                )
            }
        }
        text to contents
    }
    Text(
        text = annotatedString,
        inlineContent = inlineContents,
        softWrap = true,
        overflow = TextOverflow.Visible,
        modifier = modifier.fillMaxWidth(),
        style = textStyle.copy(
            lineHeight = if (hasInlineMath && enableLatexRendering) TextUnit.Unspecified else textStyle.lineHeight,
        ),
    )
}

@Composable
internal fun HtmlHeading(element: Element, onClickCitation: (String) -> Unit) {
    val level = element.tagName().removePrefix("h").toIntOrNull() ?: 1
    val headingStyle = when (level) {
        1 -> HeaderStyle.H1
        2 -> HeaderStyle.H2
        3 -> HeaderStyle.H3
        4 -> HeaderStyle.H4
        5 -> HeaderStyle.H5
        else -> HeaderStyle.H6
    }
    val verticalPadding = when (level) {
        1 -> 16.dp
        2 -> 14.dp
        3 -> 12.dp
        4 -> 10.dp
        5 -> 8.dp
        else -> 6.dp
    }
    ProvideTextStyle(LocalTextStyle.current.merge(headingStyle)) {
        Box(modifier = Modifier.padding(vertical = verticalPadding)) {
            HtmlParagraph(element, onClickCitation)
        }
    }
}

@Composable
internal fun HtmlList(
    element: Element,
    ordered: Boolean,
    onClickCitation: (String) -> Unit,
    level: Int,
) {
    HtmlStyledElement(element) {
        Column(modifier = Modifier.padding(start = (level * 8).dp, top = 4.dp, bottom = 4.dp)) {
            val bulletBase = when (level % 3) {
                0 -> "•"
                1 -> "◦"
                else -> "▪"
            }
            var orderedIndex = 1
            element.children().fastForEach { item ->
                if (item.tagName().lowercase() == "li") {
                    HtmlListItem(
                        item = item,
                        bulletText = if (ordered) "${orderedIndex++}. " else "$bulletBase ",
                        onClickCitation = onClickCitation,
                        level = level,
                    )
                }
            }
        }
    }
}

@Composable
private fun HtmlListItem(
    item: Element,
    bulletText: String,
    onClickCitation: (String) -> Unit,
    level: Int,
) {
    val isTaskItem = item.hasClass("task-list-item")
    val checkboxInput = item.selectFirst("input[type=checkbox]")
    val isChecked = checkboxInput?.hasAttr("checked") == true
    HtmlStyledElement(item) {
        Column {
            Row(verticalAlignment = Alignment.Top, modifier = Modifier.padding(vertical = 2.dp)) {
                if (isTaskItem && checkboxInput != null) {
                    Surface(
                        shape = RoundedCornerShape(2.dp),
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                        modifier = Modifier.padding(end = 4.dp, top = 2.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .padding(2.dp)
                                .size(LocalTextStyle.current.fontSize.toDp() * 0.8f),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (isChecked) {
                                Icon(HugeIcons.Tick01, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                } else {
                    Text(
                        text = bulletText,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.alignByBaseline(),
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    val directContentNodes = item.childNodes().filter { node ->
                        !(node is Element &&
                            (node.tagName().lowercase() in listOf("ul", "ol") ||
                                (node.tagName().lowercase() == "input" && node.attr("type") == "checkbox")))
                    }
                    val groups = mutableListOf<MutableList<Node>>()
                    directContentNodes.fastForEach { node ->
                        if (node is Element && node.tagName().lowercase() == "p") {
                            groups.add(mutableListOf(node))
                        } else {
                            val last = groups.lastOrNull()
                            if (last != null && last.none { it is Element && it.tagName().lowercase() == "p" }) {
                                last.add(node)
                            } else {
                                groups.add(mutableListOf(node))
                            }
                        }
                    }
                    groups.fastForEach { group ->
                        val first = group.firstOrNull()
                        if (first is Element && first.tagName().lowercase() == "p") {
                            HtmlParagraph(first, onClickCitation)
                        } else {
                            HtmlInlineGroup(group, onClickCitation)
                        }
                    }
                }
            }
            item.children().fastForEach { child ->
                val tag = child.tagName().lowercase()
                if (tag == "ul" || tag == "ol") {
                    HtmlList(child, tag == "ol", onClickCitation, level + 1)
                }
            }
        }
    }
}

@Composable
internal fun HtmlCodeBlock(element: Element) {
    val codeElement = element.selectFirst("code")
    val language = codeElement?.classNames()
        ?.find { it.startsWith("language-") }
        ?.removePrefix("language-")
        ?: "plaintext"
    val code = codeElement?.wholeText()?.trimEnd('\n') ?: element.wholeText().trimEnd('\n')
    HighlightCodeBlock(
        code = code,
        language = language,
        modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
        completeCodeBlock = true,
    )
}

@Composable
internal fun HtmlBlockquote(element: Element, onClickCitation: (String) -> Unit) {
    ProvideTextStyle(LocalTextStyle.current.copy(fontStyle = FontStyle.Italic)) {
        val borderColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
        val bgColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
        Column(
            modifier = Modifier
                .drawWithContent {
                    drawContent()
                    drawRect(color = bgColor, size = size)
                    drawRect(color = borderColor, size = Size(10f, size.height))
                }
                .padding(8.dp),
        ) {
            element.childNodes().fastForEach { HtmlBodyNode(it, onClickCitation) }
        }
    }
}

@Composable
internal fun HtmlMathBlock(formula: String) {
    val enableLatexRendering = LocalSettings.current.displaySetting.enableLatexRendering
    if (enableLatexRendering) {
        MathBlock(latex = formula, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp))
    } else {
        Text(
            text = formula,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        )
    }
}

@Composable
internal fun HtmlTable(element: Element, onClickCitation: (String) -> Unit) {
    val headerElements = element.select("thead tr th")
    val columnCount = headerElements.size.takeIf { it > 0 }
        ?: element.select("tbody tr:first-child td").size
    if (columnCount == 0) return
    val headers = List(columnCount) { column ->
        @Composable {
            if (column < headerElements.size) {
                HtmlStyledElement(headerElements[column]) {
                    HtmlInlineGroup(headerElements[column].childNodes(), onClickCitation)
                }
            }
        }
    }
    val rows = element.select("tbody tr").map { row ->
        val cells = row.select("td")
        List(columnCount) { column ->
            @Composable {
                if (column < cells.size) {
                    HtmlStyledElement(cells[column]) {
                        HtmlInlineGroup(cells[column].childNodes(), onClickCitation)
                    }
                }
            }
        }
    }
    DataTable(
        headers = headers,
        rows = rows,
        modifier = Modifier.padding(vertical = 8.dp),
        columnMinWidths = List(columnCount) { 80.dp },
        columnMaxWidths = List(columnCount) { 200.dp },
    )
}

@Composable
internal fun HtmlDetails(element: Element, onClickCitation: (String) -> Unit) {
    val summaryElement = element.children().find { it.tagName().lowercase() == "summary" }
    val summaryText = summaryElement?.text() ?: "Details"
    var expanded by remember { mutableStateOf(element.hasAttr("open")) }
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded }.padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(if (expanded) "▼ " else "▶ ")
            Text(summaryText, fontWeight = FontWeight.Medium)
        }
        if (expanded) {
            Column(modifier = Modifier.padding(start = 16.dp)) {
                element.childNodes().fastForEach { child ->
                    if (!(child is Element && child.tagName().lowercase() == "summary")) {
                        HtmlBodyNode(child, onClickCitation)
                    }
                }
            }
        }
    }
}

@Composable
internal fun HtmlProgress(element: Element) {
    val value = element.attr("value").toFloatOrNull() ?: 0f
    val max = element.attr("max").toFloatOrNull()?.takeIf { it > 0 } ?: 100f
    val progress = (value / max).coerceIn(0f, 1f)
    val widthValue = parseCssDeclarations(element.attr("style"))["width"] ?: element.attr("width")
    val widthModifier = when {
        widthValue.endsWith("%") -> widthValue.removeSuffix("%").toFloatOrNull()
            ?.let { Modifier.fillMaxWidth(it / 100f) } ?: Modifier.fillMaxWidth()
        widthValue.endsWith("px") -> widthValue.removeSuffix("px").toIntOrNull()
            ?.let { Modifier.width(it.dp) } ?: Modifier.fillMaxWidth()
        widthValue.isNotEmpty() -> widthValue.toIntOrNull()
            ?.let { Modifier.width(it.dp) } ?: Modifier.fillMaxWidth()
        else -> Modifier.fillMaxWidth()
    }
    androidx.compose.material3.LinearProgressIndicator(
        progress = { progress },
        modifier = widthModifier.padding(vertical = 4.dp),
    )
}
