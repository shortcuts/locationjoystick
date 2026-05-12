package com.locationjoystick.core.common.util

import kotlinx.serialization.json.Json

val AppJson: Json =
    Json {
        ignoreUnknownKeys = true
        prettyPrint = false
    }
