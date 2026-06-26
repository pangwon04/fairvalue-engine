// ===========================================================================
// InputHash 교차검증 (W7.5) — Python reproducer.py 가 동결한 expected_hash 와
// Kotlin InputHash 출력이 일치하는지 JUnit5 로 확인한다.
// 동일한 shared/schemas/hash-test-vectors.json 을 읽어 input → hash 계산 후 비교.
// ===========================================================================
package com.fairvalue.contracts

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import java.io.File
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory

class InputHashTest {

    private val mapper = ObjectMapper()

    /** 실행 위치와 무관하게 저장소 루트의 벡터 파일을 찾는다. */
    private fun vectorsFile(): File {
        val rel = "shared/schemas/hash-test-vectors.json"
        var dir: File? = File(System.getProperty("user.dir"))
        while (dir != null) {
            val f = File(dir, rel)
            if (f.exists()) return f
            dir = dir.parentFile
        }
        error("hash-test-vectors.json 을 찾을 수 없습니다(저장소 루트 기준 $rel).")
    }

    private fun vectors(): JsonNode =
        mapper.readTree(vectorsFile()).get("vectors")

    @TestFactory
    fun `각 테스트벡터 hash 가 expected_hash 와 일치한다`(): List<DynamicTest> {
        val vs = vectors()
        return vs.map { case ->
            val name = case.get("name").asText()
            DynamicTest.dynamicTest(name) {
                val input = case.get("input") as ObjectNode
                val expected = case.get("expected_hash").asText()
                val got = InputHash.ofJson(input.toString())
                assertEquals(expected, got, "$name: Kotlin hash 가 Python expected_hash 와 다릅니다")
            }
        }
    }

    @Test
    fun `벡터는 5개이며 canonical_blob 도 일치한다`() {
        val vs = vectors()
        assertEquals(5, vs.size(), "테스트벡터는 5개여야 합니다")
        for (case in vs) {
            val input = case.get("input") as ObjectNode
            val expectedBlob = case.get("canonical_blob")?.asText()
            if (expectedBlob != null && expectedBlob != "TBD") {
                assertEquals(
                    expectedBlob,
                    InputHash.canonicalBlob(input),
                    "${case.get("name").asText()}: canonical_blob 불일치",
                )
            }
        }
        assertTrue(true)
    }
}
