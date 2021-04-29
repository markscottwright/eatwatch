package com.markscottwright.eatwatch;

import java.awt.Color;
import java.awt.Font;
import java.awt.Paint;
import java.awt.Shape;
import java.awt.geom.Ellipse2D;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.JFrame;
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

	List<WeightAt> weightHistory = new ArrayList<>();
	WeightAt goalWeight = new WeightAt(2021, 10, 1, 175.0);

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
	}

	static final int GOAL_SERIES_INDEX = 0;
	static final int MEASURED_WEIGHT_SERIES_INDEX = 1;
	static final int SMOOTHED_WEIGHT_SERIES_INDEX = 2;
	static final Color MEASURED_WEIGHT_COLOR = Color.RED;
	static final Color GOAL_COLOR = Color.LIGHT_GRAY;
	static final Color SMOOTHED_WEIGHT_COLOR = Color.BLUE;

	public EatWatch() {
		loadHistory();
		var goal = new TimeSeries("Goal");
		weightHistory.get(0).addTo(goal);
		goalWeight.addTo(goal);

		var weight = new TimeSeries("Measured Weight");
		weightHistory.forEach(w -> w.addTo(weight));

		var smoothedWeight = new TimeSeries("Smoothed Weight");
		runningAveragesOf(weightHistory).forEach(w -> w.addTo(smoothedWeight));

		TimeSeriesCollection datasets = new TimeSeriesCollection();
		datasets.addSeries(goal);
		datasets.addSeries(weight);
		datasets.addSeries(smoothedWeight);
		JFreeChart chart = ChartFactory.createTimeSeriesChart("Eat Watch", "Date", "Weight", datasets);
		chart.getTitle().setFont(new Font("Liberation Sans", Font.PLAIN, 48));
		chart.getPlot().setBackgroundPaint(Color.WHITE);
		XYPlot plot = (XYPlot) chart.getPlot();
		plot.getRangeAxis().setRange(172, 200);
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
		chartPanel.setBackground(Color.WHITE);
		Border chartBorder = BorderFactory.createEmptyBorder(20, 20, 20, 20);
		chartPanel.setBorder(chartBorder);
		add(chartPanel);
	}

	private void loadHistory() {
		try {
			boolean firstLine = true;
			for (String line : Files
					.readAllLines(Paths.get("weight.txt"))) {
				line = line.trim();
				if (line.startsWith("#") || line.length() == 0)
					continue;
				if (firstLine) {
					firstLine = false;
					String[] fields = line.split("-|\\s+");
					goalWeight = new WeightAt(Integer.parseInt(fields[0]), Integer.parseInt(fields[1]),
							Integer.parseInt(fields[2]), Double.parseDouble(fields[3]));
				} else {
					String[] fields = line.split("-|\\s+");
					weightHistory.add(new WeightAt(Integer.parseInt(fields[0]), Integer.parseInt(fields[1]),
							Integer.parseInt(fields[2]), Double.parseDouble(fields[3])));
				}
			}
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

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

	public static void main(String[] args) {
		EatWatch eatWatch = new EatWatch();
		eatWatch.setSize(500, 500);
		eatWatch.setLocationRelativeTo(null);
		eatWatch.setDefaultCloseOperation(EXIT_ON_CLOSE);
		eatWatch.setVisible(true);
	}
}
