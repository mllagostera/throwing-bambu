# `core` es determinista y se compara por comportamiento en tests. R8 puede ofuscarlo
# (no usa reflexión), pero no debe eliminar nada alcanzable solo desde el motor.
-keep class dev.bambu.core.** { *; }
