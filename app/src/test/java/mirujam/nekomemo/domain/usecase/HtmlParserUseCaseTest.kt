package mirujam.nekomemo.domain.usecase

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HtmlParserUseCaseTest {

    @Test
    fun parse_keepsLongQuestionContent() {
        val longContent = "这是一段很长的题干".repeat(12)
        val html = questionHtml(
            content = longContent,
            answer = "A"
        )

        val bank = HtmlParserUseCase.parse(html)

        assertEquals(1, bank.questions.size)
        assertEquals(longContent, bank.questions.first().content)
        assertTrue(bank.questions.first().content.length > 100)
    }

    @Test
    fun parse_normalizesVerboseCorrectAnswerText() {
        val html = questionHtml(
            content = "题目内容",
            answer = "正确答案：B"
        )

        val bank = HtmlParserUseCase.parse(html)

        assertEquals(1, bank.questions.size)
        assertEquals("B", bank.questions.first().correctAnswer)
        assertEquals(1, bank.questions.first().correctIndex)
    }

    private fun questionHtml(content: String, answer: String): String {
        return """
            <html>
              <body>
                <h2 class="mark_title">测试题库</h2>
                <div class="questionLi">
                  <h3 class="mark_name">
                    <span class="colorShallow">单选题</span>
                    1. $content
                  </h3>
                  <ul class="mark_letter">
                    <li>A. 选项一</li>
                    <li>B. 选项二</li>
                  </ul>
                  <span class="rightAnswerContent">$answer</span>
                </div>
              </body>
            </html>
        """.trimIndent()
    }
}
