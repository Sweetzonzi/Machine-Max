package io.github.sweetzonzi.machine_max.client.render.gui.hud.molang;

import cn.solarmoon.spark_core.molang.MolangContextRegistry;
import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.init.Node;
import io.github.sweetzonzi.machine_max.MachineMax;

import java.util.*;

/** 客户端主线程的页面绑定器；模板使用 AUI HTML 解析管线实例化。 */
public final class HudPageBinding implements AutoCloseable {
    private static final String FILTER = "data-mm-text,data-mm-num,data-mm-var,data-mm-unit,data-mm-class,data-mm-repeat,data-mm-index";
    private final Document document;
    private final HudRuntimeElement runtime;
    private final String location;
    private final Map<Element, List<Slot>> slots = new LinkedHashMap<>();
    private final Map<Element, Item> items = new IdentityHashMap<>();
    private final List<Region> regions = new ArrayList<>();
    private final Set<Element> scanned = Collections.newSetFromMap(new IdentityHashMap<>());
    private final Set<String> warnings = new HashSet<>();
    private final List<Document.MutationRecord> pending = new ArrayList<>();
    private Document.MutationObserver observer;
    private long generation = -1;
    private boolean closed;

    private record Item(String collection, Object key, String alias) {}

    private final class Slot {
        final Element node;
        final String attribute;
        final String expression;
        String lastClass = "";
        boolean failed;
        Item validatedItem;
        boolean validated;
        Slot(Element node, String attribute, String expression) {
            this.node = node; this.attribute = attribute; this.expression = expression;
        }

        void evaluate(HudRuntimeElement.Frame frame) {
            if (failed) return;
            try {
                Item item = findItem(node);
                HudMolangContext context = runtime.getMolangContext();
                context.useFrame(frame);
                if (!validated || !Objects.equals(validatedItem, item)) {
                    HudExpression.validate(expression, context, item == null ? null : item.collection(), item == null ? null : item.alias());
                    validated = true;
                    validatedItem = item;
                }
                if (item == null) write(context);
                else if (context instanceof WeaponHudMolangContext weapon) {
                    try (var ignored = weapon.enterItem(item.collection(), item.key(), item.alias())) { write(context); }
                } else throw new IllegalArgumentException("普通 HUD 元素不能绑定武器集合");
            } catch (RuntimeException failure) {
                failed = true;
                warn(node.tagName + "[" + attribute + "=" + expression + "]", failure);
                if (attribute.equals("data-mm-text") && node.getChildren().isEmpty()) writeText(node, "MOLANG_ERROR");
                if (attribute.equals("data-mm-class") && !lastClass.isEmpty()) node.getClassList().remove(lastClass);
                if (attribute.equals("data-mm-num")) {
                    String variable = node.getAttribute("data-mm-var");
                    if (variable != null && variable.startsWith("--")) node.setInlineStyleProperty(variable, "0");
                }
            }
        }

        private void write(HudMolangContext context) {
            switch (attribute) {
                case "data-mm-text" -> {
                    if (!node.getChildren().isEmpty()) throw new IllegalArgumentException("data-mm-text 必须位于叶子元素");
                    Object value = MolangContextRegistry.evalAsObject(expression, context);
                    String text = value instanceof Number n ? format(n.doubleValue()) : value == null ? "" : value.toString();
                    writeText(node, text);
                }
                case "data-mm-num" -> {
                    String variable = node.getAttribute("data-mm-var");
                    if (variable == null || !variable.matches("--[a-zA-Z_][a-zA-Z0-9_-]*")) {
                        throw new IllegalArgumentException("data-mm-num 需要合法 CSS 变量 data-mm-var");
                    }
                    double value = MolangContextRegistry.compile(expression, context).evaluate(context);
                    if (!Double.isFinite(value)) throw new IllegalArgumentException("CSS 数值非有限");
                    String text = format(value) + unit(node);
                    if (!text.equals(node.getInlineStylePropertyValue(variable))) node.setInlineStyleProperty(variable, text);
                }
                case "data-mm-class" -> {
                    String value = MolangContextRegistry.compileString(expression, context).evaluate(context);
                    if (value == null || !value.matches("[a-zA-Z0-9_-]+")) throw new IllegalArgumentException("状态不是单个 class token");
                    String token = "is-" + value;
                    if (!lastClass.equals(token)) {
                        if (!lastClass.isEmpty()) node.getClassList().remove(lastClass);
                        node.getClassList().add(token);
                        lastClass = token;
                    }
                }
                default -> throw new IllegalArgumentException(attribute);
            }
        }
    }

    private static String unit(Element node) {
        String unit = node.getAttribute("data-mm-unit");
        if (unit == null || unit.isEmpty()) return "";
        if (!Set.of("%", "px", "deg", "rad", "em", "rem", "vw", "vh", "s", "ms").contains(unit)) {
            throw new IllegalArgumentException("不支持的 CSS 单位: " + unit);
        }
        return unit;
    }

    private static void writeText(Element node, String text) {
        if (!Objects.equals(node.getTextContent(), text) || !Objects.equals(node.innerText, text)) {
            node.setTextContent(text);
        }
    }

    private static String format(double value) {
        if (!Double.isFinite(value)) throw new IllegalArgumentException("HUD 数值非有限");
        return value == Math.rint(value) && Math.abs(value) < 1e16 ? Long.toString((long) value) : Double.toString(value);
    }

    private final class Region {
        final Node start, end;
        final String markup, collection, alias;
        final Map<Object, Element> rows = new LinkedHashMap<>();

        Region(Element template) {
            collection = template.getAttribute("data-mm-repeat");
            String index = template.getAttribute("data-mm-index");
            alias = index == null || index.isEmpty() ? null : index;
            if (!(runtime instanceof WeaponHudElement)) throw new IllegalArgumentException("武器集合要求武器 HUD 宿主");
            if (!Set.of("weapon.tubes", "weapon.belts").contains(collection)) throw new IllegalArgumentException("未知集合: " + collection);
            if (alias != null && !alias.matches("[a-z_][a-z0-9_]*")) throw new IllegalArgumentException("非法下标别名: " + alias);
            validateTemplate(template, true);
            markup = template.getOuterHTML();
            var parent = template.getParentNode();
            start = document.createComment("mm-repeat " + collection);
            end = document.createComment("/mm-repeat");
            parent.insertBefore(start, template);
            parent.insertBefore(end, template);
            parent.removeChild(template);
        }

        void reconcile(HudRuntimeElement.Frame frame) {
            var keys = collection.equals("weapon.tubes")
                    ? frame.weapon().tubes().stream().map(t -> (Object) t.key()).toList()
                    : frame.weapon().belts().stream().map(b -> (Object) b.key()).toList();
            Set<Object> wanted = new HashSet<>(keys);
            rows.entrySet().removeIf(entry -> {
                if (wanted.contains(entry.getKey())) return false;
                detach(entry.getValue()); return true;
            });
            Node cursor = start.getNextSibling();
            Node parent = end.getParentNode();
            for (Object key : keys) {
                Element row = rows.get(key);
                if (row == null || row.getParentNode() != parent) {
                    if (row != null) detach(row);
                    row = document.createHTML(markup);
                    if (row == null) throw new IllegalArgumentException("无法实例化模板: " + collection);
                    row.removeAttribute("data-mm-repeat");
                    items.put(row, new Item(collection, key, alias));
                    rows.put(key, row);
                    parent.insertBefore(row, cursor);
                    scan(row);
                } else if (cursor != row) parent.insertBefore(row, cursor);
                cursor = row.getNextSibling();
            }
        }
    }

    public HudPageBinding(Document document, HudRuntimeElement runtime, String location) {
        this.document = document; this.runtime = runtime; this.location = location;
    }

    public void refresh() {
        if (closed || document.isDisposed() || document.body == null) return;
        if (generation != document.getRefreshGeneration()) {
            clear();
            generation = document.getRefreshGeneration();
            observer = document.createMutationObserver(records -> {
                if (records instanceof List<?> list) for (Object record : list) {
                    if (record instanceof Document.MutationRecord mutation) pending.add(mutation);
                }
            });
            scan(document.body);
            observer.observe(document.body, true, true, false, true, false, false, FILTER);
        }
        pending.addAll(observer.takeRecords());
        var changes = new ArrayList<>(pending);
        pending.clear();
        for (var mutation : changes) {
            if (!document.body.contains(mutation.target)) continue;
            if (mutation.type.equals("attributes") && mutation.target instanceof Element element) {
                List<Slot> removed = slots.remove(element);
                if (removed != null) for (Slot slot : removed) {
                    if (!slot.lastClass.isEmpty()) element.getClassList().remove(slot.lastClass);
                }
                scanned.remove(element);
                scan(element);
            } else for (Node added : mutation.addedNodes) if (added instanceof Element element) scan(element);
        }
        var frame = runtime.frame();
        regions.removeIf(region -> {
            if (document.body.contains(region.start) && document.body.contains(region.end)) return false;
            region.rows.values().forEach(this::detach); return true;
        });
        for (var region : List.copyOf(regions)) {
            try { region.reconcile(frame); }
            catch (RuntimeException failure) { warn("repeat=" + region.collection, failure); }
        }
        slots.keySet().removeIf(node -> !document.body.contains(node));
        items.keySet().removeIf(node -> !document.body.contains(node));
        scanned.removeIf(node -> !document.body.contains(node));
        for (var entries : List.copyOf(slots.values())) for (Slot slot : entries) slot.evaluate(frame);
    }

    private void scan(Element element) {
        if (!scanned.add(element)) return;
        if (element.getAttributes().containsKey("data-mm-repeat")) {
            try {
                if (findItem(element) != null) throw new IllegalArgumentException("不支持嵌套重复区域");
                regions.add(new Region(element));
            } catch (RuntimeException failure) {
                warn("repeat=" + element.getAttribute("data-mm-repeat"), failure);
                element.setInlineStyleProperty("display", "none", "important");
            }
            return;
        }
        List<Slot> found = new ArrayList<>();
        for (String attribute : List.of("data-mm-text", "data-mm-num", "data-mm-class")) {
            String value = element.getAttribute(attribute);
            if (value != null) found.add(new Slot(element, attribute, value));
        }
        if (!found.isEmpty()) slots.put(element, found);
        for (Element child : List.copyOf(element.getChildren())) scan(child);
    }

    private static void validateTemplate(Element element, boolean root) {
        if (element.getAttributes().containsKey("id")) throw new IllegalArgumentException("重复模板不能包含固定 id");
        if (!root && element.getAttributes().containsKey("data-mm-repeat")) throw new IllegalArgumentException("不支持嵌套重复区域");
        for (var child : element.getChildren()) validateTemplate(child, false);
    }

    private Item findItem(Node node) {
        for (Node current = node; current != null; current = current.getParentNode()) {
            Item item = items.get(current);
            if (item != null) return item;
        }
        return null;
    }

    private void detach(Element row) {
        Node parent = row.getParentNode();
        if (parent != null) parent.removeChild(row);
        slots.keySet().removeIf(row::contains);
        scanned.removeIf(row::contains);
        items.remove(row);
    }

    private void warn(String slot, RuntimeException failure) {
        String message = location + ":" + document.getPath() + ":" + slot + ":" + failure.getMessage();
        if (warnings.add(message)) MachineMax.LOGGER.warn("HUD 绑定失败: {}", message);
    }

    private void clear() {
        if (observer != null) observer.disconnect();
        observer = null;
        slots.clear(); items.clear(); regions.clear(); scanned.clear(); pending.clear(); warnings.clear();
    }

    @Override public void close() { closed = true; clear(); }
}
