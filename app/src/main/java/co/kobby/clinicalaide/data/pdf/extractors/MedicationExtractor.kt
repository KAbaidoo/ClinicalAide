package co.kobby.clinicalaide.data.pdf.extractors

import co.kobby.clinicalaide.data.pdf.models.ParsedCondition
import co.kobby.clinicalaide.data.pdf.models.ParsedMedication

class MedicationExtractor {
    
    companion object {
        private val MEDICATION_PATTERN = Regex(
            """(?i)(?:^|\n)\s*[-•]\s*([A-Za-z]+(?:\s+[A-Za-z]+)*)\s*[:]*\s*(\d+(?:\.\d+)?)\s*(mg|g|ml|mcg|iu|units?)""",
            RegexOption.MULTILINE
        )
        
        private val DOSAGE_PATTERN = Regex(
            """(?i)(\d+(?:\.\d+)?)\s*(mg|g|ml|mcg|iu|units?|tab(?:let)?s?|cap(?:sule)?s?)(?:\s+(?:po|im|iv|sc|pr|sl|topical|inhaled))?(?:\s+(?:od|bd|tid|tds|qid|qds|prn|stat|daily|twice|three times|four times))?"""
        )
        
        private val FREQUENCY_PATTERN = Regex(
            """(?i)\b(od|bd|tid|tds|qid|qds|prn|stat|daily|twice daily|three times|four times|every \d+ hours?|q\d+h)\b"""
        )
        
        private val ROUTE_PATTERN = Regex(
            """(?i)\b(po|per os|oral(?:ly)?|im|intramuscular|iv|intravenous|sc|subcutaneous|pr|per rectum|sl|sublingual|topical|inhaled|nasal)\b"""
        )
        
        private val DURATION_PATTERN = Regex(
            """(?i)for\s+(\d+)\s+(days?|weeks?|months?)"""
        )
        
        private val PEDIATRIC_PATTERN = Regex(
            """(?i)(?:pediatric|children?|child|infant)\s*(?:dose|dosage)?[:\s]+(.+?)(?:\.|;|$)"""
        )
        
        private val PREGNANCY_CATEGORY = Regex(
            """(?i)pregnancy\s+category\s+([A-DX])"""
        )
    }
    
    fun extractMedications(condition: ParsedCondition): List<ParsedMedication> {
        val medications = mutableListOf<ParsedMedication>()
        val treatmentBlocks = condition.contentBlocks.filter { 
            it.contentType.name.contains("TREATMENT") || 
            it.contentType.name.contains("PHARMACOLOGICAL")
        }
        
        for (block in treatmentBlocks) {
            medications.addAll(extractMedicationsFromText(block.content))
        }
        
        return medications.distinctBy { it.name.lowercase() }
    }
    
    fun extractMedicationsFromText(text: String): List<ParsedMedication> {
        val medications = mutableListOf<ParsedMedication>()
        val lines = text.lines()
        
        for (line in lines) {
            if (line.trim().isEmpty()) continue
            
            val medicationInfo = parseMedicationLine(line)
            if (medicationInfo != null) {
                medications.add(medicationInfo)
            }
        }
        
        return medications
    }
    
    private fun parseMedicationLine(line: String): ParsedMedication? {
        val medicationMatch = MEDICATION_PATTERN.find(line)
        if (medicationMatch != null) {
            var name = medicationMatch.groupValues[1].trim()
            
            // Handle compound medications like Artemether-Lumefantrine
            if (line.contains("-") && name.split("-").size == 1) {
                val compoundMatch = Regex("""(?i)([\w-]+):\s*(\d+(?:\.\d+)?)\s*(mg|g|ml|mcg|iu|units?)""").find(line)
                if (compoundMatch != null) {
                    name = compoundMatch.groupValues[1].trim()
                }
            }
            
            val dosageValue = medicationMatch.groupValues[2]
            val dosageUnit = medicationMatch.groupValues[3]
            val dosage = "$dosageValue$dosageUnit"
            
            val frequency = FREQUENCY_PATTERN.find(line)?.value
            val route = ROUTE_PATTERN.find(line)?.value
            val duration = DURATION_PATTERN.find(line)?.let {
                "${it.groupValues[1]} ${it.groupValues[2]}"
            }
            
            return ParsedMedication(
                name = name,
                dosage = dosage,
                frequency = frequency,
                route = route,
                duration = duration
            )
        }
        
        val commonMedications = listOf(
            "paracetamol", "amoxicillin", "metronidazole", "ciprofloxacin",
            "doxycycline", "artemether", "lumefantrine", "metformin",
            "amlodipine", "atenolol", "omeprazole", "diclofenac"
        )
        
        val lowerLine = line.lowercase()
        for (med in commonMedications) {
            if (lowerLine.contains(med)) {
                val dosageMatch = DOSAGE_PATTERN.find(line)
                val dosage = dosageMatch?.value ?: "See text"
                val frequency = FREQUENCY_PATTERN.find(line)?.value
                val route = ROUTE_PATTERN.find(line)?.value
                val duration = DURATION_PATTERN.find(line)?.let {
                    "${it.groupValues[1]} ${it.groupValues[2]}"
                }
                
                return ParsedMedication(
                    name = med.replaceFirstChar { it.uppercase() },
                    dosage = dosage,
                    frequency = frequency,
                    route = route,
                    duration = duration
                )
            }
        }
        
        return null
    }
    
    fun extractPediatricDosing(text: String): String? {
        // First try the specific pattern
        val match = PEDIATRIC_PATTERN.find(text)
        if (match != null) {
            return match.groupValues[1].trim()
        }
        
        // Also check for simple pediatric dose mentions
        val lines = text.lines()
        for (line in lines) {
            if (line.lowercase().contains("pediatric") || line.lowercase().contains("children")) {
                // Extract dosage information from this line
                val dosageMatch = DOSAGE_PATTERN.find(line)
                if (dosageMatch != null) {
                    // Return the portion of the line after "pediatric" or "children"
                    val index = maxOf(
                        line.lowercase().indexOf("pediatric dose:"),
                        line.lowercase().indexOf("children:")
                    )
                    if (index >= 0) {
                        return line.substring(index).substringAfter(":").trim()
                    }
                }
            }
        }
        
        return null
    }
    
    fun extractPregnancyCategory(text: String): String? {
        val match = PREGNANCY_CATEGORY.find(text)
        return match?.groupValues?.get(1)
    }
    
    fun extractContraindications(text: String): List<String> {
        val contraindications = mutableListOf<String>()
        val contraindicationPattern = Regex(
            """(?i)contraindication[s]?[:\s]+(.+?)(?:\.|;|$)""",
            RegexOption.MULTILINE
        )
        
        contraindicationPattern.findAll(text).forEach { match ->
            val items = match.groupValues[1].split(Regex(""",|;"""))
            contraindications.addAll(items.map { it.trim() })
        }
        
        return contraindications
    }
    
    fun extractSideEffects(text: String): List<String> {
        val sideEffects = mutableListOf<String>()
        val sideEffectPattern = Regex(
            """(?i)(?:side effect[s]?|adverse effect[s]?)[:\s]+(.+?)(?:\.|;|$)""",
            RegexOption.MULTILINE
        )
        
        sideEffectPattern.findAll(text).forEach { match ->
            val items = match.groupValues[1].split(Regex(""",|;"""))
            sideEffects.addAll(items.map { it.trim() })
        }
        
        return sideEffects
    }
}