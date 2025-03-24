function reloadAssignmentInfo(assignmentPanel) {
    var sheetid = assignmentPanel.attr('data-sheetid');
    var aid = assignmentPanel.attr('data-assignmentid');
    var exid = assignmentPanel.attr('data-exerciseid');
    var group = assignmentPanel.attr('data-group');
    var team = assignmentPanel.attr('data-team');
    var assignmentRow = assignmentPanel.closest(".assignment-row");

    $.get(contextPath + "exercise/"+exid+"/sheet/"+sheetid+"/fragment/" + aid + "/" + group + "/" + team , function (data) {
        var newBody = $(data);
        // replace only some parts, not the dropzone itself
        //assignmentRow.find(".uploads-box").replaceWith(newBody.find(".uploads-box"));
        assignmentRow.find(".test-box").replaceWith(newBody.find(".test-box"));
        //assignmentRow.find(".papierkorb-box").replaceWith(newBody.find(".papierkorb-box"));
        assignmentRow.find(".scrollableCustom").replaceWith(newBody.find(".scrollableCustom"));
    });
}

$(function () {
    // initialize dropzone.js:
    $(".assignment-panel").each(function (i, elem) {
        var jelem = $(elem);
        var aid = jelem.attr('data-assignmentid');
        var dropzoneId = jelem.find(".dropzone").attr('id');

        if (dropzoneId) Dropzone.options[dropzoneId] = {
            dictDefaultMessage: (jelem.find(".dropzone").attr('class').indexOf("customAdmin") > -1) ? "Hochladen" : "Dateien für Aufgabe " + aid + " hier ablegen!",
            init: function() {
                var callback = function () {
                    reloadAssignmentInfo(jelem);
                };
                this.on("complete", callback);
            }
        };

    });


    $('.attendanceTableForGroup').each(function (i, groupTable) {
        var $groupTable = $(groupTable);
        var markBtn = $('<button>', {class: 'btn btn-default btn-sm', type: 'button'});
        markBtn
            .text('Alle markieren!')
            .css('margin-left', '15px')
            .appendTo($groupTable.find('th').last())
            .click(function () {
                $groupTable.find("input[type='checkbox']").prop('checked', true);
            });
    });

});

function insertLink(area_id, url) {
    var text_area_element = document.getElementById(area_id);
    var caret_pos = text_area_element.selectionStart;
    var text_area = jQuery("#" + area_id);
    var current_text = text_area.val();
    var text = "[](" + url +  ")";
    text_area.val(current_text.substring(0, caret_pos) + text + current_text.substring(caret_pos));
    text_area.focus();
    text_area_element.selectionStart = (caret_pos + (1));
    text_area_element.selectionEnd = (caret_pos + (1));
    return false;
};

$(".btn-toggle").on("click", function() {
    if ($(this).text() == "Alle schließen") {
        $(this).closest('.container').children('.panel').children('.collapse').collapse('hide');
        $(this).text("Öffne alle");
    } else {
        $(this).closest('.container').children('.panel').children('.collapse').collapse('show');
        $(this).text("Alle schließen");
    }
});