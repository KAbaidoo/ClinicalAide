package co.kobby.clinicalaide.data.pdf.models

data class ParsedMedication(
    val name: String,
    val genericName: String? = null,
    val dosage: String,
    val frequency: String? = null,
    val duration: String? = null,
    val route: String? = null,
    val indication: String? = null,
    val contraindications: List<String> = emptyList(),
    val sideEffects: List<String> = emptyList(),
    val pregnancyCategory: String? = null,
    val pediatricDose: String? = null,
    val renalAdjustment: String? = null,
    val hepaticAdjustment: String? = null
)