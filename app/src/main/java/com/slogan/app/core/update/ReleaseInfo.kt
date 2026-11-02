package com.slogan.app.core.update

/** 更新链路里所有可预期的失败都用这个类型，`message` 直接展示给用户。 */
class UpdateException(message: String, cause: Throwable? = null) : Exception(message, cause)

/** Release 里的一个附件。 */
data class UpdateAsset(
    val name: String,
    val downloadUrl: String,
    val sizeBytes: Long,
    /** GitHub 资产自带的 `sha256:` 摘要，老资产可能为 null。 */
    val digest: String? = null,
    /** 同名的 `.sha256` 附件地址；仓库没上传校验和时为 null。 */
    val checksumUrl: String? = null,
)

/** 一次发布中与「应用内更新」相关的信息。 */
data class ReleaseInfo(
    val tagName: String,
    val version: VersionName,
    val publishedAt: String,
    val notes: String,
    val pageUrl: String,
    val apk: UpdateAsset,
)
