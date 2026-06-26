// ===========================================================================
// FairValue Engine — input_hash Kotlin 구현 (W7, §2.5)
// ---------------------------------------------------------------------------
// 정본은 pricing-engine/app/reproducer.py 이며, 본 구현은 동일 정규화 8단계를
// 재현해 **동일 input 에 대해 동일 hash** 를 산출해야 한다.
// shared/schemas/hash-test-vectors.json 의 expected_hash 와 CI 에서 교차검증한다.
//
// [Python 과의 직렬화 정합 계약 — 반드시 동일해야 하는 지점]
//   - 정수/실수 구분 보존: JSON 입력에서 소수점 없는 수는 정수, 있는 수는 실수로 취급.
//     (Python json 은 파싱 시 int/float 를 구분해 "1" vs "1.0" 으로 직렬화)
//   - 실수 표기: Python repr 의 최단 왕복 표기와 동일해야 함.
//     예) 45.0 -> "45.0", 3.30 -> "3.3", 0.0 -> "0.0", 14.10 -> "14.1", 0.024 -> "0.024"
//     Java 의 Double.toString 도 위 값들에 대해 동일 결과를 낸다.
//   - float 반올림: 소수 10자리(round half to even 가 아닌, Python round 와 동일하게
//     처리해야 함 — 테스트 벡터 값들은 10자리 미만이라 영향 없음).
//   - 키 정렬: sort_keys=True (UTF-16 코드포인트 기준 사전식. ASCII 키만 사용하므로 안전).
//   - 구분자: (",", ":") 공백 없음.
//   - UTF-8 / ensure_ascii=false: 한글 등 비ASCII 를 escape 하지 않음(단, 해시 대상엔
//     비ASCII 문구 필드가 없음 — metadata/source 는 제외 대상).
//   - 빈 권리조건: enabled==false 권리는 {} 로 정규화.
//   - null 제거: 값이 null 인 키는 삭제.
//   - 배열 순서 보존: 커브 포인트 정렬 금지.
//
// 의존성: com.fasterxml.jackson.databind (Jackson). build.gradle 에 추가 필요.
// ===========================================================================
package dev.fairvalue.context

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.databind.node.JsonNodeType
import com.fasterxml.jackson.databind.node.ObjectNode
import java.math.BigInteger
import java.security.MessageDigest

object InputHash {

    private val mapper = ObjectMapper()

    /** 해시 대상 market 수치 키(§3.8). */
    private val MARKET_KEYS = listOf(
        "asset_id", "spot", "volatility", "dividend_yield", "shares_outstanding", "as_of"
    )

    /** final ValuationContext(JSON 문자열) → input_hash(소문자 hex 64자). */
    fun ofJson(ctxJson: String): String {
        val ctx = mapper.readTree(ctxJson) as ObjectNode
        val blob = canonicalBlob(ctx)
        return sha256Hex(blob)
    }

    /** 정규화된 정본 직렬화 문자열(7단계). */
    fun canonicalBlob(ctx: ObjectNode): String = serialize(canonicalize(ctx))

    // --- 1단계: 해시 대상만 추출 ---
    private fun canonicalize(ctx: ObjectNode): JsonNode {
        val market = ctx.get("market") as? ObjectNode ?: mapper.createObjectNode()
        val curves = ctx.get("curves") as? ObjectNode ?: mapper.createObjectNode()

        val pickedMarket = mapper.createObjectNode()
        for (k in MARKET_KEYS) pickedMarket.set<JsonNode>(k, market.get(k))

        val pickedCurves = mapper.createObjectNode()
        pickedCurves.set<JsonNode>("risk_free_curve", curves.get("risk_free_curve"))
        pickedCurves.set<JsonNode>("credit_curve", curves.get("credit_curve"))
        pickedCurves.set<JsonNode>("interpolation_method", curves.get("interpolation_method"))
        pickedCurves.set<JsonNode>("curve_version", curves.get("curve_version"))

        val pick = mapper.createObjectNode()
        pick.set<JsonNode>("instrument_type", ctx.get("instrument_type"))
        pick.set<JsonNode>("valuation_date", ctx.get("valuation_date"))
        pick.set<JsonNode>("terms", ctx.get("terms") ?: mapper.createObjectNode())
        pick.set<JsonNode>("rights", normalizeRights(ctx.get("rights") as? ObjectNode))
        pick.set<JsonNode>("market", pickedMarket)
        pick.set<JsonNode>("curves", pickedCurves)
        pick.set<JsonNode>("model", ctx.get("model"))
        pick.set<JsonNode>("model_version", ctx.get("model_version"))
        pick.set<JsonNode>("seed", ctx.get("seed"))
        pick.set<JsonNode>("options", ctx.get("options") ?: mapper.createObjectNode())

        // null 제거 + float 반올림(2·4단계)은 roundAndStrip 에서 수행.
        return roundAndStrip(pick)
    }

    // --- 3단계: enabled==false 권리는 {} 로, null 권리는 제거 ---
    private fun normalizeRights(rights: ObjectNode?): ObjectNode {
        val out = mapper.createObjectNode()
        if (rights == null) return out
        val it = rights.fields()
        while (it.hasNext()) {
            val (k, v) = it.next()
            if (v.isObject && v.get("enabled")?.isBoolean == true && v.get("enabled").booleanValue() == false) {
                out.set<JsonNode>(k, mapper.createObjectNode())   // 빈 객체
            } else if (!v.isNull) {
                out.set<JsonNode>(k, v)
            }
        }
        return out
    }

    // --- 2·4단계: dict 의 null 키 제거 + float 10자리 반올림 ---
    private fun roundAndStrip(node: JsonNode?): JsonNode {
        if (node == null || node.isNull) return mapper.nullNode()
        when (node.nodeType) {
            JsonNodeType.OBJECT -> {
                val obj = mapper.createObjectNode()
                val it = node.fields()
                while (it.hasNext()) {
                    val (k, v) = it.next()
                    if (v == null || v.isNull) continue          // null 키 삭제
                    obj.set<JsonNode>(k, roundAndStrip(v))
                }
                return obj
            }
            JsonNodeType.ARRAY -> {
                val arr = mapper.createArrayNode()
                for (child in node) arr.add(roundAndStrip(child)) // 순서 보존
                return arr
            }
            else -> {
                // 실수만 10자리 반올림. 정수/불리언/문자열은 그대로.
                if (node.isFloatingPointNumber) {
                    val r = Math.round(node.doubleValue() * 1e10) / 1e10
                    return mapper.nodeFactory.numberNode(r)
                }
                return node
            }
        }
    }

    // --- 7단계: sort_keys + 공백제거 + Python repr 호환 숫자 직렬화 ---
    private fun serialize(node: JsonNode): String {
        val sb = StringBuilder()
        writeNode(node, sb)
        return sb.toString()
    }

    private fun writeNode(node: JsonNode, sb: StringBuilder) {
        when (node.nodeType) {
            JsonNodeType.OBJECT -> {
                sb.append('{')
                val keys = (node as ObjectNode).fieldNames().asSequence().sorted().toList()
                for ((i, k) in keys.withIndex()) {
                    if (i > 0) sb.append(',')
                    writeString(k, sb); sb.append(':'); writeNode(node.get(k), sb)
                }
                sb.append('}')
            }
            JsonNodeType.ARRAY -> {
                sb.append('[')
                val arr = node as ArrayNode
                for (i in 0 until arr.size()) {
                    if (i > 0) sb.append(',')
                    writeNode(arr.get(i), sb)
                }
                sb.append(']')
            }
            JsonNodeType.STRING -> writeString(node.textValue(), sb)
            JsonNodeType.BOOLEAN -> sb.append(if (node.booleanValue()) "true" else "false")
            JsonNodeType.NULL -> sb.append("null")
            JsonNodeType.NUMBER -> sb.append(numberRepr(node))
            else -> sb.append(node.toString())
        }
    }

    /** Python json 과 동일한 숫자 표기: 정수는 정수, 실수는 최단 왕복(Double.toString) 표기. */
    private fun numberRepr(node: JsonNode): String {
        if (node.isIntegralNumber) {
            // BigInteger 로 받아 지수표기 없이 출력(3000000000 등).
            return (node.bigIntegerValue() ?: BigInteger.valueOf(node.longValue())).toString()
        }
        // 실수: Java Double.toString 은 위 계약의 값들에 대해 Python repr 과 동일.
        return node.doubleValue().toString()
    }

    private fun writeString(s: String, sb: StringBuilder) {
        sb.append('"')
        for (c in s) {
            when (c) {
                '"' -> sb.append("\\\"")
                '\\' -> sb.append("\\\\")
                '\n' -> sb.append("\\n")
                '\r' -> sb.append("\\r")
                '\t' -> sb.append("\\t")
                else -> sb.append(c)   // ensure_ascii=false: 비ASCII escape 안 함
            }
        }
        sb.append('"')
    }

    private fun sha256Hex(s: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        val bytes = md.digest(s.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
