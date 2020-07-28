package(default_visibility = ["//visibility:private"])

licenses(["notice"])  # Apache 2.0

android_binary(
    name = "TfLiteCameraDemo",
    srcs = glob(["java/**/*.java"]),
    assets = [
        "@tflite_mobilenet//:retrained_labels.txt",
        "@tflite_mobilenet//:optimized_graph.lite",
    ],
    assets_dir = "",
    custom_package = "com.example.android.tflitecamerademo",
    manifest = "AndroidManifest.xml",
    nocompress_extensions = [
        ".lite",
    ],
    resource_files = glob(["res/**"]),
    # In some platforms we don't have an Android SDK/NDK and this target
    # can't be built. We need to prevent the build system from trying to
    # use the target in that case.
    tags = ["manual"],
    deps = [
        "//tensorflow/contrib/lite/java:tensorflowlite",
        "//tensorflow/contrib/lite/java/src/testhelper/java/org/tensorflow/lite:testhelper",
        "@androidsdk//com.android.support:support-v13-25.2.0",
        "@androidsdk//com.android.support:support-v4-25.2.0",
    ],
)

filegroup(
    name = "all_files",
    srcs = glob(
        ["**/*"],
        exclude = [
            "**/METADATA",
            "**/OWNERS",
        ],
    ),
    visibility = ["//tensorflow:__subpackages__"],
)
