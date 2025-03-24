window.exports = window.exports || {};
window.exports.afterPrettify = function () {
 $(function () {
  $(".annotate-me").each(function(am_index, am) {
    $am = $(am);


    // data passed in by special html element
    var passedData = $am.find(".annotation-data");
    var exclaimContext = passedData.attr('data-exclaimContext');
    var csrfToken = passedData.attr('data-csrf');
    var hasEditRight = passedData.attr('data-hasEditRight') === "true";
    var exerciseId = passedData.attr('data-exerciseId');
    var sheetId = passedData.attr('data-sheetId');
    var assignmentId = passedData.attr('data-assignmentId');
    var groupId = passedData.attr('data-groupId');
    var teamId = passedData.attr('data-teamId');
    var fileid = passedData.attr('data-fileid');


    var codeTable = $am.find(".code-snippet");
    var lines = $am.find(".code-snippet > ol > li");

    var warningsExtended = false;

    lines.each(function (i, line) {
        var lineNr = i + 1;
        addCommentBtn(lineNr, line);
    });
    loadComments();

    function addCommentBtn(lineNr, line) {
        if (!hasEditRight) {
            return;
        }
        var commentBtn = $('<span class="add-comment-btn" >+</span>');
        commentBtn.prependTo(line);
        commentBtn.click(function (e) {
            addEditBox(lineNr);
        });
    }

    function removeCommentBtn(line) {
        $(line).find(".add-comment-btn").remove();
    }

      function addWarningBtn(line) {
          var warningBtn = $('<span class="warning-icon glyphicon glyphicon-warning-sign" ></span>');
          warningBtn.prependTo(line);
          warningBtn.click(function () {
              var warningBox = $(line).find(".warningbox");
              if (warningBox != null) {
                  if (warningBox.css("display") === "none") {
                      warningBox.css("display", "block");
                  } else {
                      warningBox.css("display", "none");
                  }
              }
          });
          // warningBtn.hover(function (e) {
          //     var warningBox = $(line).find(".warningbox");
          //     warningBox.css("display", "block");
          // }, function (e) {
          //     var warningBox = $(line).find(".warningbox");
          //     warningBox.css("display", "none");
          // });
      }

      function removeWarningBtn(line) {
          $(line).find(".warning-icon").remove();
      }

    function addCollapseWarningsButton() {
        var allWarningsButton = $("<button type='button' class='btn btn-default warnings-button'>Alle Warnungen anzeigen</button>");
        codeTable.append(allWarningsButton);
        allWarningsButton.click(function () {
            warningsExtended = !warningsExtended;
            if (warningsExtended) {
                codeTable.find(".warningbox").css("display", "block");
                allWarningsButton.text("Alle Warnungen verbergen");
            } else {
                codeTable.find(".warningbox").css("display", "none");
                allWarningsButton.text("Alle Warnungen anzeigen");
            }
        })
    }

    function loadComments() {
        //clearWarnings();
        $.get(exclaimContext
          + "exercise/" + encodeURIComponent(exerciseId)
          + "/sheet/" + encodeURIComponent(sheetId)
          + "/assignment/" + encodeURIComponent(assignmentId)
          + "/team/" + encodeURIComponent(groupId) + "/" + encodeURIComponent(teamId)
          + "/" + encodeURIComponent(fileid) + "/annotations")
        .fail(function() {
            alert("Kommentare konnten nicht geladen werden.");
        })
        .done(function (resp) {
            resp.forEach(function (comment) {
                console.log(comment);
                addAnnotationBox(comment.line, comment);
            });
        });
        /*$.get(exclaimContext
            + "exercise/" + encodeURIComponent(exerciseId)
            + "/sheet/" + encodeURIComponent(sheetId)
            + "/assignment/" + encodeURIComponent(assignmentId)
            + "/team/" + encodeURIComponent(groupId) + "/" + encodeURIComponent(teamId)
            + "/" + encodeURIComponent(fileid) + "/warnings")
            .fail(function() {
                alert("Kommentare konnten nicht geladen werden.");
            })
            .done(function (resp) {
                resp.forEach(function (comment) {
                    console.log(comment);
                    addWarningBox(comment.line, comment);
                });

                if (resp.length > 0) {
                    addCollapseWarningsButton()
                }
            });*/
    }

    function clearWarnings() {
        lines.each(function (i, line) {
            var lineNr = i + 1;
            var codePart = $(line);
            codePart.find(".warningbox").remove();
        });
    }

    function addAnnotationBox(lineNr, annotation) {
        var line = lines.eq(lineNr-1);
        var codePart = $(line);

        codePart.find(".annotationbox").remove();
        removeCommentBtn(codePart);


        if (annotation.markdown === "") {
            addCommentBtn(lineNr, codePart);
            return;
        }
        removeCommentBtn(codePart);

        var commentbox = $("<div>", {class: "annotationbox commentbox popover right"})
            .appendTo(codePart);

        $("<div>", {class: "arrow"}).appendTo(commentbox);

        var content = $("<div>", {class: "comment popover-content"})
            .html(annotation.markdown)
            .appendTo(commentbox);


        if (hasEditRight) {
            var editBtn = $("<button>", {class: "btn btn-primary edit-btn"});
            editBtn.text("Edit");
            editBtn.insertBefore(content);

            editBtn.click(function() {
                addEditBox(lineNr, annotation)
            });
        }
    }

      function addWarningBox(lineNr, comment) {
          var line = lines.eq(lineNr-1);
          var codePart = $(line);

          removeWarningBtn(codePart);

          //codePart.find(".commentbox").remove();

          var commentbox = $("<div>", {class: "warningbox commentbox popover right"})
              .appendTo(codePart);

          var content = $("<div>", {class: "comment popover-content"})
              .html(comment.markdown)
              .appendTo(commentbox);

          commentbox.css("display", "none");
          addWarningBtn(codePart);
      }


    function addEditBox(lineNr, comment) {
        if (comment === undefined) comment = {text: ""};

        var line = lines.eq(lineNr-1);
        var codePart = $(line);
        codePart.find(".annotationbox").remove();
        removeCommentBtn(codePart);

        var textarea = $("<textarea>", {class: "form-control"});
        textarea.val(comment.text);
        var cancelBtn = $("<button>", {class: "btn"});
        cancelBtn.text("A")
        var saveBtn = $("<button>", {class: "btn btn-primary", type: "submit"});
        saveBtn.text("Speichern");
        var commentbox = $("<div>", {class: "annotationbox commentbox"});
        commentbox.append(textarea);
        commentbox.append(saveBtn);
        codePart.append(commentbox);
        textarea.focus();


        saveBtn.click(function () {
            var text = textarea.val();
            var spinner = $("<span>", {class: "glyphicon glyphicon-refresh spinning"});
            saveBtn.prepend(spinner);
            //alert("save " + lineNr + " " + text)
            var jqxhr = $.ajax({
                type: 'POST',
                url: (exclaimContext
                    + "exercise/" + encodeURIComponent(exerciseId)
                    + "/sheet/" + encodeURIComponent(sheetId)
                    + "/assignment/" + encodeURIComponent(assignmentId)
                    + "/team/" + encodeURIComponent(groupId) + "/" + encodeURIComponent(teamId)
                    + "/" + encodeURIComponent(fileid) + "/annotations"),
                data: {lineNr: lineNr, text: text, "__anti-forgery-token": csrfToken},
                beforeSend: function (xhr) {
                    xhr.setRequestHeader('X-CSRF-TOKEN', csrfToken);
                }
            });
            jqxhr.always(function() {
                spinner.remove();
            });
            jqxhr.done(function(resp) {
                addAnnotationBox(lineNr, {text: text, markdown: resp.markdown});
            });
            jqxhr.fail(function() {
                alert( "Kommentar konnte nicht gespeichert werden." );
            });

        });
    }

})})};