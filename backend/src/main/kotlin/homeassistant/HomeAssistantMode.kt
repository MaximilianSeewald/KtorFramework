package com.loudless.homeassistant

object HomeAssistantMode {
    const val userName = "ha-user"
    const val userGroupName = "ha-instance"
    const val password = "home-assistant-instance-user"
    const val lovelaceCardVersion = "1.1.6"
    const val localLovelaceResourceUrl = "/local/ktor-lovelace-cards-$lovelaceCardVersion.js"
    const val lovelaceCardFileName = "ktor-lovelace-cards.js"
    const val versionedLovelaceCardFileName = "ktor-lovelace-cards-$lovelaceCardVersion.js"
    const val configurationFilePath = "/homeassistant/configuration.yaml"

    val enabled: Boolean
        get() = System.getenv("HA_MODE")?.equals("true", ignoreCase = true) == true

    val supervisorToken: String?
        get() = System.getenv("SUPERVISOR_TOKEN")?.takeIf { it.isNotBlank() }

    val ingressBaseUrl: String?
        get() = listOf(
            "INGRESS_ENTRY",
            "HASSIO_INGRESS_ENTRY",
            "SUPERVISOR_INGRESS_ENTRY"
        )
            .firstNotNullOfOrNull { name -> System.getenv(name)?.takeIf { it.isNotBlank() } }
            ?.replace(Regex("/?$"), "/")

    val homeAssistantBaseUrl: String
        get() = System.getenv("SUPERVISOR_URL")?.takeIf { it.isNotBlank() } ?: "http://supervisor"
}
