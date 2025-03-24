package de.rptu.cs.exclaim.utils;

import com.vladsch.flexmark.ast.Node;
import com.vladsch.flexmark.html.HtmlRenderer;
import com.vladsch.flexmark.parser.Parser;
import com.vladsch.flexmark.parser.ParserEmulationProfile;
import com.vladsch.flexmark.util.options.MutableDataSet;

public class Markdown {
    public static String toHtml(String markdownString) {
        markdownString = markdownString.replaceAll("<br[ /]*>", " \n");
        markdownString = markdownString.replaceAll("<[ /]*code>", "`");
        MutableDataSet options = new MutableDataSet();
        options.setFrom(ParserEmulationProfile.COMMONMARK_0_28);
        options.set(Parser.HEADING_NO_ATX_SPACE, true);
        Parser parser = Parser.builder(options).build();
        Node document = parser.parse(markdownString);
        HtmlRenderer renderer = HtmlRenderer.builder()
            .escapeHtml(true)
            .build();
        return renderer.render(document);
    }
}
