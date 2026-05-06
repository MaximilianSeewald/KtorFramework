package com.loudless.homeassistant

object HomeAssistantMode {
    const val userName = "ha-user"
    const val userGroupName = "ha-instance"
    const val password = "home-assistant-instance-user"

    val enabled: Boolean
        get() = System.getenv("HA_MODE")?.equals("true", ignoreCase = true) == true
}
