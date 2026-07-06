package mirujam.nekomemo.navigation

import mirujam.nekomemo.R

sealed class Route(val route: String, val titleResId: Int) {
    data object Library : Route("library", R.string.nav_library)
    data object Settings : Route("settings", R.string.nav_settings)
    data object Fetcher : Route("fetcher", R.string.nav_fetcher)
    data object JsonImport : Route("json_import", R.string.nav_json_import)
    data object Extract : Route("extract", R.string.nav_extract)
    data object Detail : Route("detail?bankId={bankId}", R.string.nav_detail) {
        fun createRoute(bankId: Long): String = "detail?bankId=$bankId"
    }
    data object Test : Route("test?bankId={bankId}&questionCount={questionCount}&shuffleQuestions={shuffleQuestions}&shuffleOptions={shuffleOptions}", R.string.nav_test) {
        fun createRoute(bankId: Long, questionCount: Int, shuffleQuestions: Boolean = false, shuffleOptions: Boolean = false): String = "test?bankId=$bankId&questionCount=$questionCount&shuffleQuestions=$shuffleQuestions&shuffleOptions=$shuffleOptions"
    }
}
