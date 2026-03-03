package com.tacz.legacy.common.application.gunpack

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.tacz.legacy.common.domain.gunpack.GunBoltType
import com.tacz.legacy.common.domain.gunpack.GunBulletData
import com.tacz.legacy.common.domain.gunpack.GunPackCompatibilityReport
import com.tacz.legacy.common.domain.gunpack.GunData
import com.tacz.legacy.common.domain.gunpack.GunDefaults
import com.tacz.legacy.common.domain.gunpack.GunFeedType
import com.tacz.legacy.common.domain.gunpack.GunFireMode
import com.tacz.legacy.common.domain.gunpack.GunInaccuracyData
import com.tacz.legacy.common.domain.gunpack.GunRecoilData
import com.tacz.legacy.common.domain.gunpack.GunRecoilKeyFrameData
import com.tacz.legacy.common.domain.gunpack.GunReloadData

public class GunPackCompatibilityParser {

    public fun parseGunDataJson(json: String, sourceId: String = "unknown"): GunPackParseResult {
        val report = GunPackCompatibilityReport()
        val root = parseRoot(json, report) ?: return GunPackParseResult(null, report)

        val ammoId = readString(
            root = root,
            primary = "ammo",
            aliases = listOf("ammo_id"),
            field = "ammo",
            report = report
        )

        val rawGunId = readString(
            root = root,
            primary = "id",
            aliases = listOf("gun_id", "name"),
            field = "id",
            report = report
        )
        val gunId = normalizeGunId(rawGunId, sourceId, report)

        if (ammoId.isNullOrBlank()) {
            report.addError(
                code = IssueCode.MISSING_REQUIRED_FIELD,
                field = "ammo",
                message = "Missing required field 'ammo'."
            )
            return GunPackParseResult(null, report)
        }

        val ammoAmount = readInt(
            root = root,
            primary = "ammo_amount",
            aliases = listOf("magazine_size"),
            field = "ammo_amount",
            defaultValue = GunDefaults.AMMO_AMOUNT,
            report = report
        )

        val extendedMagAmmoAmount = readIntList(
            root = root,
            primary = "extended_mag_ammo_amount",
            aliases = listOf("extended_magazine_size"),
            field = "extended_mag_ammo_amount",
            report = report
        )?.also {
            if (it.isNotEmpty() && it.size < 3) {
                report.addWarning(
                    code = IssueCode.INVALID_FIELD_VALUE,
                    field = "extended_mag_ammo_amount",
                    message = "Expected at least 3 entries for 'extended_mag_ammo_amount'."
                )
            }
        }

        val canCrawl = readBoolean(
            root = root,
            primary = "can_crawl",
            aliases = emptyList(),
            field = "can_crawl",
            defaultValue = true,
            report = report
        )

        val canSlide = readBoolean(
            root = root,
            primary = "can_slide",
            aliases = emptyList(),
            field = "can_slide",
            defaultValue = true,
            report = report
        )

        val boltType = readEnum(
            root = root,
            primary = "bolt",
            aliases = emptyList(),
            field = "bolt",
            defaultValue = GunBoltType.OPEN_BOLT,
            report = report,
            converter = GunBoltType::fromSerialized
        )

        val roundsPerMinute = readInt(
            root = root,
            primary = "rpm",
            aliases = listOf("rounds_per_minute"),
            field = "rpm",
            defaultValue = GunDefaults.RPM,
            report = report
        )

        val aimTimeSeconds = readFloat(
            root = root,
            primary = "aim_time",
            aliases = listOf("aimTime"),
            field = "aim_time",
            defaultValue = GunDefaults.AIM_TIME_SECONDS,
            report = report
        )

        val fireModes = readFireModes(root, report)
        val crawlRecoilMultiplier = readFloat(
            root = root,
            primary = "crawl_recoil_multiplier",
            aliases = emptyList(),
            field = "crawl_recoil_multiplier",
            defaultValue = GunDefaults.CRAWL_RECOIL_MULTIPLIER,
            report = report
        )
        val recoil = readRecoil(root, report)
        val inaccuracy = readInaccuracy(root, report)
        val reload = readReload(root, report)
        val bullet = readBullet(root, report)

        val normalized = normalize(
            GunData(
                sourceId = sourceId,
                gunId = gunId,
                ammoId = ammoId,
                ammoAmount = ammoAmount,
                extendedMagAmmoAmount = extendedMagAmmoAmount,
                canCrawl = canCrawl,
                canSlide = canSlide,
                boltType = boltType,
                roundsPerMinute = roundsPerMinute,
                aimTimeSeconds = aimTimeSeconds,
                fireModes = fireModes,
                crawlRecoilMultiplier = crawlRecoilMultiplier,
                recoil = recoil,
                inaccuracy = inaccuracy,
                bullet = bullet,
                reload = reload
            ),
            report
        )

        return GunPackParseResult(normalized, report)
    }

    private fun normalizeGunId(
        rawGunId: String?,
        sourceId: String,
        report: GunPackCompatibilityReport
    ): String {
        val candidate = rawGunId?.trim().orEmpty()
        if (candidate.isNotEmpty()) {
            return toSnakeCase(candidate)
        }

        val fallback = deriveFallbackGunIdFromSourceId(sourceId)
        report.addInfo(
            code = IssueCode.MISSING_OPTIONAL_FIELD,
            field = "id",
            message = "Field 'id' is missing, fallback to derived id '$fallback'."
        )
        return toSnakeCase(fallback)
    }

    private fun deriveFallbackGunIdFromSourceId(sourceId: String): String {
        val filename = sourceId.substringAfterLast('/').substringBeforeLast('.')
        val withoutNamespace = filename.substringAfterLast(':')
        return withoutNamespace.removeSuffix("_data").ifBlank { "unknown_gun" }
    }

    private fun toSnakeCase(value: String): String {
        val lowered = value.trim().lowercase()
        if (lowered.isEmpty()) {
            return "unknown_gun"
        }

        val snake = buildString(lowered.length) {
            lowered.forEach { ch ->
                append(if (ch.isLetterOrDigit()) ch else '_')
            }
        }.replace("__+".toRegex(), "_").trim('_')

        return snake.ifBlank { "unknown_gun" }
    }

    private fun parseRoot(json: String, report: GunPackCompatibilityReport): JsonObject? {
        val sanitizedJson = stripJsonComments(json)
        val element = runCatching { JsonParser().parse(sanitizedJson) }
            .getOrElse {
                report.addError(
                    code = IssueCode.MALFORMED_JSON,
                    field = "$",
                    message = "Malformed json: ${it.message ?: "unknown error"}"
                )
                return null
            }

        if (!element.isJsonObject) {
            report.addError(
                code = IssueCode.MALFORMED_JSON,
                field = "$",
                message = "Root element must be a JSON object."
            )
            return null
        }

        return element.asJsonObject
    }

    private fun stripJsonComments(json: String): String {
        val out = StringBuilder(json.length)
        var i = 0
        var inString = false
        var escaped = false
        var inLineComment = false
        var inBlockComment = false

        while (i < json.length) {
            val ch = json[i]
            val next = if (i + 1 < json.length) json[i + 1] else '\u0000'

            if (inLineComment) {
                if (ch == '\n' || ch == '\r') {
                    inLineComment = false
                    out.append(ch)
                }
                i += 1
                continue
            }

            if (inBlockComment) {
                if (ch == '*' && next == '/') {
                    inBlockComment = false
                    i += 2
                } else {
                    i += 1
                }
                continue
            }

            if (inString) {
                out.append(ch)
                if (escaped) {
                    escaped = false
                } else if (ch == '\\') {
                    escaped = true
                } else if (ch == '"') {
                    inString = false
                }
                i += 1
                continue
            }

            if (ch == '"') {
                inString = true
                out.append(ch)
                i += 1
                continue
            }

            if (ch == '/' && next == '/') {
                inLineComment = true
                i += 2
                continue
            }

            if (ch == '/' && next == '*') {
                inBlockComment = true
                i += 2
                continue
            }

            out.append(ch)
            i += 1
        }

        return out.toString()
    }

    private fun normalize(data: GunData, report: GunPackCompatibilityReport): GunData {
        val normalizedAmmoAmount = if (data.ammoAmount < 1) {
            report.addError(
                code = IssueCode.INVALID_FIELD_VALUE,
                field = "ammo_amount",
                message = "ammo_amount must be >= 1, fallback to ${GunDefaults.AMMO_AMOUNT}."
            )
            GunDefaults.AMMO_AMOUNT
        } else {
            data.ammoAmount
        }

        val normalizedRpm = if (data.roundsPerMinute < 1) {
            report.addError(
                code = IssueCode.INVALID_FIELD_VALUE,
                field = "rpm",
                message = "rpm must be >= 1, fallback to ${GunDefaults.RPM}."
            )
            GunDefaults.RPM
        } else {
            data.roundsPerMinute
        }

        val normalizedAimTime = if (data.aimTimeSeconds < 0f) {
            report.addWarning(
                code = IssueCode.INVALID_FIELD_VALUE,
                field = "aim_time",
                message = "aim_time must be >= 0, fallback to ${GunDefaults.AIM_TIME_SECONDS}."
            )
            GunDefaults.AIM_TIME_SECONDS
        } else {
            data.aimTimeSeconds
        }

        val normalizedCrawlRecoilMultiplier = if (data.crawlRecoilMultiplier < 0f) {
            report.addWarning(
                code = IssueCode.INVALID_FIELD_VALUE,
                field = "crawl_recoil_multiplier",
                message = "crawl_recoil_multiplier must be >= 0, fallback to ${GunDefaults.CRAWL_RECOIL_MULTIPLIER}."
            )
            GunDefaults.CRAWL_RECOIL_MULTIPLIER
        } else {
            data.crawlRecoilMultiplier
        }

        val normalizedBulletAmount = if (data.bullet.bulletAmount < 1) {
            report.addWarning(
                code = IssueCode.INVALID_FIELD_VALUE,
                field = "bullet.bullet_amount",
                message = "bullet_amount must be >= 1, fallback to ${GunDefaults.BULLET_AMOUNT}."
            )
            GunDefaults.BULLET_AMOUNT
        } else {
            data.bullet.bulletAmount
        }

        val normalizedDamage = if (data.bullet.damage < 0f) {
            report.addWarning(
                code = IssueCode.INVALID_FIELD_VALUE,
                field = "bullet.damage",
                message = "bullet.damage must be >= 0, fallback to ${GunDefaults.BULLET_DAMAGE}."
            )
            GunDefaults.BULLET_DAMAGE
        } else {
            data.bullet.damage
        }

        val normalizedInaccuracyStand = normalizeInaccuracyField(
            value = data.inaccuracy.stand,
            field = "inaccuracy.stand",
            report = report
        )
        val normalizedInaccuracyMove = normalizeInaccuracyField(
            value = data.inaccuracy.move,
            field = "inaccuracy.move",
            report = report
        )
        val normalizedInaccuracySneak = normalizeInaccuracyField(
            value = data.inaccuracy.sneak,
            field = "inaccuracy.sneak",
            report = report
        )
        val normalizedInaccuracyLie = normalizeInaccuracyField(
            value = data.inaccuracy.lie,
            field = "inaccuracy.lie",
            report = report
        )
        val normalizedInaccuracyAim = normalizeInaccuracyField(
            value = data.inaccuracy.aim,
            field = "inaccuracy.aim",
            report = report
        )

        return data.copy(
            ammoAmount = normalizedAmmoAmount,
            roundsPerMinute = normalizedRpm,
            aimTimeSeconds = normalizedAimTime,
            crawlRecoilMultiplier = normalizedCrawlRecoilMultiplier,
            inaccuracy = data.inaccuracy.copy(
                stand = normalizedInaccuracyStand,
                move = normalizedInaccuracyMove,
                sneak = normalizedInaccuracySneak,
                lie = normalizedInaccuracyLie,
                aim = normalizedInaccuracyAim
            ),
            bullet = data.bullet.copy(
                bulletAmount = normalizedBulletAmount,
                damage = normalizedDamage,
                friction = data.bullet.friction.coerceAtLeast(0.0f)
            )
        )
    }

    private fun readRecoil(root: JsonObject, report: GunPackCompatibilityReport): GunRecoilData {
        val recoilField = root.findField("recoil", emptyList(), "recoil", report)
        if (recoilField == null) {
            return GunRecoilData()
        }

        val recoilElement = recoilField.second
        if (!recoilElement.isJsonObject) {
            report.addWarning(
                code = IssueCode.INVALID_FIELD_TYPE,
                field = "recoil",
                message = "Field 'recoil' should be an object, defaults are used."
            )
            return GunRecoilData()
        }

        val recoilRoot = recoilElement.asJsonObject
        return GunRecoilData(
            pitch = readRecoilTrack(
                root = recoilRoot,
                track = "pitch",
                report = report
            ),
            yaw = readRecoilTrack(
                root = recoilRoot,
                track = "yaw",
                report = report
            )
        )
    }

    private fun readRecoilTrack(
        root: JsonObject,
        track: String,
        report: GunPackCompatibilityReport
    ): List<GunRecoilKeyFrameData> {
        val fieldName = "recoil.$track"
        val element = root.get(track) ?: return emptyList()
        if (!element.isJsonArray) {
            report.addWarning(
                code = IssueCode.INVALID_FIELD_TYPE,
                field = fieldName,
                message = "Field '$fieldName' should be an array, ignored."
            )
            return emptyList()
        }

        val out = mutableListOf<GunRecoilKeyFrameData>()
        element.asJsonArray.forEachIndexed { index, frameElement ->
            if (!frameElement.isJsonObject) {
                report.addWarning(
                    code = IssueCode.INVALID_FIELD_TYPE,
                    field = "$fieldName[$index]",
                    message = "Each recoil key frame should be an object, entry ignored."
                )
                return@forEachIndexed
            }

            val frame = frameElement.asJsonObject
            val timeElement = frame.get("time")
            if (timeElement == null || !timeElement.isJsonPrimitive || !timeElement.asJsonPrimitive.isNumber) {
                report.addWarning(
                    code = IssueCode.INVALID_FIELD_TYPE,
                    field = "$fieldName[$index].time",
                    message = "Field '$fieldName[$index].time' should be a number, entry ignored."
                )
                return@forEachIndexed
            }

            val valueElement = frame.get("value")
            if (valueElement == null || !valueElement.isJsonArray) {
                report.addWarning(
                    code = IssueCode.INVALID_FIELD_TYPE,
                    field = "$fieldName[$index].value",
                    message = "Field '$fieldName[$index].value' should be a number array, entry ignored."
                )
                return@forEachIndexed
            }

            val valueArray = valueElement.asJsonArray
            if (valueArray.size() < 2) {
                report.addWarning(
                    code = IssueCode.INVALID_FIELD_VALUE,
                    field = "$fieldName[$index].value",
                    message = "Field '$fieldName[$index].value' should contain at least 2 numbers, entry ignored."
                )
                return@forEachIndexed
            }

            val minElement = valueArray[0]
            val maxElement = valueArray[1]
            if (!minElement.isJsonPrimitive || !minElement.asJsonPrimitive.isNumber ||
                !maxElement.isJsonPrimitive || !maxElement.asJsonPrimitive.isNumber
            ) {
                report.addWarning(
                    code = IssueCode.INVALID_FIELD_TYPE,
                    field = "$fieldName[$index].value",
                    message = "Field '$fieldName[$index].value' should be a number pair, entry ignored."
                )
                return@forEachIndexed
            }

            val normalizedTime = timeElement.asFloat.coerceAtLeast(0f)
            if (normalizedTime != timeElement.asFloat) {
                report.addWarning(
                    code = IssueCode.INVALID_FIELD_VALUE,
                    field = "$fieldName[$index].time",
                    message = "Recoil key frame time should be >= 0, clamped to 0."
                )
            }

            val rawMin = minElement.asFloat
            val rawMax = maxElement.asFloat
            val valueMin = kotlin.math.min(rawMin, rawMax)
            val valueMax = kotlin.math.max(rawMin, rawMax)

            out += GunRecoilKeyFrameData(
                timeSeconds = normalizedTime,
                valueMin = valueMin,
                valueMax = valueMax
            )
        }

        return out.sortedBy { it.timeSeconds }
    }

    private fun normalizeInaccuracyField(
        value: Float,
        field: String,
        report: GunPackCompatibilityReport
    ): Float {
        if (value >= 0f) {
            return value
        }

        report.addWarning(
            code = IssueCode.INVALID_FIELD_VALUE,
            field = field,
            message = "$field must be >= 0, fallback to 0.0."
        )
        return 0.0f
    }

    private fun readInaccuracy(root: JsonObject, report: GunPackCompatibilityReport): GunInaccuracyData {
        val inaccuracyField = root.findField("inaccuracy", emptyList(), "inaccuracy", report)
        if (inaccuracyField == null) {
            return GunInaccuracyData()
        }

        val inaccuracyObj = inaccuracyField.second
        if (!inaccuracyObj.isJsonObject) {
            report.addWarning(
                code = IssueCode.INVALID_FIELD_TYPE,
                field = "inaccuracy",
                message = "Field 'inaccuracy' should be an object, defaults are used."
            )
            return GunInaccuracyData()
        }

        val inaccuracyRoot = inaccuracyObj.asJsonObject
        return GunInaccuracyData(
            stand = readFloat(
                root = inaccuracyRoot,
                primary = "stand",
                aliases = emptyList(),
                field = "inaccuracy.stand",
                defaultValue = GunDefaults.INACCURACY_STAND,
                report = report
            ),
            move = readFloat(
                root = inaccuracyRoot,
                primary = "move",
                aliases = emptyList(),
                field = "inaccuracy.move",
                defaultValue = GunDefaults.INACCURACY_MOVE,
                report = report
            ),
            sneak = readFloat(
                root = inaccuracyRoot,
                primary = "sneak",
                aliases = emptyList(),
                field = "inaccuracy.sneak",
                defaultValue = GunDefaults.INACCURACY_SNEAK,
                report = report
            ),
            lie = readFloat(
                root = inaccuracyRoot,
                primary = "lie",
                aliases = emptyList(),
                field = "inaccuracy.lie",
                defaultValue = GunDefaults.INACCURACY_LIE,
                report = report
            ),
            aim = readFloat(
                root = inaccuracyRoot,
                primary = "aim",
                aliases = emptyList(),
                field = "inaccuracy.aim",
                defaultValue = GunDefaults.INACCURACY_AIM,
                report = report
            )
        )
    }

    private fun readBullet(root: JsonObject, report: GunPackCompatibilityReport): GunBulletData {
        val bulletField = root.findField("bullet", emptyList(), "bullet", report)
        if (bulletField == null) {
            report.addInfo(
                code = IssueCode.MISSING_OPTIONAL_FIELD,
                field = "bullet",
                message = "Field 'bullet' is missing, defaults are used."
            )
            return GunBulletData(
                lifeSeconds = GunDefaults.BULLET_LIFE_SECONDS,
                bulletAmount = GunDefaults.BULLET_AMOUNT,
                damage = GunDefaults.BULLET_DAMAGE,
                speed = GunDefaults.BULLET_SPEED,
                gravity = GunDefaults.BULLET_GRAVITY,
                friction = GunDefaults.BULLET_FRICTION,
                pierce = GunDefaults.BULLET_PIERCE
            )
        }

        val bulletObj = bulletField.second
        if (!bulletObj.isJsonObject) {
            report.addWarning(
                code = IssueCode.INVALID_FIELD_TYPE,
                field = "bullet",
                message = "Field 'bullet' should be an object, defaults are used."
            )
            return GunBulletData(
                lifeSeconds = GunDefaults.BULLET_LIFE_SECONDS,
                bulletAmount = GunDefaults.BULLET_AMOUNT,
                damage = GunDefaults.BULLET_DAMAGE,
                speed = GunDefaults.BULLET_SPEED,
                gravity = GunDefaults.BULLET_GRAVITY,
                friction = GunDefaults.BULLET_FRICTION,
                pierce = GunDefaults.BULLET_PIERCE
            )
        }

        val bulletObjRoot = bulletObj.asJsonObject

        return GunBulletData(
            lifeSeconds = readFloat(
                root = bulletObjRoot,
                primary = "life",
                aliases = emptyList(),
                field = "bullet.life",
                defaultValue = GunDefaults.BULLET_LIFE_SECONDS,
                report = report
            ),
            bulletAmount = readInt(
                root = bulletObjRoot,
                primary = "bullet_amount",
                aliases = emptyList(),
                field = "bullet.bullet_amount",
                defaultValue = GunDefaults.BULLET_AMOUNT,
                report = report
            ),
            damage = readFloat(
                root = bulletObjRoot,
                primary = "damage",
                aliases = emptyList(),
                field = "bullet.damage",
                defaultValue = GunDefaults.BULLET_DAMAGE,
                report = report
            ),
            speed = readFloat(
                root = bulletObjRoot,
                primary = "speed",
                aliases = emptyList(),
                field = "bullet.speed",
                defaultValue = GunDefaults.BULLET_SPEED,
                report = report
            ),
            gravity = readFloat(
                root = bulletObjRoot,
                primary = "gravity",
                aliases = emptyList(),
                field = "bullet.gravity",
                defaultValue = GunDefaults.BULLET_GRAVITY,
                report = report
            ),
            friction = readFloat(
                root = bulletObjRoot,
                primary = "friction",
                aliases = emptyList(),
                field = "bullet.friction",
                defaultValue = GunDefaults.BULLET_FRICTION,
                report = report
            ),
            pierce = readInt(
                root = bulletObjRoot,
                primary = "pierce",
                aliases = emptyList(),
                field = "bullet.pierce",
                defaultValue = GunDefaults.BULLET_PIERCE,
                report = report
            )
        )
    }

    private fun readReload(root: JsonObject, report: GunPackCompatibilityReport): GunReloadData {
        val reloadField = root.findField("reload", emptyList(), "reload", report)
        if (reloadField == null) {
            report.addInfo(
                code = IssueCode.MISSING_OPTIONAL_FIELD,
                field = "reload",
                message = "Field 'reload' is missing, defaults are used."
            )
            return GunReloadData(
                type = GunFeedType.MAGAZINE,
                infinite = false,
                emptyTimeSeconds = GunDefaults.RELOAD_EMPTY_TIME,
                tacticalTimeSeconds = GunDefaults.RELOAD_TACTICAL_TIME
            )
        }

        val reloadObj = reloadField.second
        if (!reloadObj.isJsonObject) {
            report.addWarning(
                code = IssueCode.INVALID_FIELD_TYPE,
                field = "reload",
                message = "Field 'reload' should be an object, defaults are used."
            )
            return GunReloadData(
                type = GunFeedType.MAGAZINE,
                infinite = false,
                emptyTimeSeconds = GunDefaults.RELOAD_EMPTY_TIME,
                tacticalTimeSeconds = GunDefaults.RELOAD_TACTICAL_TIME
            )
        }

        val reloadObjRoot = reloadObj.asJsonObject

        val feedObj = reloadObjRoot.findField("feed", emptyList(), "reload.feed", report)?.let { (_, value) ->
            if (value.isJsonObject) {
                value.asJsonObject
            } else {
                report.addWarning(
                    code = IssueCode.INVALID_FIELD_TYPE,
                    field = "reload.feed",
                    message = "Field 'reload.feed' should be an object, ignored."
                )
                null
            }
        }
        val cooldownObj = reloadObjRoot.findField("cooldown", emptyList(), "reload.cooldown", report)?.let { (_, value) ->
            if (value.isJsonObject) {
                value.asJsonObject
            } else {
                report.addWarning(
                    code = IssueCode.INVALID_FIELD_TYPE,
                    field = "reload.cooldown",
                    message = "Field 'reload.cooldown' should be an object, ignored."
                )
                null
            }
        }

        val timingObj = feedObj ?: cooldownObj
        val timingField = if (feedObj != null) "reload.feed" else "reload.cooldown"
        if (feedObj == null && cooldownObj != null) {
            report.addInfo(
                code = IssueCode.FIELD_ALIAS_USED,
                field = "reload.feed",
                message = "Field 'reload.feed' is missing, fallback to 'reload.cooldown'."
            )
        }

        return GunReloadData(
            type = readEnum(
                root = reloadObjRoot,
                primary = "type",
                aliases = listOf("feed_type"),
                field = "reload.type",
                defaultValue = GunFeedType.MAGAZINE,
                report = report,
                converter = GunFeedType::fromSerialized
            ),
            infinite = readBoolean(
                root = reloadObjRoot,
                primary = "infinite",
                aliases = emptyList(),
                field = "reload.infinite",
                defaultValue = false,
                report = report
            ),
            emptyTimeSeconds = readFloat(
                root = timingObj,
                primary = "empty",
                aliases = emptyList(),
                field = "$timingField.empty",
                defaultValue = GunDefaults.RELOAD_EMPTY_TIME,
                report = report
            ),
            tacticalTimeSeconds = readFloat(
                root = timingObj,
                primary = "tactical",
                aliases = emptyList(),
                field = "$timingField.tactical",
                defaultValue = GunDefaults.RELOAD_TACTICAL_TIME,
                report = report
            )
        )
    }

    private fun readFireModes(root: JsonObject, report: GunPackCompatibilityReport): Set<GunFireMode> {
        val found = root.findField(
            primary = "fire_mode",
            aliases = listOf("fire_modes"),
            field = "fire_mode",
            report = report
        ) ?: return setOf(GunFireMode.SEMI).also {
            report.addWarning(
                code = IssueCode.MISSING_OPTIONAL_FIELD,
                field = "fire_mode",
                message = "Field 'fire_mode' is missing, fallback to [semi]."
            )
        }

        val value = found.second
        if (value.isJsonPrimitive && value.asJsonPrimitive.isString) {
            return parseFireModesFromTokens(
                rawValue = value.asString,
                report = report
            )
        }

        if (!value.isJsonArray) {
            report.addWarning(
                code = IssueCode.INVALID_FIELD_TYPE,
                field = "fire_mode",
                message = "Field 'fire_mode' should be an array, fallback to [semi]."
            )
            return setOf(GunFireMode.SEMI)
        }

        val rawModes = mutableListOf<String>()
        value.asJsonArray.forEach { element ->
            val raw = element.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }?.asString
            if (raw == null) {
                report.addWarning(
                    code = IssueCode.INVALID_FIELD_TYPE,
                    field = "fire_mode",
                    message = "Encountered non-string fire mode entry, ignored."
                )
                return@forEach
            }
            rawModes += raw
        }

        return parseFireModesFromTokens(
            rawValues = rawModes,
            report = report
        )
    }

    private fun parseFireModesFromTokens(
        rawValue: String,
        report: GunPackCompatibilityReport
    ): Set<GunFireMode> {
        val tokens = rawValue
            .split(',', '|', '/')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        return parseFireModesFromTokens(tokens, report)
    }

    private fun parseFireModesFromTokens(
        rawValues: Iterable<String>,
        report: GunPackCompatibilityReport
    ): Set<GunFireMode> {
        val modes = linkedSetOf<GunFireMode>()
        rawValues.forEach { raw ->
            val mode = GunFireMode.fromSerialized(raw)
            if (mode == null) {
                report.addWarning(
                    code = IssueCode.UNSUPPORTED_ENUM_VALUE,
                    field = "fire_mode",
                    message = "Unsupported fire mode '$raw', ignored."
                )
                return@forEach
            }
            modes += mode
        }

        if (modes.isEmpty()) {
            report.addWarning(
                code = IssueCode.INVALID_FIELD_VALUE,
                field = "fire_mode",
                message = "No valid fire modes found, fallback to [semi]."
            )
            return setOf(GunFireMode.SEMI)
        }

        return modes
    }

    private fun readString(
        root: JsonObject?,
        primary: String,
        aliases: List<String>,
        field: String,
        report: GunPackCompatibilityReport
    ): String? {
        if (root == null) {
            return null
        }
        val found = root.findField(primary, aliases, field, report) ?: return null
        val value = found.second
        if (!value.isJsonPrimitive || !value.asJsonPrimitive.isString) {
            report.addWarning(
                code = IssueCode.INVALID_FIELD_TYPE,
                field = field,
                message = "Field '$field' should be a string."
            )
            return null
        }
        return value.asString
    }

    private fun readInt(
        root: JsonObject?,
        primary: String,
        aliases: List<String>,
        field: String,
        defaultValue: Int,
        report: GunPackCompatibilityReport
    ): Int {
        if (root == null) {
            return defaultValue
        }
        val found = root.findField(primary, aliases, field, report) ?: return defaultValue
        val value = found.second
        if (!value.isJsonPrimitive || !value.asJsonPrimitive.isNumber) {
            report.addWarning(
                code = IssueCode.INVALID_FIELD_TYPE,
                field = field,
                message = "Field '$field' should be a number, fallback to $defaultValue."
            )
            return defaultValue
        }
        return value.asInt
    }

    private fun readFloat(
        root: JsonObject?,
        primary: String,
        aliases: List<String>,
        field: String,
        defaultValue: Float,
        report: GunPackCompatibilityReport
    ): Float {
        if (root == null) {
            return defaultValue
        }
        val found = root.findField(primary, aliases, field, report) ?: return defaultValue
        val value = found.second
        if (!value.isJsonPrimitive || !value.asJsonPrimitive.isNumber) {
            report.addWarning(
                code = IssueCode.INVALID_FIELD_TYPE,
                field = field,
                message = "Field '$field' should be a number, fallback to $defaultValue."
            )
            return defaultValue
        }
        return value.asFloat
    }

    private fun readBoolean(
        root: JsonObject?,
        primary: String,
        aliases: List<String>,
        field: String,
        defaultValue: Boolean,
        report: GunPackCompatibilityReport
    ): Boolean {
        if (root == null) {
            return defaultValue
        }
        val found = root.findField(primary, aliases, field, report) ?: return defaultValue
        val value = found.second
        if (!value.isJsonPrimitive || !value.asJsonPrimitive.isBoolean) {
            report.addWarning(
                code = IssueCode.INVALID_FIELD_TYPE,
                field = field,
                message = "Field '$field' should be a boolean, fallback to $defaultValue."
            )
            return defaultValue
        }
        return value.asBoolean
    }

    private fun <T> readEnum(
        root: JsonObject,
        primary: String,
        aliases: List<String>,
        field: String,
        defaultValue: T,
        report: GunPackCompatibilityReport,
        converter: (String) -> T?
    ): T {
        val value = readString(
            root = root,
            primary = primary,
            aliases = aliases,
            field = field,
            report = report
        ) ?: return defaultValue

        return converter(value) ?: defaultValue.also {
            report.addWarning(
                code = IssueCode.UNSUPPORTED_ENUM_VALUE,
                field = field,
                message = "Unsupported value '$value' for field '$field', fallback to '$defaultValue'."
            )
        }
    }

    private fun readIntList(
        root: JsonObject,
        primary: String,
        aliases: List<String>,
        field: String,
        report: GunPackCompatibilityReport
    ): List<Int>? {
        val found = root.findField(primary, aliases, field, report) ?: return null
        val value = found.second
        if (!value.isJsonArray) {
            report.addWarning(
                code = IssueCode.INVALID_FIELD_TYPE,
                field = field,
                message = "Field '$field' should be an array of numbers."
            )
            return null
        }

        val result = mutableListOf<Int>()
        value.asJsonArray.forEach { entry ->
            if (!entry.isJsonPrimitive || !entry.asJsonPrimitive.isNumber) {
                report.addWarning(
                    code = IssueCode.INVALID_FIELD_TYPE,
                    field = field,
                    message = "Non-number entry in '$field' ignored."
                )
                return@forEach
            }
            result += entry.asInt
        }
        return result
    }

    private fun JsonObject.findField(
        primary: String,
        aliases: List<String>,
        field: String,
        report: GunPackCompatibilityReport
    ): Pair<String, JsonElement>? {
        if (has(primary)) {
            return primary to get(primary)
        }

        aliases.forEach { alias ->
            if (has(alias)) {
                report.addInfo(
                    code = IssueCode.FIELD_ALIAS_USED,
                    field = field,
                    message = "Field '$field' loaded via alias '$alias'."
                )
                return alias to get(alias)
            }
        }
        return null
    }

    private object IssueCode {
        const val MALFORMED_JSON: String = "MALFORMED_JSON"
        const val MISSING_REQUIRED_FIELD: String = "MISSING_REQUIRED_FIELD"
        const val MISSING_OPTIONAL_FIELD: String = "MISSING_OPTIONAL_FIELD"
        const val INVALID_FIELD_TYPE: String = "INVALID_FIELD_TYPE"
        const val INVALID_FIELD_VALUE: String = "INVALID_FIELD_VALUE"
        const val UNSUPPORTED_ENUM_VALUE: String = "UNSUPPORTED_ENUM_VALUE"
        const val FIELD_ALIAS_USED: String = "FIELD_ALIAS_USED"
    }

}