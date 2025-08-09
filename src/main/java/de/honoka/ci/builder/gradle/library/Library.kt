@file:Suppress("unused")

package de.honoka.ci.builder.gradle.library

import de.honoka.ci.builder.args

fun build() {
    println("hello gradle build: ${args.toList()}")
}
