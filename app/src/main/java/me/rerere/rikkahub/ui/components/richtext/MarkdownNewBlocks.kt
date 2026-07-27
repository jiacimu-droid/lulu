package me.rerere.rikkahub.ui.components.richtext

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastForEach
import me.rerere.rikkahub.utils.toDp
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode

@Composable
internal fun HtmlStyledElement(
    element: Element,
    content: @Composable () -> Unit,
) {
    val baseTextStyle = LocalTextStyle.current
    val density = LocalDensity.current
    val elementStyle = remember(element.attr("style"), density, baseTextStyle) {
        element.attr("style").takeIf { it.isNotBlank() }?.let {
            parseBlockTextStyle(
                style = it,
                density = density,
                baseTextStyle = baseTextStyle,
            )
        }
    }
    if (elementStyle != null) {
        ProvideTextStyle(baseTextStyle.merge(elementStyle), content)
    } else {
        content()
    }
}

@Composable
internal fun HtmlBodyNode(node: Node, onClickCitation: (String) -> Unit) {
    when (node) {
        is Element -> HtmlBlockElement(element = node, onClickCitation = onClickCitation)
        is TextNode -> {
            val text = node.text().trim()
            if (text.isNotEmpty()) Text(text = text)
        }
    }
}

@Composable
internal fun HtmlBlockElement(
    element: Element,
    onClickCitation: (String) -> Unit,
    listLevel: Int = 0,
) {
    when (element.tagName().lowercase()) {
        "p" -> HtmlParagraph(
            element = element,
            onClickCitation = onClickCitation,
            modifier = if (element.nextElementSibling() != null) {
                Modifier.padding(bottom = LocalTextStyle.current.fontSize.toDp())
            } else {
                Modifier
            },
        )
        "h1", "h2", "h3", "h4", "h5", "h6" -> HtmlHeading(element, onClickCitation)
        "ul" -> HtmlList(
            element = element,
            ordered = false,
            onClickCitation = onClickCitation,
            level = listLevel,
        )
        "ol" -> HtmlList(
            element = element,
            ordered = true,
            onClickCitation = onClickCitation,
            level = listLevel,
        )
        "pre" -> HtmlCodeBlock(element)
        "blockquote" -> HtmlStyledElement(element) { HtmlBlockquote(element, onClickCitation) }
        "table" -> HtmlStyledElement(element) { HtmlTable(element, onClickCitation) }
        "hr" -> HorizontalDivider(
            modifier = Modifier.padding(vertical = 16.dp),
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
            thickness = 0.5.dp,
        )
        "img" -> {
            val src = element.attr("src")
            val alt = element.attr("alt")
            if (src.isNotEmpty()) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    ZoomableAsyncImage(
                        model = src,
                        contentDescription = alt.takeIf { it.isNotEmpty() },
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .widthIn(min = 120.dp)
                            .heightIn(min = 120.dp),
                    )
                }
            }
        }
        "span" -> {
            if (element.hasClass("math") && element.attr("inline") != "true") {
                HtmlMathBlock(element.text())
            } else {
                HtmlInlineGroup(listOf(element), onClickCitation)
            }
        }
        "details" -> HtmlStyledElement(element) { HtmlDetails(element, onClickCitation) }
        "progress" -> HtmlProgress(element)
        "div" -> HtmlStyledElement(element) {
            Column(modifier = Modifier.fillMaxWidth()) {
                element.childNodes().fastForEach { HtmlBodyNode(it, onClickCitation) }
            }
        }
        else -> HtmlStyledElement(element) {
            element.childNodes().forEach { HtmlBodyNode(it, onClickCitation) }
        }
    }
}
