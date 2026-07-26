package com.dwinovo.numen.event;

import com.dwinovo.numen.entity.Companions;
import com.dwinovo.numen.entity.NumenPlayer;

import java.util.Map;

/**
 * 事件登记处:游戏世界通知 agent 的事件类型在此登记,XML 组装与转义在此收口——
 * 散装手搓 {@code <event>} 字符串的写法到此为止。
 *
 * <p>登记处<b>不管时机</b>。事件何时被模型消费,由客户端收件箱按"发生时大脑的
 * 状态"三态路由(回合中→贴边界;有后台任务→立刻开轮;全闲→躺着搭车),
 * 事件类型没有贵贱之分。唯一的例外位 {@code principal}(活人在说话)属于
 * {@link Companions#emitEvent} 的入口,外部桥接直接用,不经此处。
 *
 * <p>客户端内部注入的事件(死亡叙事、人设切换)不经服务端信道,不在此登记,
 * 但同属 {@code <event>} 词汇表:death / persona-change。
 */
public final class GameEvents {

    /** 登记表:目前的服务端事件词汇。 */
    public enum Kind {
        /** 异步任务收尾(status: done / failed / timeout / stopped)。 */
        TASK_FINISHED("task_finished"),
        /** 身体自理日记(BodyLog 出口)。 */
        BODY_LOG("body_log"),
        /** 同伴自己跨了维度。 */
        DIMENSION_CHANGE("dimension_change");

        private final String kind;

        Kind(String kind) {
            this.kind = kind;
        }

        public String kindName() { return kind; }
    }

    private GameEvents() {}

    /** 组装并发射一个世界事件(principal 恒为 false——事实不配自定紧急度)。 */
    public static void emit(NumenPlayer body, Kind kind, Map<String, String> attrs, String text) {
        Companions.emitEvent(body, xml(kind, attrs, text), false);
    }

    /**
     * 发射一个事先获模型/主人授权的一次性空闲唤醒。事件类型本身仍然没有
     * 紧急度;开轮资格来自此次显式授权,不是事实生产者自报紧急。
     *
     * @return 在线主人存在且包已发送时为 true
     */
    public static boolean authorizedWake(NumenPlayer body, Kind kind,
                                         Map<String, String> attrs, String text) {
        return authorizedWake(body, kind.kind, attrs, text);
    }

    /** Internal implementation behind the public API facade. */
    public static boolean authorizedWake(NumenPlayer body, String kind,
                                         Map<String, String> attrs, String text) {
        return Companions.emitAuthorizedWake(body, xml(kind, attrs, text));
    }

    private static String xml(Kind kind, Map<String, String> attrs, String text) {
        return xml(kind.kind, attrs, text);
    }

    private static String xml(String kind, Map<String, String> attrs, String text) {
        StringBuilder sb = new StringBuilder("<event kind=\"").append(escapeAttribute(kind)).append('"');
        if (attrs != null) {
            for (Map.Entry<String, String> e : attrs.entrySet()) {
                sb.append(' ').append(e.getKey()).append("=\"")
                        .append(escapeAttribute(e.getValue())).append('"');
            }
        }
        sb.append('>').append(escape(text)).append("</event>");
        return sb.toString();
    }

    /** 异步任务收尾事件。{@code status} ∈ done / failed / timeout / stopped。 */
    public static void taskFinished(NumenPlayer body, String taskId, String tool,
                                    String status, String message) {
        java.util.LinkedHashMap<String, String> attrs = new java.util.LinkedHashMap<>();
        attrs.put("id", taskId);
        attrs.put("task", tool);
        attrs.put("status", status);
        emit(body, Kind.TASK_FINISHED, attrs, message);
    }

    /** XML 词汇表用尖括号,正文里的尖括号一律圆括号化,防注入也防解析歧义。 */
    public static String escape(String s) {
        return s == null ? "" : s.replace('<', '(').replace('>', ')');
    }

    private static String escapeAttribute(String s) {
        return escape(s).replace("&", "&amp;").replace("\"", "&quot;");
    }
}
