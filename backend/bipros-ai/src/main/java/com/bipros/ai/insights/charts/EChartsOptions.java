package com.bipros.ai.insights.charts;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.List;
import java.util.Map;

/**
 * Tiny ECharts option-builder helpers. Each method returns a complete ECharts
 * option JSON object suitable for embedding into a {@code ChartSpec.option}.
 * The frontend forwards these directly to {@code ReactECharts}, so field names
 * and shapes must match the Apache ECharts schema.
 */
public final class EChartsOptions {

    private EChartsOptions() {}

    /** ECharts tooltip must be an object; passing the trigger as a bare string crashes ECharts. */
    private static ObjectNode tooltip(ObjectMapper m, String trigger) {
        ObjectNode tip = m.createObjectNode();
        tip.put("trigger", trigger);
        return tip;
    }

    public static ObjectNode bar(ObjectMapper m, List<String> categories, String seriesName,
                                 List<? extends Number> values) {
        ObjectNode opt = m.createObjectNode();
        opt.set("tooltip", tooltip(m, "axis"));
        ObjectNode x = m.createObjectNode();
        x.put("type", "category");
        ArrayNode cats = m.createArrayNode();
        categories.forEach(cats::add);
        x.set("data", cats);
        opt.set("xAxis", x);
        ObjectNode y = m.createObjectNode();
        y.put("type", "value");
        opt.set("yAxis", y);
        ArrayNode series = m.createArrayNode();
        ObjectNode s = m.createObjectNode();
        s.put("name", seriesName);
        s.put("type", "bar");
        ArrayNode data = m.createArrayNode();
        values.forEach(v -> data.add(v == null ? 0 : v.doubleValue()));
        s.set("data", data);
        series.add(s);
        opt.set("series", series);
        return opt;
    }

    public static ObjectNode pie(ObjectMapper m, Map<String, ? extends Number> items) {
        return pieLike(m, items, false);
    }

    public static ObjectNode donut(ObjectMapper m, Map<String, ? extends Number> items) {
        return pieLike(m, items, true);
    }

    private static ObjectNode pieLike(ObjectMapper m, Map<String, ? extends Number> items, boolean donut) {
        ObjectNode opt = m.createObjectNode();
        opt.set("tooltip", tooltip(m, "item"));
        ObjectNode legend = m.createObjectNode();
        legend.put("orient", "horizontal");
        legend.put("bottom", 0);
        opt.set("legend", legend);
        ArrayNode series = m.createArrayNode();
        ObjectNode s = m.createObjectNode();
        s.put("type", "pie");
        if (donut) {
            ArrayNode radius = m.createArrayNode();
            radius.add("45%");
            radius.add("70%");
            s.set("radius", radius);
        } else {
            s.put("radius", "60%");
        }
        ArrayNode data = m.createArrayNode();
        items.forEach((name, value) -> {
            ObjectNode item = m.createObjectNode();
            item.put("name", name);
            item.put("value", value == null ? 0 : value.doubleValue());
            data.add(item);
        });
        s.set("data", data);
        series.add(s);
        opt.set("series", series);
        return opt;
    }

    public static ObjectNode gauge(ObjectMapper m, String name, double value, double min, double max) {
        ObjectNode opt = m.createObjectNode();
        ObjectNode s = m.createObjectNode();
        s.put("type", "gauge");
        s.put("min", min);
        s.put("max", max);
        ObjectNode detail = m.createObjectNode();
        detail.put("formatter", "{value}");
        detail.put("fontSize", 18);
        s.set("detail", detail);
        ArrayNode data = m.createArrayNode();
        ObjectNode item = m.createObjectNode();
        item.put("value", value);
        item.put("name", name);
        data.add(item);
        s.set("data", data);
        ArrayNode series = m.createArrayNode();
        series.add(s);
        opt.set("series", series);
        return opt;
    }

    public static ObjectNode treemap(ObjectMapper m, Map<String, ? extends Number> items) {
        ObjectNode opt = m.createObjectNode();
        ObjectNode s = m.createObjectNode();
        s.put("type", "treemap");
        ArrayNode data = m.createArrayNode();
        items.forEach((name, value) -> {
            ObjectNode item = m.createObjectNode();
            item.put("name", name);
            item.put("value", value == null ? 0 : value.doubleValue());
            data.add(item);
        });
        s.set("data", data);
        ArrayNode series = m.createArrayNode();
        series.add(s);
        opt.set("series", series);
        return opt;
    }

    public static ObjectNode line(ObjectMapper m, List<String> categories, String seriesName,
                                  List<? extends Number> values) {
        ObjectNode opt = m.createObjectNode();
        opt.set("tooltip", tooltip(m, "axis"));
        ObjectNode x = m.createObjectNode();
        x.put("type", "category");
        ArrayNode cats = m.createArrayNode();
        categories.forEach(cats::add);
        x.set("data", cats);
        opt.set("xAxis", x);
        ObjectNode y = m.createObjectNode();
        y.put("type", "value");
        opt.set("yAxis", y);
        ArrayNode series = m.createArrayNode();
        ObjectNode s = m.createObjectNode();
        s.put("name", seriesName);
        s.put("type", "line");
        s.put("smooth", true);
        ArrayNode data = m.createArrayNode();
        values.forEach(v -> data.add(v == null ? 0 : v.doubleValue()));
        s.set("data", data);
        series.add(s);
        opt.set("series", series);
        return opt;
    }

    public static ObjectNode scatter(ObjectMapper m, List<double[]> points, String label) {
        ObjectNode opt = m.createObjectNode();
        opt.set("tooltip", tooltip(m, "item"));
        ObjectNode x = m.createObjectNode();
        x.put("type", "value");
        x.put("name", "Probability");
        x.put("min", 0);
        x.put("max", 5);
        opt.set("xAxis", x);
        ObjectNode y = m.createObjectNode();
        y.put("type", "value");
        y.put("name", "Impact");
        y.put("min", 0);
        y.put("max", 5);
        opt.set("yAxis", y);
        ArrayNode series = m.createArrayNode();
        ObjectNode s = m.createObjectNode();
        s.put("name", label);
        s.put("type", "scatter");
        s.put("symbolSize", 16);
        ArrayNode data = m.createArrayNode();
        for (double[] p : points) {
            ArrayNode pt = m.createArrayNode();
            for (double v : p) pt.add(v);
            data.add(pt);
        }
        s.set("data", data);
        series.add(s);
        opt.set("series", series);
        return opt;
    }
}
