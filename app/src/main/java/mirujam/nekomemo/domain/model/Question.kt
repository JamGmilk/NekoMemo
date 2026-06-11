package mirujam.nekomemo.domain.model

data class Question(
    val id: Long = 0,
    val questionBankId: Long,
    val text: String,
    val options: List<String>,
    val correctIndices: List<Int>,
    val type: String
)
