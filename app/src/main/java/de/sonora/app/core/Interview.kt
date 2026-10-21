package de.sonora.app.core

data class InterviewOption(val id: String, val label: String, val curve: List<Float>)
data class InterviewQuestion(val id: String, val prompt: String, val options: List<InterviewOption>)
data class InterviewAnswer(val questionId: String, val optionId: String)

object InterviewCatalog {
    private fun option(id: String, label: String, vararg curve: Float) =
        InterviewOption(id, label, curve.toList())

    val questions: List<InterviewQuestion> = listOf(
        InterviewQuestion("balance", "Klingt Musik für dich eher zu dumpf oder zu schrill?", listOf(
            option("dumpf", "Zu dumpf", 4f, 3f, 2f, 0f, 0f, 0f, 1f, 2f, 2f, 1f),
            option("ausgewogen", "Ausgewogen", *List(10) { 0f }.toFloatArray()),
            option("schrill", "Zu schrill", -2f, -2f, -1f, 0f, 0f, 0f, -1f, -2f, -3f, -3f)
        )),
        InterviewQuestion("bass", "Magst du kräftigen Bass oder lieber einen schlanken Klang?", listOf(
            option("bass", "Kräftiger Bass", 6f, 5f, 4f, 2f, 0f, -1f, -1f, 0f, 1f, 1f),
            option("ausgewogen", "Ausgewogen", *List(10) { 0f }.toFloatArray()),
            option("bassarm", "Schlank", -4f, -3f, -2f, 0f, 1f, 2f, 2f, 1f, 0f, 0f)
        )),
        InterviewQuestion("sprache", "Soll Sprache im Vordergrund stehen?", listOf(
            option("ja", "Ja, Sprache zuerst", -3f, -2f, -1f, 1f, 3f, 4f, 3f, 1f, 0f, -1f),
            option("nein", "Eher Musik", 2f, 2f, 1f, 0f, 0f, 0f, 0f, 0f, 0f, 0f),
            option("egal", "Ist mir egal", *List(10) { 0f }.toFloatArray())
        )),
        InterviewQuestion("hoehen", "Wie stark dürfen die hohen Töne sein?", listOf(
            option("sanft", "Zurückhaltend", 0f, 0f, 0f, 0f, 0f, -1f, -2f, -3f, -3f, -2f),
            option("normal", "Normal", *List(10) { 0f }.toFloatArray()),
            option("klar", "Präsent", -1f, -1f, 0f, 0f, 1f, 2f, 3f, 4f, 4f, 3f)
        )),
        InterviewQuestion("klangfarbe", "Hörst du lieber warm oder hell?", listOf(
            option("warm", "Warm", 3f, 3f, 2f, 1f, 0f, 0f, -1f, -2f, -2f, -2f),
            option("ausgewogen", "Ausgewogen", *List(10) { 0f }.toFloatArray()),
            option("hell", "Hell", -2f, -1f, 0f, 0f, 0f, 1f, 2f, 3f, 3f, 2f)
        )),
        InterviewQuestion("verkehr", "Stört dich kräftiger Bass in Zug und Bus?", listOf(
            option("stoert", "Ja, das stört mich", -4f, -4f, -3f, 0f, 1f, 1f, 0f, 0f, 0f, 0f),
            option("egal", "Egal", *List(10) { 0f }.toFloatArray()),
            option("nicht", "Nein, es stört mich nicht", 3f, 3f, 2f, 0f, 0f, 0f, 0f, 0f, 0f, 0f)
        )),
        InterviewQuestion("hoeren", "Welche Lautstärke fühlt sich nach langem Hören angenehm an?", listOf(
            option("leise", "Leise", 0f, 0f, 0f, 1f, 2f, 2f, 1f, 0f, -1f, -1f),
            option("mittel", "Mittel", *List(10) { 0f }.toFloatArray()),
            option("laut", "Laut", -1f, -1f, 0f, 0f, 0f, 0f, 0f, 1f, 1f, 1f)
        )),
        InterviewQuestion("inhalt", "Hörst du vor allem Musik oder Sprache?", listOf(
            option("musik", "Musik", 2f, 2f, 1f, 0f, 0f, 0f, 0f, 0f, 0f, 0f),
            option("beides", "Beides", *List(10) { 0f }.toFloatArray()),
            option("sprache", "Sprache", -3f, -2f, -1f, 1f, 3f, 4f, 3f, 1f, 0f, -1f)
        ))
    )
}

object InterviewEvaluator {
    fun evaluate(answers: List<InterviewAnswer>): EqCurve = evaluateWithBandCount(answers, BandCount.TEN)

    fun evaluateWithBandCount(answers: List<InterviewAnswer>, bandCount: BandCount): EqCurve {
        val selected = answers.mapNotNull { answer ->
            InterviewCatalog.questions.firstOrNull { it.id == answer.questionId }
                ?.options?.firstOrNull { it.id == answer.optionId }
        }
        if (selected.isEmpty()) return EqCurve(bandCount, List(bandCount.size) { 0f })
        val levels = List(10) { band ->
            selected.map { it.curve[band] }.average().toFloat().coerceIn(EqModel.MIN_DB, EqModel.MAX_DB)
        }
        return EqCurve(BandCount.TEN, levels).withBandCount(bandCount)
    }

    fun summary(curve: EqCurve): String {
        val ten = curve.withBandCount(BandCount.TEN).levelsDb
        val bass = ten.take(3).average()
        val voice = ten.slice(3..6).average()
        return when {
            bass > 1.5 && voice > 1.5 -> "Dein Profil bringt Bass und Sprache nach vorn."
            bass > 1.5 -> "Dein Profil gibt dem Bass mehr Kraft."
            voice > 1.5 -> "Dein Profil stellt Sprache klarer nach vorn."
            bass < -1.5 -> "Dein Profil klingt schlanker und zurückhaltender im Bass."
            else -> "Dein Profil bleibt ausgewogen und nah am Original."
        }
    }
}
