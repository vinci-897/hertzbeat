/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.apache.hertzbeat.common.util.prometheus;

import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.common.protocol.types.Field;
import org.eclipse.persistence.internal.sessions.DirectCollectionChangeRecord;
import org.springframework.boot.autoconfigure.web.ServerProperties;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * prometheus metric sparser
 */
@Slf4j
public class PrometheusUtil {

    // An unknown format occurred during parsing because parsing cannot continue
    // or the end of the input stream has been reached
//    private static final int WRONG_FORMAT = -1;

    //The input stream ends normally
//    private static final int NORMAL_END = -2;

//    private static final int COMMENT_LINE = -3;

    private static final String HELP_PREFIX = "HELP";

    private static final String TYPE_PREFIX = "TYPE";

    private static class FormatException extends Exception {
        public FormatException() {}
        public FormatException(String message) {
            super(message);
        }
    }

    private static class CharChecker {
        int i;
        boolean satisfied;
        CharChecker(int i) {
            this.i = i;
            this.satisfied = false;
        }
//        private CharChecker NotEOF() throws FormatException {
//            if (i == -1) {
//                throw new FormatException();
//            }
//            return this;
//        }
//        private CharChecker NotEOL() throws FormatException {
//            if (i == '\n') {
//                throw new FormatException();
//            }
//            return this;
//        }
        private CharChecker MaybeLeftBracket() {
            if (i == '{') {
                satisfied = true;
            }
            return this;
        }

        private CharChecker MaybeRightBracket() {
            if (i == '}') {
                satisfied = true;
            }
            return this;
        }

        private CharChecker MaybeEqualsSign() {
            if (i == '=') {
                satisfied = true;
            }
            return this;
        }

        private CharChecker MaybeQuotationMark() {
            if (i == '"') {
                satisfied = true;
            }
            return this;
        }

        private CharChecker MaybeSpace() {
            if (i == ' ') {
                satisfied = true;
            }
            return this;
        }

        private CharChecker MaybeComma() {
            if (i == ',') {
                satisfied = true;
            }
            return this;
        }

        private CharChecker MaybeEOF() {
            if (i == -1) {
                satisfied = true;
            }
            return this;
        }

        private CharChecker MaybeEOL() {
            if (i == '\n') {
                satisfied = true;
            }
            return this;
        }

        private int NoElse() throws FormatException {
            if (!satisfied) {
                throw new FormatException();
            }
            return this.i;
        }

    }

    private static CharChecker parseOneChar(InputStream inputStream) throws IOException {
        int i = inputStream.read();
        return new CharChecker(i);
    }

    private static CharChecker parseOneWord(InputStream inputStream, StringBuilder stringBuilder) throws IOException {
        int i = inputStream.read();
        while ((i >= 'a' && i <= 'z') || (i >= 'A' && i <= 'Z') || (i >= '0' && i <= '9') || i == '_' || i == ':') {
            stringBuilder.append((char) i);
            i = inputStream.read();
        }
        return new CharChecker(i);
    }
    private static CharChecker parseUntilEnd(InputStream inputStream, StringBuilder stringBuilder) throws IOException {
        int i = inputStream.read();
        while (i != '\n' && i != -1) {
            stringBuilder.append((char) i);
            i = inputStream.read();
        }
        return new CharChecker(i);
    }

    private static Double parseDouble(String string) throws FormatException {
        switch (string) {
            case "+Inf":
                return Double.POSITIVE_INFINITY;
            case "-Inf":
                return Double.NEGATIVE_INFINITY;
            default:
                try {
                    BigDecimal bigDecimal = new BigDecimal(string);
                    return bigDecimal.doubleValue();
                } catch (NumberFormatException e) {
                    throw new FormatException();
                }
        }
    }



    private static int parseLabel(InputStream inputStream, List<Label> labelList) throws IOException {
        Label.LabelBuilder labelBuilder = new Label.LabelBuilder();
        int i;

        StringBuilder labelName = new StringBuilder();
        i = inputStream.read();
        while (i != -1 && i != '=') {
            labelName.append((char) i);
            i = inputStream.read();
        }
        if (i == -1) {
            return WRONG_FORMAT;
        }
        labelBuilder.name(labelName.toString());

        if (inputStream.read() != '\"') {
            return WRONG_FORMAT;
        }

        StringBuilder labelValue = new StringBuilder();
        i = inputStream.read();
        while (i != -1 && i != ',' && i != '}') {
            labelValue.append((char) i);
            i = inputStream.read();
        }
        if (i == -1 || labelValue.charAt(labelValue.length() - 1) != '\"') {
            return WRONG_FORMAT;
        }

        // skip space only in this condition
        if (i == '}' && inputStream.read() != ' ') {
            return WRONG_FORMAT;
        }

        labelValue.deleteCharAt(labelValue.length() - 1);
        labelBuilder.value(labelValue.toString());

        labelList.add(labelBuilder.build());
        return i;
    }

    private static int parseLabelList(InputStream inputStream, Metric.MetricBuilder metricBuilder) throws IOException {
        List<Label> labelList = new ArrayList<>();
        int i;

        i = parseLabel(inputStream, labelList);
        while (i == ',') {
            i = parseLabel(inputStream, labelList);
        }
        if (i == -1) {
            return WRONG_FORMAT;
        }

        metricBuilder.labelList(labelList);
        return i;
    }

    private static int parseValue(InputStream inputStream, Metric.MetricBuilder metricBuilder) throws IOException {
        int i;

        StringBuilder stringBuilder = new StringBuilder();
        i = inputStream.read();
        while (i != -1 && i != ' ' && i != '\n') {
            stringBuilder.append((char) i);
            i = inputStream.read();
        }

        String string = stringBuilder.toString();

        switch (string) {
            case "+Inf":
                return Double.POSITIVE_INFINITY;
                break;
            case "-Inf":
                return Double.NEGATIVE_INFINITY);
                break;
            default:
                try {
                    BigDecimal bigDecimal = new BigDecimal(string);
                    metricBuilder.value(bigDecimal.doubleValue());
                } catch (NumberFormatException e) {
                    return WRONG_FORMAT;
                }
                break;
        }

        if (i == -1) {
            return NORMAL_END;
        }
        else {
            return i; // ' ' or \n'
        }
    }

    private static int parseTimestamp(InputStream inputStream, Metric.MetricBuilder metricBuilder) throws IOException {
        int i;

        StringBuilder stringBuilder = new StringBuilder();
        i = inputStream.read();
        while (i != -1 && i != '\n') {
            stringBuilder.append((char) i);
            i = inputStream.read();
        }

        String string = stringBuilder.toString();
        try {
            metricBuilder.timestamp(Long.parseLong(string));
        } catch (NumberFormatException e) {
            return WRONG_FORMAT;
        }

        if (i == -1) {
            return NORMAL_END;
        }
        else {
            return i; // '\n'
        }
    }
    private static int parseMetricName(InputStream inputStream, StringBuilder stringBuilder) throws IOException {
        int i = inputStream.read();
        while (i != '{') {
            stringBuilder.append((char) i);
            i = inputStream.read();
        }

        while (i != -1) {
            if (i == ' ' || i == '{') {
                metricBuilder.metricName(stringBuilder.toString());
                return i;
            }
            stringBuilder.append((char) i);
            i = inputStream.read();
        }

        return WRONG_FORMAT;
    }
    // return value:
    // -1: error format
    // -2: normal end
    // '\n': more lines
    private static void parseMetric(InputStream inputStream, MetricFamily metricFamily, StringBuilder stringBuilder) throws IOException {
        int i = parseMetricName(inputStream, stringBuilder); // RET: -1, -2, -3, '{', ' '
        if (i == WRONG_FORMAT || i == NORMAL_END || i == COMMENT_LINE) {
            return i;
        }

        if (i == '{') {
            i = parseLabelList(inputStream, metricBuilder); // RET: -1, '}'
            if (i == WRONG_FORMAT) {
                return i;
            }
        }


        i = parseValue(inputStream, metricBuilder); // RET: -1, -2, '\n', ' '
        if (i != ' ') {
            metrics.add(metricBuilder.build());
            return i;
        }

        i = parseTimestamp(inputStream, metricBuilder); // RET: -1, -2, '\n'

        metrics.add(metricBuilder.build());
        return i;

    }


    private static List<MetricFamily.Label> parseLabel(InputStream inputStream, StringBuilder stringBuilder) throws IOException, FormatException {
        List<MetricFamily.Label> labelList = new ArrayList<>();
        int i;
        while (true) {
            MetricFamily.Label label = new MetricFamily.Label();
            parseOneWord(inputStream, stringBuilder).MaybeEqualsSign().NoElse();
            label.setName(stringBuilder.toString());
            stringBuilder.delete(0, stringBuilder.length());

            parseOneChar(inputStream).MaybeQuotationMark().NoElse();

            parseOneWord(inputStream, stringBuilder).MaybeQuotationMark().NoElse();
            label.setValue(stringBuilder.toString());
            stringBuilder.delete(0, stringBuilder.length());

            parseOneChar(inputStream).MaybeQuotationMark().NoElse();

            i = parseOneChar(inputStream).MaybeComma().MaybeRightBracket().NoElse();
            labelList.add(label);
            if (i == '}') {
                break;
            }
        }
        return labelList;
    }

    private static void parse_summary(InputStream inputStream, MetricFamily metricFamily, StringBuilder stringBuilder) throws IOException, FormatException {
        MetricFamily.Metric metric = new MetricFamily.Metric();
        int i = parseOneWord(inputStream, stringBuilder).MaybeSpace().MaybeLeftBracket().NoElse();
        stringBuilder.delete(0, stringBuilder.length());

        List<MetricFamily.Label> labelList = new ArrayList<>();
        if (i == '{') {
            while (true) {
                MetricFamily.Label label = new MetricFamily.Label();
                parseOneWord(inputStream, stringBuilder).MaybeEqualsSign().NoElse();
                String labelName = stringBuilder.toString();
                if ()
                label.setName(stringBuilder.toString());
                stringBuilder.delete(0, stringBuilder.length());

                parseOneChar(inputStream).MaybeQuotationMark().NoElse();

                parseOneWord(inputStream, stringBuilder).MaybeQuotationMark().NoElse();
                label.setValue(stringBuilder.toString());
                stringBuilder.delete(0, stringBuilder.length());

                parseOneChar(inputStream).MaybeQuotationMark().NoElse();

                i = parseOneChar(inputStream).MaybeComma().MaybeRightBracket().NoElse();
                labelList.add(label);
                if (i == '}') {
                    break;
                }
            }
        }
        parseOneChar(inputStream).MaybeSpace().NoElse();

        MetricFamily.Counter counter = new MetricFamily.Counter();
        i = parseOneWord(inputStream, stringBuilder).MaybeSpace().MaybeEOL().NoElse();
        counter.setValue(parseDouble(stringBuilder.toString()));
        stringBuilder.delete(0, stringBuilder.length());
        metric.setCounter(counter);

        if (i == ' ') {
            parseOneWord(inputStream, stringBuilder).MaybeEOL().NoElse();
            metric.setTimestampMs(Long.parseLong(stringBuilder.toString()));
        }

        metricFamily.getMetricList().add(metric);
    }

    private static void parse_counter(InputStream inputStream, MetricFamily metricFamily, StringBuilder stringBuilder) throws IOException, FormatException {
        MetricFamily.Metric metric = new MetricFamily.Metric();
        int i = parseOneWord(inputStream, stringBuilder).MaybeSpace().MaybeLeftBracket().NoElse();
        stringBuilder.delete(0, stringBuilder.length());

        if (i == '{') {
            metric.setLabelPair(parseLabel(inputStream, stringBuilder));
        }
        parseOneChar(inputStream).MaybeSpace().NoElse();

        MetricFamily.Counter counter = new MetricFamily.Counter();
        i = parseOneWord(inputStream, stringBuilder).MaybeSpace().MaybeEOL().NoElse();
        counter.setValue(parseDouble(stringBuilder.toString()));
        stringBuilder.delete(0, stringBuilder.length());
        metric.setCounter(counter);

        if (i == ' ') {
            parseOneWord(inputStream, stringBuilder).MaybeEOL().NoElse();
            metric.setTimestampMs(Long.parseLong(stringBuilder.toString()));
        }

        metricFamily.getMetricList().add(metric);
    }

    private static void parse_gauge(InputStream inputStream, MetricFamily metricFamily, StringBuilder stringBuilder) throws IOException, FormatException {
        MetricFamily.Metric metric = new MetricFamily.Metric();
        int i = parseOneWord(inputStream, stringBuilder).MaybeSpace().MaybeLeftBracket().NoElse();
        stringBuilder.delete(0, stringBuilder.length());

        if (i == '{') {
            metric.setLabelPair(parseLabel(inputStream, stringBuilder));
        }
        parseOneChar(inputStream).MaybeSpace().NoElse();

        MetricFamily.Gauge gauge = new MetricFamily.Gauge();
        i = parseOneWord(inputStream, stringBuilder).MaybeSpace().MaybeEOL().NoElse();
        gauge.setValue(parseDouble(stringBuilder.toString()));
        stringBuilder.delete(0, stringBuilder.length());
        metric.setGauge(gauge);

        if (i == ' ') {
            parseOneWord(inputStream, stringBuilder).MaybeEOL().NoElse();
            metric.setTimestampMs(Long.parseLong(stringBuilder.toString()));
        }

        metricFamily.getMetricList().add(metric);

    }

    private static void parse_histogram(InputStream inputStream, MetricFamily metricFamily, StringBuilder stringBuilder) throws IOException, FormatException {

    }

    private static void parseCommentLine(InputStream inputStream, MetricFamily metricFamily) throws IOException, FormatException {
        // skip space after '#'
        int i = inputStream.read();
        if (i != ' ') {
            throw new FormatException();
        }

        // parse prefix
        StringBuilder stringBuilderPrefix = new StringBuilder();
        parseOneWord(inputStream, stringBuilderPrefix).MaybeSpace().NoElse();
        StringBuilder stringBuilderName = new StringBuilder();
        parseOneWord(inputStream, stringBuilderName).MaybeSpace().NoElse();
        metricFamily.setName(stringBuilderName.toString());
        if (HELP_PREFIX.contentEquals(stringBuilderPrefix)) {
            stringBuilderPrefix.setLength(0);
            parseUntilEnd(inputStream, stringBuilderPrefix).MaybeEOL().NoElse();
            metricFamily.setHelp(stringBuilderPrefix.toString());
        }
        else if (TYPE_PREFIX.contentEquals(stringBuilderPrefix)) {
            stringBuilderPrefix.setLength(0);
            parseOneWord(inputStream, stringBuilderPrefix).MaybeEOL().NoElse();
            MetricFamily.MetricType type = MetricFamily.MetricType.getType(stringBuilderPrefix.toString());
            if (type != null) {
                metricFamily.setMetricType(type);
            }
            else {
                throw new FormatException();
            }
        }
    }

    public static Map<String, MetricFamily> parseMetrics(InputStream inputStream) throws IOException {
        Map<String, MetricFamily> metricFamilyMap = new ConcurrentHashMap<>(10);
        MetricFamily metricFamily = null;
        int i = inputStream.read();
        try {
            while (i != -1) {
                if (i == '#') {
                    metricFamily = new MetricFamily();
                    metricFamily.setMetricList(new ArrayList<>());
                    parseCommentLine(inputStream, metricFamily);
                } else {
                    StringBuilder stringBuilder = new StringBuilder();
                    stringBuilder.append((char) i);
                    PrometheusUtil.class.getMethod("parse_" + metricFamily.getMetricType().getValue(),
                            InputStream.class, MetricFamily.class, StringBuilder.class)
                            .invoke(null, inputStream, metricFamily, stringBuilder);
                }
                i = inputStream.read();
            }
        } catch (FormatException | NoSuchMethodException | InvocationTargetException | IllegalAccessException | NullPointerException e) {
            log.error("prometheus parser failed because of wrong input format. {}", e.getMessage());
            return null;
        }
        return metricFamilyMap;
    }


}
