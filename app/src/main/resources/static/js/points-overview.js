$(".points-overview").each(function(am_index, am) {
    var $am = $(am);

    // data passed in by special html element
    var passedData = $am.find(".points-data");
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
          + "/pointsoverview")
        .fail(function() {
            alert("Punkteverteilung konnte nicht geladen werden.");
        })
        .done(function (res) {
            data = res;
            isLoaded = true;
            //loadChart();
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
        console.log($am);
        if (!inView && isScrolledIntoView('#points-overview-canvas')) {
            if (!isLoaded) return;
            inView = true;
            loadChart()
        }
    };

    $(window).scroll(checkLoad);

    function loadChart() {
        var x = [];
        var y = [];

        var points = data.points;

        var max = Math.ceil(data.maxPoints / 5) * 5 ;

        //convert to data and add 0 values;
        for (var i = 0; i <= max; i++) {
            x.push(i);
            if (i in points) {
                y.push(points[i]);
            } else {
                y.push(0);
            }
        }

        var backgroundColor = Array(max+1).fill('rgba(54, 162, 235, 1)');
        var borderColor = Array(max+1).fill('rgba(54, 162, 235, 1)');

        var ctx =  $am.find("#points-overview-canvas");

        var myChart = new Chart(ctx, {
            type: 'bar',
            data: {
                labels: x,
                datasets: [{
                    data: y,
                    label: 'Punkteverteilung',
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
                            labelString: 'Punkte'
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
                            return 'Punkte ' + tooltipItem[0].xLabel;
                        }
                    }
                }
            }
        });

    }

});