package com.dwinovo.numen.task;

import com.dwinovo.numen.task.CompanionTickDispatcher;
import com.dwinovo.numen.task.TaskRecord;
import com.dwinovo.numen.task.TaskState;
import com.dwinovo.numen.agent.tool.Schema;
import com.dwinovo.numen.agent.tool.NumenTool;
import com.dwinovo.numen.entity.NumenPlayer;
import com.dwinovo.numen.task.TaskResult;
import com.google.gson.JsonObject;

import java.util.Map;
import java.util.function.Consumer;

/** Query tool (instant): read the state of the background task, if any. */
public final class TaskStatusTool implements NumenTool {

    @Override
    public String name() {
        return "task_status";
    }

    @Override
    public String description() {
        return "Read the ONE background task's live state without changing it: id, tool, running/queued, "
                + "elapsed time and remaining budget. Use only when the owner explicitly asks for progress "
                + "or before deciding whether to task_stop. Never call it merely because the owner says "
                + "continue, never poll it, and never use an idle result as permission to repeat a completed "
                + "step; task_finished arrives automatically and the todo/goal decides what comes next.";
    }

    @Override
    public Map<String, Object> parameterSchema() {
        return Schema.object().build();
    }

    @Override
    public void onServerCall(String toolCallId, JsonObject args, NumenPlayer companion, Consumer<String> reply) {
        TaskRecord rec = CompanionTickDispatcher.asyncTaskFor(companion.getUUID());
        if (rec == null) {
            reply.accept(TaskResult.ok("身体空闲,没有后台任务。").toJson());
            return;
        }
        long now = companion.level().getGameTime();
        long elapsedS = rec.getStartedGameTime() >= 0 ? (now - rec.getStartedGameTime()) / 20 : 0;
        long budgetLeftS = Math.max(0, rec.getDeadlineGameTime() - now) / 20;
        String state = rec.getState() == TaskState.RUNNING ? "running" : "queued";
        reply.accept(TaskResult.ok(
                rec.publicId() + "(" + rec.describe() + ") " + state
                        + ",已进行 " + elapsedS + "s,时间预算剩 " + budgetLeftS + "s。",
                Map.of("task_id", rec.publicId(),
                        "task", rec.getToolName(),
                        "state", state,
                        "elapsed_s", elapsedS,
                        "budget_left_s", budgetLeftS)).toJson());
    }
}
