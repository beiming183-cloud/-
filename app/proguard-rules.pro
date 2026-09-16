# The app uses no reflection. Keep this file intentionally minimal.

# R8 8.x can report an invalid stack-map warning for javac's synthetic methods
# on the large Java 17 record used for two-variable statistics. Keep this pure
# result carrier intact so release minification cannot rewrite or discard it.
-keep class com.codex.fx991.core.math.StatisticsEngine$TwoVariableResults { *; }
