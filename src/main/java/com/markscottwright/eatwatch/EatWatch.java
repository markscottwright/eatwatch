package com.markscottwright.eatwatch;

import static java.awt.BorderLayout.NORTH;
import static java.awt.BorderLayout.SOUTH;
import static java.awt.Color.WHITE;
import static java.awt.Font.ITALIC;
import static java.awt.Label.CENTER;
import static java.nio.file.Files.readAllLines;
import static java.time.temporal.ChronoUnit.DAYS;
import static javax.swing.JOptionPane.ERROR_MESSAGE;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Font;
import java.awt.Label;
import java.awt.Paint;
import java.awt.Shape;
import java.awt.geom.Ellipse2D;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalDouble;

import javax.imageio.ImageIO;
import javax.swing.BorderFactory;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;
import javax.swing.border.Border;

import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartPanel;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.xy.XYLineAndShapeRenderer;
import org.jfree.data.time.Day;
import org.jfree.data.time.TimeSeries;
import org.jfree.data.time.TimeSeriesCollection;

@SuppressWarnings("serial")
public class EatWatch extends JFrame {

    static class WeightAt {
        public WeightAt(int year, int month, int day, double weight) {
            this.year = year;
            this.month = month;
            this.day = day;
            this.weight = weight;
        }

        final int year;
        final int month;
        final int day;
        final double weight;

        void addTo(TimeSeries series) {
            series.add(new Day(day, month, year), weight);
        }

        @Override
        public String toString() {
            return "WeightAt [year=" + year + ", month=" + month + ", day=" + day + ", weight=" + weight + "]";
        }

        public LocalDate getDate() {
            return LocalDate.of(year, month, day);
        }
    }

    static final int GOAL_SERIES_INDEX = 0;
    static final int MEASURED_WEIGHT_SERIES_INDEX = 1;
    static final int SMOOTHED_WEIGHT_SERIES_INDEX = 2;
    static final Color MEASURED_WEIGHT_COLOR = Color.RED;
    static final Color GOAL_COLOR = Color.LIGHT_GRAY;
    static final Color SMOOTHED_WEIGHT_COLOR = Color.BLUE;
    private ChartPanel chartPanel;
    private List<WeightAt> weightHistory = new ArrayList<>();
    private WeightAt goalWeight;

    public EatWatch() {
        goalWeight = loadHistory(weightHistory);

        JPanel titleAndSubtitleLayout = createTitlePanel();
        add(titleAndSubtitleLayout, BorderLayout.NORTH);

        chartPanel = createChartPanel();
        add(chartPanel, BorderLayout.CENTER);

        JButton reloadButton = new JButton("reload");
        add(reloadButton, BorderLayout.SOUTH);
        reloadButton.addActionListener(a -> reload());

        setIcon();
    }

    private JPanel createTitlePanel() {
        JPanel titleAndSubtitleLayout = new JPanel(new BorderLayout());
        Label title = new Label("Eat Watch");
        title.setFont(new Font("Liberation Sans", Font.PLAIN, 48));
        title.setAlignment(CENTER);
        title.setBackground(WHITE);
        final String subTitleText;
        if (weightHistory.size() > 0) {
            long daysDown = DAYS.between(weightHistory.get(0).getDate(), LocalDate.now()) + 1;
            long daysToGo = DAYS.between(LocalDate.now(), goalWeight.getDate()) - 1;
            subTitleText = String.format("%d day%s down, %d day%s to go (%d%%)", daysDown, daysDown == 1 ? "" : "s",
                    daysToGo, daysToGo == 1 ? "" : "s", (100) * daysDown / (daysDown + daysToGo));
        } else {
            subTitleText = "- days down, - days to go (0%%)";
        }
        Label subTitle = new Label(subTitleText);
        subTitle.setFont(new Font("Liberation Sans", ITALIC, 24));
        subTitle.setAlignment(CENTER);
        subTitle.setBackground(WHITE);
        titleAndSubtitleLayout.add(title, NORTH);
        titleAndSubtitleLayout.add(subTitle, SOUTH);
        return titleAndSubtitleLayout;
    }

    private void reload() {
        weightHistory.clear();
        goalWeight = loadHistory(weightHistory);
        ChartPanel newChartPane = createChartPanel();
        remove(chartPanel);
        add(newChartPane);
        chartPanel = newChartPane;
        revalidate();
    }

    private ChartPanel createChartPanel() {

        TimeSeries goal = new TimeSeries("Goal");
        weightHistory.get(0).addTo(goal);
        goalWeight.addTo(goal);

        TimeSeries weight = new TimeSeries("Measured Weight");
        weightHistory.forEach(w -> w.addTo(weight));

        TimeSeries smoothedWeight = new TimeSeries("Smoothed Weight");
        runningAveragesOf(weightHistory).forEach(w -> w.addTo(smoothedWeight));

        TimeSeriesCollection datasets = new TimeSeriesCollection();
        datasets.addSeries(goal);
        datasets.addSeries(weight);
        datasets.addSeries(smoothedWeight);
        JFreeChart chart = ChartFactory.createTimeSeriesChart("", "Date", "Weight", datasets);
        // chart.getTitle().setFont(new Font("Liberation Sans", Font.PLAIN, 48));
        chart.getPlot().setBackgroundPaint(Color.WHITE);
        XYPlot plot = (XYPlot) chart.getPlot();

        // determine axis range
        OptionalDouble maybeMaxWeight = weightHistory.stream().mapToDouble(w -> w.weight).max();
        OptionalDouble maybeMinWeight = weightHistory.stream().mapToDouble(w -> w.weight).min();
        int maxWeight = Integer.max((int) maybeMaxWeight.orElse(Integer.MIN_VALUE), (int) goalWeight.weight);
        int minWeight = Integer.min((int) maybeMinWeight.orElse(Integer.MAX_VALUE), (int) goalWeight.weight);
        plot.getRangeAxis().setRange(minWeight - 2, maxWeight + 2);

        plot.setRenderer(new XYLineAndShapeRenderer() {
            Shape CIRCLE = new Ellipse2D.Double(5, 5, 5, 5);

            @Override
            public Paint getSeriesPaint(int series) {
                switch (series) {
                case MEASURED_WEIGHT_SERIES_INDEX:
                    return MEASURED_WEIGHT_COLOR;
                case GOAL_SERIES_INDEX:
                    return GOAL_COLOR;
                case SMOOTHED_WEIGHT_SERIES_INDEX:
                    return SMOOTHED_WEIGHT_COLOR;
                default:
                    return super.getSeriesPaint(series);
                }
            }

            public Boolean getSeriesLinesVisible(int series) {
                return series != MEASURED_WEIGHT_SERIES_INDEX;
            };

            public Boolean getSeriesShapesVisible(int series) {
                return series == MEASURED_WEIGHT_SERIES_INDEX;
            };

            public Shape getSeriesShape(int series) {
                if (series == MEASURED_WEIGHT_SERIES_INDEX)
                    return CIRCLE;
                return super.getSeriesShape(series);
            };

        });
        ChartPanel chartPanel = new ChartPanel(chart);
        chartPanel.setBackground(WHITE);
        Border chartBorder = BorderFactory.createEmptyBorder(20, 20, 20, 20);
        chartPanel.setBorder(chartBorder);
        return chartPanel;
    }

    private void setIcon() {
        try (InputStream iconStream = getClass().getResourceAsStream("/icon.png")) {
            ImageIcon icon = new ImageIcon(ImageIO.read(iconStream));
            setIconImage(icon.getImage());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Read the weight.txt file and fill weightHistory. Returns the goalWeight value
     *
     * @param weightHistory
     * @return
     */
    private WeightAt loadHistory(List<WeightAt> weightHistory) {
        WeightAt goalWeight = null;
        try {
            boolean firstLine = true;
            for (String line : readAllLines(Paths.get("weight.txt"))) {
                line = line.trim();
                if (line.startsWith("#") || line.length() == 0)
                    continue;
                String delimiterRegex = "-|/|\\s+";
                if (firstLine) {
                    firstLine = false;
                    String[] fields = line.split(delimiterRegex);
                    goalWeight = new WeightAt(Integer.parseInt(fields[0]), Integer.parseInt(fields[1]),
                            Integer.parseInt(fields[2]), Double.parseDouble(fields[3]));
                } else {
                    String[] fields = line.split(delimiterRegex);
                    weightHistory.add(new WeightAt(Integer.parseInt(fields[0]), Integer.parseInt(fields[1]),
                            Integer.parseInt(fields[2]), Double.parseDouble(fields[3])));
                }
            }
        } catch (Throwable e) {
            StringWriter msg = new StringWriter();
            msg.append("Error reading weight.txt:" + e + "\n");
            e.printStackTrace(new PrintWriter(msg));
            JOptionPane.showMessageDialog(this, msg.toString(), "Error reading weight file", ERROR_MESSAGE);
        }
        return goalWeight;
    }

    static List<WeightAt> runningAveragesOf(List<WeightAt> weights) {
        int windowSize = 10;
        ArrayList<WeightAt> averages = new ArrayList<>(weights.size());
        double windowAverage = 0;
        for (int i = 0; i < weights.size(); i++) {
            windowAverage += weights.get(i).weight;
            if (i >= windowSize)
                windowAverage -= weights.get(i - windowSize).weight;
            averages.add(new WeightAt(weights.get(i).year, weights.get(i).month, weights.get(i).day,
                    windowAverage / Math.min(i + 1, windowSize)));
        }
        return averages;
    }

    public static void main(String[] args) throws ClassNotFoundException, InstantiationException,
            IllegalAccessException, UnsupportedLookAndFeelException {
        UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        EatWatch eatWatch = new EatWatch();
        eatWatch.setSize(500, 500);
        eatWatch.setLocationRelativeTo(null);
        eatWatch.setDefaultCloseOperation(EXIT_ON_CLOSE);
        eatWatch.setVisible(true);
    }
}
