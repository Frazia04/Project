$(".grade-overview").each(function(am_index, am) {
    var $am = $(am);

    // data passed in by special html element
    var passedData = $am.find(".grade-data");
    var exclaimContext = passedData.attr('data-exclaimContext');
    var csrfToken = passedData.attr('data-csrf');
    var exerciseId = passedData.attr('data-exerciseId');
    var examId = passedData.attr('data-examId');

    var inView = false;
    var isLoaded = false;
    var data;

    loadData();

    function loadData() {
        $.get(exclaimContext
          + "exercise/" + encodeURIComponent(exerciseId)
          + "/exam/" + encodeURIComponent(examId)
          + "/gradeoverview")
        .fail(function() {
            alert("Notenverteilung konnte nicht geladen werden.");
        })
        .done(function (res) {
            data = res;
            isLoaded = true;
            checkLoad();
        });
    }

    function isScrolledIntoView(elem)
    {
        var docViewTop = $(window).scrollTop();
        var docViewBottom = docViewTop + $(window).height();

        var elemTop = $(elem).offset().top;
        var elemBottom = elemTop + $(elem).height();

        return ((elemTop <= docViewBottom) && (elemBottom >= docViewTop));
    }

    var checkLoad = function() {
        if (!inView && isScrolledIntoView('#grade-overview-canvas')) {
            if (!isLoaded) return;
            inView = true;
            loadChart()
        }
    };

    $(window).scroll(checkLoad);

    function loadChart() {
        var x = [];
        var y = [];

        Object.keys(data).forEach(function (key) {
            x.push(key);
            y.push(data[key]);
        });

        var size = Object.keys(data).length;

        var backgroundColor = Array(size+1).fill('rgba(54, 162, 235, 0.5)');
        var borderColor = Array(size+1).fill('rgba(54, 162, 235, 1)');

        var ctx =  $am.find("#grade-overview-canvas");


        var myChart = new Chart(ctx, {
            type: 'bar',
            data: {
                labels: x,
                datasets: [{
                    data: y,
                    label: 'Notenverteilung',
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
                            labelString: 'Anzahl'
                        }
                    }],
                    xAxes: [{
                        id: 'x1',
                        scaleLabel: {
                            display: true,
                            labelString: 'Note'
                        }
                    }]
                },
                maintainAspectRatio: false,
                legend: {
                    labels: {
                        fontSize: 20,
                        boxWidth: 0
                    },
                    onClick: function () {}
                },
                tooltips: {
                    displayColors: false,
                    callbacks: {
                        label: function(tooltipItem) {
                            return tooltipItem.yLabel;
                        },
                        title: function(tooltipItem) {
                            return 'Note ' + tooltipItem[0].xLabel;
                        }
                    }
                }
            }
        });

    }

});