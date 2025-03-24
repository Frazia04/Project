$(function () {
    var assignmentRow = $('.assignment-row');

    var wsprot = "";
    if (window.location.protocol === "http:") {
        wsprot = "ws";
    } else {
        wsprot = "wss";
    }
    var websock = new RobustWebSocket(wsprot + '://' + window.location.hostname + ':' + window.location.port +  window.contextPath + 'wsregistry', null, {
        timeout: 4000,
        shouldReconnect: function (event, ws) {
            if (event.code === 1008 || event.code === 1011) return;
            return Math.pow(1.5, ws.attempts) * 500; //exponential backoff
        }
    });
    var stompClient = webstomp.over(websock, {'debug': true});
    var headers = {};
    headers["X-CSRF-TOKEN"] = window.csrftoken;

    var onStompConnect = function(frame) {

        $('.assignment-panel').each(function(index, p){
            p = $(p);
            var assignmentid = p.attr('data-assignmentid');
            var group = p.attr('data-group');
            var sheetid = p.attr('data-sheetid');
            var team = p.attr('data-team');
            var exerciseid = p.attr('data-exerciseid');
            stompClient.subscribe('/topic/testresults/' + exerciseid + '/' + sheetid + '/' + group + '/' + team, function(f) {
                var result = JSON.parse(f.body);
                if (result.assignment === assignmentid &&
                    (result.status === "done" || result.status === "failed")) {
                    reloadAssignmentInfo(p);
                }
            });
        });

        console.log("Connection to server-channels established")
    };
    stompClient.connect(headers, onStompConnect);

    assignmentRow.on('submit', '.test-form', function (e) {
        var form = $(this);
        var clickedBtn = form.find('.test-button');
        var assignmentPanel = form.closest('.assignment-panel');
        var container = form.closest('.assignment-controls');
        var testResults = container.find('.test-results');
        var testResultShortDetails = container.find('.testResultShortDisplay');

        var assignmentid = assignmentPanel.attr('data-assignmentid');
        var group = assignmentPanel.attr('data-group');
        var sheetid = assignmentPanel.attr('data-sheetid');
        var team = assignmentPanel.attr('data-team');
        var exerciseid = assignmentPanel.attr('data-exerciseid');
        var snapshot = assignmentPanel.find('.uploads-box').attr('data-snapshot');
        if (!snapshot) {
            snapshot = assignmentPanel.attr('data-snapshot');
        }
        console.log("data: ", assignmentid, group, sheetid, team, exerciseid);

        clickedBtn.button('loading ...');
        clickedBtn.html('<span class="glyphicon glyphicon-refresh spinning"></span> Loading...  ');
        testResults.html('');
        testResultShortDetails.html('');
        
        $.ajax({
            type: 'POST',
            url: window.contextPath + "exercise/" + exerciseid + "/sheet/" + sheetid + "/assignment/" + assignmentid + "/team/" + group + "/" + team + "/test",
            data: {
                snapshot: snapshot
            },
            beforeSend: function (xhr) {
                xhr.setRequestHeader('X-CSRF-TOKEN', window.csrftoken);
            },
            timeout: 30*1000 // 30 seconds
        }).fail(function(req, status, err) {
            var errorPanel = $('#stomp-error');
            errorPanel.html('Testfälle konnten nicht gestartet werden! Bitte Seite neu laden.');
            errorPanel.show();
        });

        // prevent the normal event from happening
        return false;
    });

    $('.startAllTests-button').click(function (e) {
        var clickedBtn = $(this);
        var msgBox = clickedBtn.siblings('.all-tests-res');

        var exerciseid = clickedBtn.attr('data-exerciseid');
        var sheetid = clickedBtn.attr('data-sheetid');

        $.ajax({
            type: 'POST',
            url: window.contextPath + "exercise/" + exerciseid + "/sheet/" + sheetid + "/alltests",
            beforeSend: function (xhr) {
                xhr.setRequestHeader('X-CSRF-TOKEN', window.csrftoken);
            }
        }).done(function (response) {
            msgBox.text("Alle tests gestartet.");
        }).fail(function (req, status, err) {
            msgBox.text('Tests konnten nicht gestartet werden.');
        }).always(function () {
            clickedBtn.button('reset');
        });


    });


});