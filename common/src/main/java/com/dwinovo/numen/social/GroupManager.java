package com.dwinovo.numen.social;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory registry of companion "groups" — a lightweight way for several of one owner's
 * companions to coordinate: an owner creates a group, adds companions to it, and can then
 * delegate a task or broadcast a message to every member ({@code GroupMessaging#deliver}) — and
 * a companion's own reply gets relayed to its groupmates the same way ({@code
 * GroupMessaging#relay}), so two companions can actually carry on a conversation. This class only
 * tracks membership, live status, and the chat transcript so the UI has something to show; the
 * actual message delivery lives in {@code GroupMessaging}.
 *
 * <p>Scoped to a single owner: every member of a group must be a companion owned by that
 * group's owner (checked by the caller, not enforced here — see {@code GroupCommands}). Not
 * persisted across a server restart (same trade-off the original prototype made); a companion
 * that despawns simply stops appearing "online" in {@link #getGroupMemberStatuses}.
 */
public final class GroupManager {

    private static final GroupManager INSTANCE = new GroupManager();

    public static GroupManager instance() {
        return INSTANCE;
    }

    /** How many completed-task lines a group keeps before dropping the oldest. */
    private static final int PROGRESS_LOG_CAP = 50;
    /** How many chat/delegate lines a group keeps before dropping the oldest — this is the
     *  transcript the Group tab renders in place of the old vanilla-chat projection. */
    private static final int CHAT_LOG_CAP = 50;

    private final Map<String, GroupInfo> groups = new ConcurrentHashMap<>();
    private final Map<UUID, MemberStatus> memberStatuses = new ConcurrentHashMap<>();
    private final Map<String, List<TaskCompletion>> progress = new ConcurrentHashMap<>();
    private final Map<String, List<ChatLine>> chatLogs = new ConcurrentHashMap<>();

    private GroupManager() {}

    // ==================== 群组增删 ====================

    /** Create an empty group owned by {@code ownerUuid}. Returns false if the name is taken. */
    public boolean createGroup(String groupName, UUID ownerUuid) {
        return groups.putIfAbsent(groupName, new GroupInfo(groupName, ownerUuid)) == null;
    }

    /** Disband a group (drops membership + progress log; member statuses are left as-is). */
    public boolean disbandGroup(String groupName) {
        progress.remove(groupName);
        chatLogs.remove(groupName);
        return groups.remove(groupName) != null;
    }

    public GroupInfo getGroup(String groupName) {
        return groups.get(groupName);
    }

    /** Every group owned by {@code ownerUuid}, name → info. */
    public Map<String, GroupInfo> listGroups(UUID ownerUuid) {
        Map<String, GroupInfo> out = new LinkedHashMap<>();
        for (Map.Entry<String, GroupInfo> e : groups.entrySet()) {
            if (e.getValue().owner().equals(ownerUuid)) out.put(e.getKey(), e.getValue());
        }
        return out;
    }

    // ==================== 成员 ====================

    /** Add a companion to a group. Returns false if the group doesn't exist. A companion can only
     *  be in one group at a time — adding it here silently drops any prior membership elsewhere,
     *  so {@link #groupOf} (which assumes "at most one") stays a safe assumption rather than a
     *  hope. */
    public boolean addMember(String groupName, UUID companionUuid, String companionName) {
        GroupInfo g = groups.get(groupName);
        if (g == null) return false;
        String previous = groupOf(companionUuid);
        if (previous != null && !previous.equals(groupName)) {
            GroupInfo prevGroup = groups.get(previous);
            if (prevGroup != null) prevGroup.members().remove(companionUuid);
        }
        g.members().put(companionUuid, companionName);
        return true;
    }

    /** Remove a companion from a group (kick / leave — same operation either way). */
    public boolean removeMember(String groupName, UUID companionUuid) {
        GroupInfo g = groups.get(groupName);
        if (g == null) return false;
        return g.members().remove(companionUuid) != null;
    }

    public boolean isMember(String groupName, UUID companionUuid) {
        GroupInfo g = groups.get(groupName);
        return g != null && g.members().containsKey(companionUuid);
    }

    /** The name of the group {@code companionUuid} belongs to, or null (a companion is in at most one). */
    public String groupOf(UUID companionUuid) {
        for (GroupInfo g : groups.values()) {
            if (g.members().containsKey(companionUuid)) return g.name();
        }
        return null;
    }

    // ==================== 状态 ====================
    // NOTE (unwired extension point): nothing in this PR calls setMemberStatus yet, so every
    // member currently shows "idle" in the Group tab's status column. Left in place as the hook
    // a future change (e.g. TaskDispatch/CompanionTickDispatcher reporting in) can call into,
    // rather than half-building a real status source here.

    /** Companion self-reports (idle / working / blocked …) picked up by the UI. */
    public void setMemberStatus(UUID companionUuid, String status, String detail) {
        memberStatuses.put(companionUuid, new MemberStatus(status, detail, System.currentTimeMillis()));
    }

    public MemberStatus getMemberStatus(UUID companionUuid) {
        return memberStatuses.get(companionUuid);
    }

    /** Every known member's latest status, keyed by companion UUID (missing = never reported → idle). */
    public Map<UUID, MemberStatus> getGroupMemberStatuses(String groupName) {
        GroupInfo g = groups.get(groupName);
        if (g == null) return Map.of();
        Map<UUID, MemberStatus> out = new LinkedHashMap<>();
        for (UUID u : g.members().keySet()) {
            MemberStatus s = memberStatuses.get(u);
            if (s != null) out.put(u, s);
        }
        return out;
    }

    // ==================== 任务完成日志 ====================
    // NOTE (unwired extension point): same as above — nothing calls recordTaskCompletion yet.
    // getGroupProgress is ready for a future "show what each member has finished" panel, but
    // there's no data source feeding it in this PR.

    public void recordTaskCompletion(String groupName, String companionName, String taskDesc,
                                      TaskResultType result) {
        List<TaskCompletion> log = progress.computeIfAbsent(groupName, k -> Collections.synchronizedList(new ArrayList<>()));
        log.add(new TaskCompletion(companionName, taskDesc, result, System.currentTimeMillis()));
        while (log.size() > PROGRESS_LOG_CAP) log.remove(0);
    }

    /** Most-recent-last. */
    public List<TaskCompletion> getGroupProgress(String groupName) {
        List<TaskCompletion> log = progress.get(groupName);
        return log == null ? List.of() : List.copyOf(log);
    }

    // ==================== 群聊记录 ====================
    // The Group tab's own transcript — replaces the old projection into vanilla chat. Call once
    // per CHAT/DELEGATE action (not once per recipient), so a broadcast to N members produces one
    // line, not N.

    /** {@code target} is null for a broadcast CHAT line, or the delegate's name for DELEGATE. */
    public void recordChatLine(String groupName, String senderName, String target, String text, boolean delegate) {
        List<ChatLine> log = chatLogs.computeIfAbsent(groupName, k -> Collections.synchronizedList(new ArrayList<>()));
        log.add(new ChatLine(senderName, target, text, delegate, System.currentTimeMillis()));
        while (log.size() > CHAT_LOG_CAP) log.remove(0);
    }

    /** Most-recent-last. */
    public List<ChatLine> getChatLog(String groupName) {
        List<ChatLine> log = chatLogs.get(groupName);
        return log == null ? List.of() : List.copyOf(log);
    }

    // ==================== 数据结构 ====================

    /** A group's roster (companion UUID → last-known name). Membership is a plain mutable map — this
     *  class only owns the wiring, not thread-safety theatrics beyond ConcurrentHashMap. */
    public record GroupInfo(String name, UUID owner, Map<UUID, String> members) {
        GroupInfo(String name, UUID owner) {
            this(name, owner, new ConcurrentHashMap<>());
        }
    }

    public record MemberStatus(String status, String detail, long updatedAt) {}

    public enum TaskResultType { SUCCESS, FAILURE, TIMEOUT, CANCELLED }

    public record TaskCompletion(String companionName, String taskDesc, TaskResultType result, long timestamp) {}

    /** One line of the Group tab's own transcript. {@code target} is null for a broadcast CHAT
     *  line; the delegate's name for a DELEGATE line. */
    public record ChatLine(String sender, String target, String text, boolean delegate, long timestamp) {}
}
