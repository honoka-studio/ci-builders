@file:Suppress("unused")

package de.honoka.ci.builder.npm.library

import de.honoka.ci.builder.args

fun build() {
    println("hello npm build: ${args.toList()}")
}
