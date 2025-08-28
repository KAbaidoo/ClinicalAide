package co.kobby.clinicalaide.services

import co.kobby.clinicalaide.data.rag.RagRepository
import kotlinx.coroutines.delay
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random

/**
 * Mock LLM service for testing the chat interface.
 * Provides canned responses based on query keywords until real LLM integration.
 */
@Singleton
class MockLLMService @Inject constructor() {
    
    /**
     * Generate a mock response based on the query and RAG context.
     */
    suspend fun generateResponse(
        query: String,
        ragContext: RagRepository.RagContext
    ): LLMResponse {
        // Simulate processing time
        val processingTime = Random.nextLong(500, 1500)
        delay(processingTime)
        
        val queryLower = query.lowercase()
        
        // Generate response based on keywords
        val response = when {
            queryLower.contains("malaria") && queryLower.contains("child") -> {
                """
                The treatment for severe malaria in children involves:
                
                PRIMARY TREATMENT:
                • Artesunate IV/IM: 2.4 mg/kg at 0, 12, and 24 hours, then daily
                • Complete treatment course: 7 days total
                • Switch to oral artemisinin-based combination therapy (ACT) when able to tolerate
                
                SUPPORTIVE CARE:
                • Monitor blood glucose regularly (risk of hypoglycemia)
                • Maintain fluid balance carefully
                • Treat fever with paracetamol 15 mg/kg every 6 hours
                
                IMPORTANT NOTES:
                • Admit all children with severe malaria
                • Monitor for complications: cerebral malaria, severe anemia, respiratory distress
                • Do NOT use quinine as first-line in severe malaria
                
                REFERRAL CRITERIA:
                • Impaired consciousness or coma
                • Severe anemia (Hb <5 g/dL)
                • Respiratory distress
                • Multiple convulsions
                
                Evidence Level: Grade A recommendation
                """.trimIndent()
            }
            
            queryLower.contains("malaria") && queryLower.contains("adult") -> {
                """
                The treatment for severe malaria in adults involves:
                
                PRIMARY TREATMENT:
                • Artesunate IV/IM: 2.4 mg/kg at 0, 12, and 24 hours, then daily
                • Total duration: 7 days
                • Switch to oral ACT when patient can tolerate
                
                ALTERNATIVE (if artesunate unavailable):
                • Artemether IM: 3.2 mg/kg loading dose, then 1.6 mg/kg daily
                • OR Quinine IV: 20 mg/kg loading dose, then 10 mg/kg every 8 hours
                
                MONITORING:
                • Blood glucose every 4-6 hours
                • Renal function daily
                • Parasitemia daily until cleared
                
                Evidence Level: Grade A recommendation
                """.trimIndent()
            }
            
            queryLower.contains("diarrhea") || queryLower.contains("diarrhoea") -> {
                """
                Management of acute diarrhea:
                
                ASSESSMENT:
                • Determine dehydration status (none, some, severe)
                • Check for blood in stool
                • Assess duration and frequency
                
                TREATMENT:
                • Oral Rehydration Solution (ORS):
                  - No dehydration: 50-100 ml after each stool
                  - Some dehydration: 75 ml/kg over 4 hours
                  - Severe dehydration: Refer for IV fluids
                
                • Continue feeding/breastfeeding
                • Zinc supplementation: 20 mg daily for 10-14 days (10 mg if <6 months)
                
                ANTIBIOTICS:
                • NOT routinely recommended
                • Consider only for bloody diarrhea or suspected cholera
                
                Evidence Level: Grade A for ORS, Grade B for zinc
                """.trimIndent()
            }
            
            queryLower.contains("hypertension") -> {
                """
                Hypertension management guidelines:
                
                DIAGNOSIS:
                • BP ≥140/90 mmHg on 2+ occasions
                • Home BP monitoring recommended
                
                FIRST-LINE TREATMENT:
                • ACE inhibitor (e.g., Lisinopril 5-10 mg daily)
                • OR Calcium channel blocker (e.g., Amlodipine 5 mg daily)
                • Thiazide diuretic for elderly or black patients
                
                TARGET BP:
                • <140/90 mmHg for most patients
                • <130/80 mmHg if diabetic or CKD
                
                LIFESTYLE MODIFICATIONS:
                • Salt restriction (<5g/day)
                • Weight loss if overweight
                • Regular exercise
                • Limit alcohol intake
                
                Evidence Level: Grade A recommendations
                """.trimIndent()
            }
            
            queryLower.contains("pneumonia") -> {
                """
                Community-acquired pneumonia treatment:
                
                OUTPATIENT TREATMENT:
                • Amoxicillin 1g TDS for 5-7 days (first-line)
                • OR Azithromycin 500mg daily for 3 days
                • Add macrolide if atypical pneumonia suspected
                
                SEVERE PNEUMONIA (hospitalize):
                • Co-amoxiclav IV 1.2g TDS
                • PLUS Clarithromycin 500mg BD
                • Switch to oral when improving
                
                DANGER SIGNS requiring admission:
                • Respiratory rate >30/min
                • Confusion
                • BP <90/60 mmHg
                • SpO2 <92% on air
                
                Evidence Level: Grade A for empirical antibiotics
                """.trimIndent()
            }
            
            else -> {
                """
                Based on your query about "$query", here are the relevant clinical guidelines:
                
                Please note that treatment recommendations should be tailored to individual patient circumstances. Key considerations include:
                
                • Patient age and weight
                • Comorbidities and contraindications
                • Severity of condition
                • Local resistance patterns for infections
                • Available resources and medications
                
                For specific dosing and detailed protocols, please consult the complete Ghana STG guidelines or seek specialist advice when needed.
                
                Always consider:
                • Patient safety first
                • Evidence-based practice
                • Local guidelines and protocols
                • Referral when beyond scope of practice
                """.trimIndent()
            }
        }
        
        // Extract mock citations from context
        val citations = if (ragContext.citations.isNotEmpty()) {
            ragContext.citations.joinToString("\n") { "• Ghana STG 7th Edition, $it" }
        } else {
            "• Ghana STG 7th Edition, Chapter reference pending"
        }
        
        return LLMResponse(
            text = response,
            citations = citations,
            processingTimeMs = processingTime,
            similarityScore = ragContext.averageSimilarity,
            contentIds = ragContext.contents.map { it.contentId }
        )
    }
    
    /**
     * Build a contextual prompt for follow-up queries.
     */
    fun buildContextualPrompt(
        currentQuery: String,
        previousQuery: String,
        previousResponse: String
    ): String {
        return """
        Previous conversation:
        User: $previousQuery
        Assistant: ${previousResponse.take(200)}...
        
        Current question: $currentQuery
        
        Please provide a response that considers the previous context.
        """.trimIndent()
    }
    
    /**
     * Response from the LLM service.
     */
    data class LLMResponse(
        val text: String,
        val citations: String,
        val processingTimeMs: Long,
        val similarityScore: Float? = null,
        val contentIds: List<Int> = emptyList()
    )
}