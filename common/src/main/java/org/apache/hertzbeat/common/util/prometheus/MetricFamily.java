package org.apache.hertzbeat.common.util.prometheus;


import java.util.ArrayList;
import java.util.List;
import lombok.Data;
import lombok.ToString;

/**
 *
 */
@Data
@ToString
public class MetricFamily {
    /**
     * metric name
     */
    private String name;

    /**
     * metric help
     */
    private String help;

    /**
     * metric type
     */
    private MetricType metricType;

    /**
     * Specific metric
     */
    private List<Metric> metricList;

    public enum MetricType {
        // for string metric info
        INFO("info"),
        // Represents a monotonically increasing counter, e.g., counting occurrences
        COUNTER("counter"),
        // A metric type that can fluctuate up and down, e.g., CPU usage rate
        GAUGE("gauge"),
        SUMMARY("summary"),
        UNTYPED("untyped"),
        HISTOGRAM("histogram");

        private final String value;

        MetricType(String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }

        public static MetricType getType(String value) {
            for (MetricType metricType : values()) {
                if (metricType.getValue().equals(value)) {
                    return metricType;
                }
            }
            return null;
        }
    }

    /**
     * Metric
     */
    @Data
    public static class Metric {

        /**
         * Label data, mainly corresponding to the content within {}
         */
        private List<Label> labelPair;

        /**
         * info
         */
        private Info info;

        /**
         * gauge
         */
        private Gauge gauge;

        /**
         * counter
         */
        private Counter counter;

        /**
         * summary
         */
        private Summary summary;

        /**
         * untyped
         */
        private Untyped untyped;

        /**
         * histogram
         */
        private Histogram histogram;

        /**
         * timestampMs
         */
        private Long timestampMs;
    }

    /**
     * Label
     */
    @Data
    public static class Label {

        /**
         * name
         */
        private String name;

        /**
         * value
         */
        private String value;
    }

    /**
     * Info
     */
    @Data
    public static class Info {

        /**
         * value
         */
        private double value;

    }

    /**
     * Counter
     */
    @Data
    public static class Counter {

        /**
         * value
         */
        private double value;

        // Exemplar
    }

    /**
     * Gauge
     */
    @Data
    public static class Gauge {

        /**
         * value
         */
        private double value;
    }

    /**
     * untyped
     */
    @Data
    public static class Untyped {

        /**
         * value
         */
        private double value;
    }

    /**
     * Summary
     */
    @Data
    public static class Summary {

        /**
         * count
         */
        private long count;

        /**
         * sum
         */
        private double sum;

        /**
         * quantileList
         */
        private List<Quantile> quantileList = new ArrayList<>();
    }

    /**
     * Quantile
     */
    @Data
    public static class Quantile {
        /**
         * Corresponding to the quantile field in Prometheus
         */
        private double xLabel;

        /**
         * value
         */
        private double value;
    }

    /**
     * Histogram
     */
    @Data
    public static class Histogram {

        /**
         * count
         */
        private long count;

        /**
         * sum
         */
        private double sum;

        /**
         * bucketList
         */
        private List<Bucket> bucketList = new ArrayList<>();
    }

    /**
     * Bucket
     */
    @Data
    public static class Bucket {

        /**
         * cumulativeCount
         */
        private long cumulativeCount;

        /**
         * upperBound
         */
        private double upperBound;
    }
}
