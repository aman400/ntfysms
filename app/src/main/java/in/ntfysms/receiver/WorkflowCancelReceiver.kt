package `in`.ntfysms.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import `in`.ntfysms.domain.engine.ExecutionJobRegistry

/**
 * BroadcastReceiver for handling notification Cancel action taps during active workflow execution.
 */
class WorkflowCancelReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        if (intent == null || context == null) return
        if (intent.action == "in.ntfysms.ACTION_CANCEL_WORKFLOW") {
            val workflowId = intent.getStringExtra("workflow_id")
            if (!workflowId.isNullOrBlank()) {
                ExecutionJobRegistry.cancelJob(workflowId)
            } else {
                ExecutionJobRegistry.cancelAllJobs()
            }
        }
    }
}
