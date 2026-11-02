# 保留行号信息，便于线上崩溃栈回溯。
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Android 平台与 Compose 已自带消费者规则，此处只保留应用侧显式需要的部分。
# 应用不使用反射、不使用序列化框架，因此无需额外 keep 规则。
