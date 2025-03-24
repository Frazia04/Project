require.config({paths: {'vs': window.contextPath + 'webjars/monaco-editor/0.10.1/min/vs'}});
require(['vs/editor/editor.main'], function () {

    function numberOfLines(str) {
        return str.split(/\r\n|\r|\n/).length
    }

    $(".compare-results").each(function (inex, cr) {
        var btn = $('<button>')
            .addClass('btn')
            .addClass('btn-default')
            .addClass('btn-xs')
            .css('float', 'right')
            .text('Differenz anzeigen.');
        $(cr).find("th").last().append(btn);

        btn.click(function(e) {


            var left = $(cr).find(".compare-results-left").text();
            var right = $(cr).find(".compare-results-right").text();

            var maxLines = Math.max(numberOfLines(left), numberOfLines(right));
            var height = Math.min(600, Math.max(40, maxLines * 20));

            var div = $("<div>").css('height', height + 'px');
            var outerDiv = $("<div>")
                .append("<p>Erwartetes Ergebnis (links) / Tatsächliches Ergebnis (rechts):</p> ")
                .append(div);
            $(cr).replaceWith(outerDiv);

            var diffEditor = monaco.editor.createDiffEditor(div[0], {
                // enableSplitViewResizing: false,
                renderControlCharacters: true,
                renderWhitespace: "boundary", // or "all"
                minimap: {
                    enabled: false
                },
                readOnly: true,
                scrollBeyondLastLine: false,
                ignoreTrimWhitespace: false
            });

            diffEditor.setModel({
                original: monaco.editor.createModel(left),
                modified: monaco.editor.createModel(right)
            });
        });
    });
});