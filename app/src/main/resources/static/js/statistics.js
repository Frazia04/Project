$(".test-statistics").each(function(am_index, am) {
    $am = $(am);

    // data passed in by special html element
    var passedData = $am.find(".statistics-data");
    var exclaimContext = passedData.attr('data-exclaimContext');
    var csrfToken = passedData.attr('data-csrf');
    var exerciseId = passedData.attr('data-exerciseId');
    var sheetId = passedData.attr('data-sheetId');
    var assignmentId = passedData.attr('data-assignmentId');
    var groupId = passedData.attr('data-groupId');
    var teamId = passedData.attr('data-teamId');
    var requestnr = passedData.attr('data-requestnr');

    var inView = false;
    var isLoaded = false;
    var statistics;

    loadStatistics();

    function loadStatistics() {
        $.get(exclaimContext
          + "exercise/" + encodeURIComponent(exerciseId)
          + "/sheet/" + encodeURIComponent(sheetId)
          + "/" + encodeURIComponent(assignmentId)
          + "/team/" + encodeURIComponent(groupId) + "/" + encodeURIComponent(teamId)
          + "/test/" + encodeURIComponent(requestnr) + "/statistics")
        .fail(function() {
            alert("Statistiken konnten nicht geladen werden.");
        })
        .done(function (res) {
                statistics = res;
                isLoaded = true;
                loadMessage();
        });
    }

    function loadMessage() {
        var div = $am.find("div#test-statistics-message");
        if (statistics.teamPlace <= 3) {
            div.append("<h2>Herzlichen Glückwunsch!</h2>");
        }
        div.append("<h3>Dein Team ist auf Platz " + statistics.teamPlace + " der bestandenen Tests.</h3>");
        if (statistics.teamPlace === 2) {
            div.append("<p>1 Team hat mehr Tests oder diese schneller bestanden.</p>");
        } else if (statistics.teamPlace > 1) {
            div.append("<p>" + (statistics.teamPlace-1) + " Teams haben mehr Tests oder diese schneller bestanden.</p>");
        }
    }

    function isScrolledIntoView(elem)
    {
        var docViewTop = $(window).scrollTop();
        var docViewBottom = docViewTop + $(window).height();

        var elemTop = $(elem).offset().top;
        var elemBottom = elemTop + $(elem).height();

        return ((elemTop <= docViewBottom) && (elemBottom >= docViewTop));
    }

    $(window).scroll(function() {
        if (!inView && isScrolledIntoView('#test-statistics-canvas')) {
            if (!isLoaded) return;
            inView = true;
            loadChart()
        }
    });

    function loadChart() {
        var labels = [];
        var data = [];
        max = Math.max.apply(null, Object.keys(statistics.passedTests));

        //convert to data and add 0 values;
        for (var i = 0; i <= max; i++) {
            labels.push(i);
            if (i in statistics.passedTests) {
                data.push(statistics.passedTests[i]);
            } else {
                data.push(0);
            }
        }

        var backgroundColor = Array(max+1).fill('rgba(0, 0, 0, 0.3)');
        var borderColor = Array(max+1).fill('rgba(0, 0, 0, 0.4)');

        backgroundColor[statistics.testsPassed] = 'rgba(54, 162, 235, 0.5)';
        borderColor[statistics.testsPassed] = 'rgba(54, 162, 235, 1)';

        var ctx =  $am.find("#test-statistics-canvas");

        var myChart = new Chart(ctx, {
            type: 'bar',
            data: {
                labels: labels,
                datasets: [{
                    data: data,
                    label: '# Teams',
                    backgroundColor: backgroundColor,
                    borderColor: borderColor,
                    borderWidth: 1,
                    xAxisID: 'x1'

                }]
            },
            options: {
                scales: {
                    yAxes: [{
                        ticks: {
                            beginAtZero: true,
                            precision: 0
                        },
                        scaleLabel: {
                            display: true,
                            labelString: 'Anzahl Teams'
                        }
                    }],
                    xAxes: [{
                        id: 'x1',
                        scaleLabel: {
                            display: true,
                            labelString: 'Bestandene Tests'
                        }
                    }]
                },
                maintainAspectRatio: false,
                legend: {
                    display: false
                },
                tooltips: {
                    displayColors: false,
                    callbacks: {
                        label: function(tooltipItem) {
                            return 'Teams ' + tooltipItem.yLabel;
                        },
                        title: function(tooltipItem) {
                            return 'Tests ' + tooltipItem[0].xLabel;
                        }
                    }
                }
            }
        });

    }

});