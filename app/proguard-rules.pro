# `core` is deterministic and compared by behaviour in tests. R8 may obfuscate it (it
# uses no reflection), but must not strip anything reachable only from the engine.
-keep class com.vansid.panda.core.** { *; }
