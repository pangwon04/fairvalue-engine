package com.fairvalue.validation

import com.fasterxml.jackson.databind.JsonNode

/**
 * 점(.) 표기 경로로 JsonNode 를 탐색한다(rawForm bind 경로용).
 *   예: "metadata.issuer", "rights.conversion.strike", "rights.vesting.tranche[0].quantity"
 *
 * ★ Jackson 의 멤버 JsonNode.at(String) 은 JSON Pointer(RFC 6901, "/a/b")이므로
 *    점 표기를 그대로 넘기면 IllegalArgumentException 이 난다. 이 함수는 '.' 로 분해해
 *    path() 로 한 단계씩 내려가며, [idx] 배열 인덱스도 처리한다. 경로가 없으면 null.
 */
fun nodeAtPath(node: JsonNode, path: String): JsonNode? {
    var cur: JsonNode = node
    for (rawSeg in path.split('.')) {
        var name = rawSeg
        var index: Int? = null
        val br = rawSeg.indexOf('[')
        if (br >= 0 && rawSeg.endsWith("]")) {
            name = rawSeg.substring(0, br)
            index = rawSeg.substring(br + 1, rawSeg.length - 1).toIntOrNull()
        }
        if (name.isNotEmpty()) {
            cur = cur.path(name)
            if (cur.isMissingNode) return null
        }
        if (index != null) {
            cur = cur.path(index)
            if (cur.isMissingNode) return null
        }
    }
    return if (cur.isNull) null else cur
}
