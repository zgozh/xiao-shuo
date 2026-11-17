package io.legado.app.ui.main.hub

import com.google.gson.JsonParser
import org.htmlunit.corejs.javascript.Context
import org.htmlunit.corejs.javascript.ScriptableObject
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

class JapaneseSourceRuleTest {
    class Files {
        fun cacheFile(url: String, seconds: Int): String = "ignored\nignored\nchapter A\nchapter B\nnext chapter"
    }

    private fun content(part: Int, locked: Boolean = false): String {
        val source = JsonParser.parseString(File("src/main/assets/defaultData/goubinJapaneseTextSources.json").readText())
            .asJsonArray[0].asJsonObject
        val script = source["ruleContent"].asJsonObject["content"].asString.removePrefix("@js:")
        val context = Context.enter()
        try {
            val scope = context.initStandardObjects()
            ScriptableObject.putProperty(scope, "java", Context.javaToJS(Files(), scope))
            ScriptableObject.putProperty(scope, "baseUrl", "https://example/volume/1?part=$part")
            ScriptableObject.putProperty(scope, "result", """{"code":200,"results":{"is_lock":$locked,"volume":{"txt_addr":"https://example/text.txt","contents":[{"content_type":1,"start_lines":2,"end_lines":4},{"content_type":2,"content":"https://example/image.jpg"}]}}}""")
            return Context.toString(context.evaluateString(scope, script, "content", 1, null))
        } finally {
            Context.exit()
        }
    }

    @Test fun extractsOnlyTheRequestedChapterLines() {
        assertEquals("chapter A\nchapter B", content(0))
    }

    @Test fun keepsIllustrationsAsReaderImages() {
        assertEquals("<img src=\"https://example/image.jpg\">", content(1))
    }

    @Test(expected = RuntimeException::class) fun refusesLockedVolumes() {
        content(0, locked = true)
    }
}
