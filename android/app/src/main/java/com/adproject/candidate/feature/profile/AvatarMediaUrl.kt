package com.adproject.candidate.feature.profile

/**
 * Resolves a server-relative avatar path (e.g. `/api/v1/avatars/u1`) into an absolute
 * URL on the configured API origin, appending a cache-busting revision so Coil never
 * serves a stale avatar after an upload/replace. The origin is taken from the API base
 * URL's scheme + host + port (its own `/api/v1/` path segment is dropped, since the
 * relative avatar path already carries it). Anything that is not a single-segment
 * relative path — absolute URLs, `file:`, `content:`, `javascript:`, `data:`, or
 * protocol-relative `//` — is rejected, so an untrusted `avatarUrl` can never point the
 * image loader at an arbitrary host or scheme.
 */
fun resolveAvatarUrl(avatarUrl: String?, revision: Long, apiBaseUrl: String): String? {
    val trimmed = avatarUrl?.trim()
    if (trimmed.isNullOrBlank()) return null
    if (!trimmed.startsWith("/") || trimmed.startsWith("//")) return null
    val origin = originOf(apiBaseUrl) ?: return null
    return "$origin$trimmed?v=$revision"
}

private fun originOf(baseUrl: String): String? {
    val schemeEnd = baseUrl.indexOf("://")
    if (schemeEnd < 0) return null
    val authorityStart = schemeEnd + 3
    val authorityEnd = baseUrl.indexOf('/', authorityStart)
    val authority = if (authorityEnd < 0) baseUrl.substring(authorityStart)
    else baseUrl.substring(authorityStart, authorityEnd)
    if (authority.isEmpty()) return null
    return baseUrl.substring(0, authorityStart) + authority
}
