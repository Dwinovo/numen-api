package com.dwinovo.numen.client.screen.group;

import com.dwinovo.numen.client.screen.FlatEditBox;
import com.dwinovo.numen.client.screen.Nb;
import com.dwinovo.numen.client.screen.SimpleButton;
import com.dwinovo.numen.client.screen.UiTheme;
import com.dwinovo.numen.client.social.GroupClientState;
import com.dwinovo.numen.network.payload.GroupActionPayload;
import com.dwinovo.numen.network.payload.GroupSyncPayload;
import com.dwinovo.numen.network.payload.RequestGroupsPayload;
import com.dwinovo.numen.platform.Services;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.UUID;

/**
 * The Group tab of {@link com.dwinovo.numen.client.screen.NumenScreen}: create/disband groups of
 * companions, add/remove members (by clicking their avatar on the left rail while a group is
 * selected — see {@link #onRailAvatarClicked}), and either broadcast a message to every member
 * or delegate a task to one selected member. Companions also relay their own replies to each
 * other while grouped (see {@code GroupRelayPayload}), and every line — owner→companion,
 * companion→companion — shows up in this tab's own transcript panel; nothing is echoed into
 * vanilla chat.
 *
 * <p>Follows {@code ItemsView}/{@code SettingsView}'s split: geometry/widget registration comes
 * from the screen through {@link Host}; group *data* comes from {@link GroupClientState}, kept
 * fresh by the server pushing a full {@link GroupSyncPayload} after every action.
 */
public final class GroupView {

    public interface Host {
        <T extends AbstractWidget> T add(T w);
        void rebuild();
        Font font();
        int left();
        int top();
        int panelW();
        int panelH();
    }

    private static final int PAD = 8;
    private static final int HEADER_H = 22;
    private static final int ROW_H = 14;
    private static final int FIELD_INSET_X = 5, FIELD_INSET_Y = 4;

    private final Host host;

    private String selectedGroup;
    private UUID selectedTarget;   // null = message goes to the whole group

    private FlatEditBox newGroupInput;
    private FlatEditBox messageInput;

    public GroupView(Host host) {
        this.host = host;
    }

    /** Called once when the tab is selected — the server only pushes on change, so pull once up front. */
    public void onOpened() {
        if (Services.NETWORK != null) {
            Services.NETWORK.sendToServer(RequestGroupsPayload.INSTANCE);
        }
    }

    public void clearWidgets() {
        newGroupInput = null;
        messageInput = null;
    }

    public void buildWidgets() {
        int x = host.left() + PAD;
        int y = host.top() + HEADER_H + 4;

        newGroupInput = new FlatEditBox(host.font(), x + FIELD_INSET_X, y + FIELD_INSET_Y,
                listW() - 44 - FIELD_INSET_X * 2, ROW_H - FIELD_INSET_Y * 2, Component.literal(""));
        newGroupInput.setMaxLength(GroupActionPayload.MAX_NAME);
        newGroupInput.setBordered(false);
        newGroupInput.setTextColor(UiTheme.current().text());
        newGroupInput.setHint(Component.translatable("numen.group.new_hint"));
        host.add(newGroupInput);
        host.add(new SimpleButton(x + listW() - 40, y, 40, ROW_H, Component.translatable("numen.group.create"),
                b -> doCreate()).primary());

        if (selectedGroup != null) {
            int mx = x + listW() + 10;
            int sendY = sendY();
            messageInput = new FlatEditBox(host.font(), mx + FIELD_INSET_X, sendY + FIELD_INSET_Y,
                    rightW(mx) - 56 - FIELD_INSET_X * 2, ROW_H - FIELD_INSET_Y * 2, Component.literal(""));
            messageInput.setMaxLength(GroupActionPayload.MAX_TEXT);
            messageInput.setBordered(false);
            messageInput.setTextColor(UiTheme.current().text());
            messageInput.setHint(Component.translatable(
                    selectedTarget == null ? "numen.group.chat_hint" : "numen.group.delegate_hint"));
            host.add(messageInput);
            host.add(new SimpleButton(mx + rightW(mx) - 52, sendY, 52, ROW_H,
                    Component.translatable("numen.group.send"), b -> doSend()).primary());
        }
    }

    private int listW() {
        return Math.min(140, (host.panelW() - PAD * 3) / 2);
    }

    /** Top of the message-input row — shared by {@link #buildWidgets} (to place the widgets) and
     *  {@link #render} (to know how much room the transcript panel above it has to work with). */
    private int sendY() {
        return host.top() + host.panelH() - PAD - ROW_H - 4;
    }

    private int rightW(int rightX) {
        return host.left() + host.panelW() - PAD - rightX;
    }

    public void render(GuiGraphics g, int mouseX, int mouseY) {
        UiTheme t = UiTheme.current();
        int x = host.left() + PAD;
        int y = host.top() + HEADER_H + 4 + ROW_H + 6;
        int rightX = x + listW() + 10;

        List<GroupSyncPayload.GroupDto> groups = GroupClientState.groups();
        if (groups.stream().noneMatch(gr -> gr.name().equals(selectedGroup))) selectedGroup = null;

        // ---- left: group list ----
        for (GroupSyncPayload.GroupDto gr : groups) {
            boolean active = gr.name().equals(selectedGroup);
            int color = active ? t.cta() : (hovered(mouseX, mouseY, x, y, listW(), ROW_H) ? t.text() : t.textDim());
            Nb.text(g, host.font(), gr.name() + "  (" + gr.members().size() + ")", x, y + 2, color);
            y += ROW_H;
        }
        if (groups.isEmpty()) {
            Nb.text(g, host.font(), I18n.get("numen.group.empty"), x, y + 2, t.faint());
        }
        Nb.text(g, host.font(), I18n.get("numen.group.rail_hint"), x, host.top() + host.panelH() - PAD - ROW_H, t.faint());

        // ---- right: selected group's members ----
        if (selectedGroup == null) return;
        GroupSyncPayload.GroupDto gr = groups.stream().filter(g2 -> g2.name().equals(selectedGroup)).findFirst().orElse(null);
        if (gr == null) return;

        int ry = host.top() + HEADER_H + 4;
        Nb.text(g, host.font(), gr.name(), rightX, ry, t.text());
        int bx = rightX + rightW(rightX) - 44;
        // "解散" drawn as a plain hit-tested label (kept out of the widget list — see mouseClicked)
        Nb.text(g, host.font(), I18n.get("numen.group.disband"), bx, ry, t.fail());
        ry += ROW_H + 2;

        for (GroupSyncPayload.MemberDto m : gr.members()) {
            boolean isTarget = m.uuid().equals(selectedTarget);
            int rowColor = isTarget ? t.cta() : t.text();
            Nb.text(g, host.font(), m.name(), rightX, ry + 2, rowColor);
            Nb.text(g, host.font(), "[" + m.status() + "]", rightX + 70, ry + 2, statusColor(t, m.status()));
            if (!m.detail().isBlank()) {
                Nb.text(g, host.font(), m.detail(), rightX + 110, ry + 2, t.textDim());
            }
            Nb.text(g, host.font(), "✕", rightX + rightW(rightX) - 10, ry + 2, t.faint());
            ry += ROW_H;
        }
        if (gr.members().isEmpty()) {
            Nb.text(g, host.font(), I18n.get("numen.group.no_members"), rightX, ry + 2, t.faint());
            ry += ROW_H;
        }

        // ---- chat transcript — replaces the old vanilla-chat projection; bottom-anchored
        // (most recent line lowest, oldest dropped first if the log outgrows the panel). ----
        int logTop = ry + 4;
        int logBottom = sendY() - 4;
        if (logBottom > logTop) {
            List<GroupSyncPayload.ChatLineDto> log = gr.chatLog();
            if (log.isEmpty()) {
                Nb.text(g, host.font(), I18n.get("numen.group.chat_log_empty"), rightX, logTop, t.faint());
            } else {
                int maxLines = Math.max(1, (logBottom - logTop) / ROW_H);
                int from = Math.max(0, log.size() - maxLines);
                int w = rightW(rightX);
                int ly = logTop;
                for (int i = from; i < log.size(); i++) {
                    GroupSyncPayload.ChatLineDto line = log.get(i);
                    String prefix = line.delegate() ? ("→" + line.target() + "：") : "群：";
                    Nb.text(g, host.font(), truncate(host.font(), prefix + line.text(), w),
                            rightX, ly + 2, line.delegate() ? t.cta() : t.textDim());
                    ly += ROW_H;
                }
            }
        }
    }

    /** Shrink {@code s} until "s…" fits {@code maxWidth} px (same trim-from-the-end pattern
     *  {@code ChatView} uses) — the transcript panel has no wrapping, one line per message. */
    private static String truncate(Font font, String s, int maxWidth) {
        if (font.width(s) <= maxWidth) return s;
        String base = s;
        while (base.length() > 1 && font.width(base + "…") > maxWidth) base = base.substring(0, base.length() - 1);
        return base + "…";
    }

    private int statusColor(UiTheme t, String status) {
        return switch (status) {
            case "working" -> t.run();
            case "blocked", "failed" -> t.fail();
            default -> t.ok();
        };
    }

    private boolean hovered(int mx, int my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    /** Hit-tests the parts of the tab that are NOT vanilla widgets (list rows, member rows, ✕, 解散).
     *  Returns true if the click was consumed. */
    public boolean mouseClicked(double mouseX, double mouseY) {
        int x = host.left() + PAD;
        int y = host.top() + HEADER_H + 4 + ROW_H + 6;
        List<GroupSyncPayload.GroupDto> groups = GroupClientState.groups();
        for (GroupSyncPayload.GroupDto gr : groups) {
            if (hovered((int) mouseX, (int) mouseY, x, y, listW(), ROW_H)) {
                selectedGroup = gr.name();
                selectedTarget = null;
                host.rebuild();
                return true;
            }
            y += ROW_H;
        }
        if (selectedGroup == null) return false;
        GroupSyncPayload.GroupDto gr = groups.stream().filter(g -> g.name().equals(selectedGroup)).findFirst().orElse(null);
        if (gr == null) return false;
        int rightX = x + listW() + 10;
        int ry = host.top() + HEADER_H + 4;
        int bx = rightX + rightW(rightX) - 44;
        if (hovered((int) mouseX, (int) mouseY, bx, ry, 44, ROW_H)) {   // 解散
            send(new GroupActionPayload(GroupActionPayload.Action.DISBAND, selectedGroup, null, null));
            selectedGroup = null;
            return true;
        }
        ry += ROW_H + 2;
        for (GroupSyncPayload.MemberDto m : gr.members()) {
            if (hovered((int) mouseX, (int) mouseY, rightX + rightW(rightX) - 12, ry, 10, ROW_H)) {   // ✕ kick
                send(new GroupActionPayload(GroupActionPayload.Action.REMOVE_MEMBER, selectedGroup, m.uuid(), null));
                return true;
            }
            if (hovered((int) mouseX, (int) mouseY, rightX, ry, rightW(rightX) - 14, ROW_H)) {   // select as delegate target
                selectedTarget = m.uuid().equals(selectedTarget) ? null : m.uuid();
                host.rebuild();
                return true;
            }
            ry += ROW_H;
        }
        return false;
    }

    /** Called by {@code NumenScreen} when the player clicks a rail avatar while the Group tab is
     *  open and a group is selected: toggles that companion's membership. Returns true if consumed
     *  (so the screen doesn't also treat it as a "switch active companion" click). */
    public boolean onRailAvatarClicked(UUID companionUuid) {
        if (selectedGroup == null) return false;
        GroupSyncPayload.GroupDto gr = GroupClientState.groups().stream()
                .filter(g -> g.name().equals(selectedGroup)).findFirst().orElse(null);
        boolean isMember = gr != null && gr.members().stream().anyMatch(m -> m.uuid().equals(companionUuid));
        send(new GroupActionPayload(isMember ? GroupActionPayload.Action.REMOVE_MEMBER
                : GroupActionPayload.Action.ADD_MEMBER, selectedGroup, companionUuid, null));
        return true;
    }

    /** True while a group is selected — {@code NumenScreen} checks this before routing a rail click
     *  the normal "switch companion" way. */
    public boolean capturesRailClicks() {
        return selectedGroup != null;
    }

    private void doCreate() {
        if (newGroupInput == null) return;
        String name = newGroupInput.getValue().trim();
        if (name.isEmpty()) return;
        send(new GroupActionPayload(GroupActionPayload.Action.CREATE, name, null, null));
        newGroupInput.setValue("");
        selectedGroup = name;
        host.rebuild();
    }

    private void doSend() {
        if (messageInput == null || selectedGroup == null) return;
        String text = messageInput.getValue().trim();
        if (text.isEmpty()) return;
        GroupActionPayload.Action action = selectedTarget == null
                ? GroupActionPayload.Action.CHAT : GroupActionPayload.Action.DELEGATE;
        send(new GroupActionPayload(action, selectedGroup, selectedTarget, text));
        messageInput.setValue("");
    }

    private void send(GroupActionPayload payload) {
        Services.NETWORK.sendToServer(payload);
    }
}
