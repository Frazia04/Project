package de.rptu.cs.exclaim.data;

import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

@SuppressWarnings("NullAway")
@NoArgsConstructor
@AllArgsConstructor
public class MDAnnotation {
    private int line;
    private String text;
    private String markdown;

    public int getLine() {
        return line;
    }

    public void setLine(int line) {
        this.line = line;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public String getMarkdown() {
        return markdown;
    }

    public void setMarkdown(String markdown) {
        this.markdown = markdown;
    }
}
