package com.ryan.smalltalk.tools

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Device-local clock. No permissions, no network. */
class GetTimeTool : Tool {
    override val name = "get_time"
    override val description = "Get the current device time."
    override val requiresNetwork = false
    override suspend fun execute(args: Map<String, String>): String {
        val now = SimpleDateFormat("h:mm a z", Locale.getDefault()).format(Date())
        return "The current time is $now."
    }
}

/** Device-local date. No permissions, no network. */
class GetDateTool : Tool {
    override val name = "get_date"
    override val description = "Get the current device date."
    override val requiresNetwork = false
    override suspend fun execute(args: Map<String, String>): String {
        val today = SimpleDateFormat("EEEE, MMMM d, yyyy", Locale.getDefault()).format(Date())
        return "Today's date is $today."
    }
}
