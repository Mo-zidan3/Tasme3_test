package com.example.tools.eval

/** Phase 1: JVM CLI scorer consuming eval/hypotheses JSON (§13.2). Phase 0 stub. */
fun main(args: Array<String>) {
    if (args.isEmpty()) {
        println("usage: eval <hypotheses-dir> <labels-dir>")
        return
    }
    println("tools:eval stub — implements §13 metrics in Phase 1")
}
