package com.pawly.service;

import com.pawly.entity.KnowledgeChunk;
import com.pawly.repository.KnowledgeChunkRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class RagService {

    private final KnowledgeChunkRepository chunkRepo;

    @Transactional(readOnly = true)
    public List<KnowledgeChunk> searchRelevantChunks(String symptomText, String species) {
        Set<Long> seen = new LinkedHashSet<>();
        List<KnowledgeChunk> results = new ArrayList<>();
        try {
            String booleanQuery = buildBooleanQuery(symptomText);
            List<KnowledgeChunk> ftResults = chunkRepo.searchByContent(booleanQuery, 10);
            for (KnowledgeChunk c : ftResults) { if (seen.add(c.getId())) results.add(c); }
        } catch (Exception e) {
            log.warn("Full-text search failed, falling back to keyword search: {}", e.getMessage());
        }
        if (species != null && !species.isBlank()) {
            List<KnowledgeChunk> sr = chunkRepo.findRelevantChunks(species.toLowerCase());
            for (KnowledgeChunk c : sr) { if (seen.add(c.getId())) results.add(c); }
        }
        if (results.size() < 5) {
            Arrays.stream(symptomText.split("\\s+"))
                  .filter(w -> w.length() > 4).limit(5)
                  .forEach(keyword -> chunkRepo.findByKeyword(keyword.toLowerCase())
                           .forEach(c -> { if (seen.add(c.getId())) results.add(c); }));
        }
        return results.stream().limit(8).collect(Collectors.toList());
    }

    private String buildBooleanQuery(String text) {
        return Arrays.stream(text.toLowerCase().split("[^a-z0-9]+"))
                     .filter(w -> w.length() > 3)
                     .map(w -> "+"  + w + "*")
                     .collect(Collectors.joining(" "));
    }

    public String buildTriagePrompt(String symptomText, String species, String petName, List<KnowledgeChunk> chunks) {
        StringBuilder sb = new StringBuilder();
        sb.append("You are an expert veterinary triage assistant. ")
          .append("Use ONLY the knowledge base context below to answer. ")
          .append("If the answer is not in the context, say 'Please consult a veterinarian immediately.'\n\n");
        sb.append("=== VETERINARY KNOWLEDGE BASE ===\n");
        for (int i = 0; i < chunks.size(); i++) {
            KnowledgeChunk c = chunks.get(i);
            sb.append("\n[Chunk ").append(i + 1).append("] Topic: ").append(c.getTopic());
            if (c.getUrgencyLevel() != null) sb.append(" | Urgency: ").append(c.getUrgencyLevel());
            if (c.getSpecies() != null)      sb.append(" | Species: ").append(c.getSpecies());
            sb.append("\n").append(c.getContentText()).append("\n");
        }
        sb.append("\n=== TRIAGE REQUEST ===\n");
        if (petName != null && !petName.isBlank()) sb.append("Pet name: ").append(petName).append("\n");
        if (species  != null && !species.isBlank()) sb.append("Species: ").append(species).append("\n");
        sb.append("Reported symptoms: ").append(symptomText).append("\n\n");
        sb.append("Provide a structured triage assessment:\n")
          .append("1. Urgency level (CRITICAL / URGENT / MODERATE / MILD)\n")
          .append("2. Likely cause based on symptoms\n")
          .append("3. Immediate actions the owner should take\n")
          .append("4. Whether emergency vet visit is needed and how soon\n")
          .append("5. Warning signs to watch for\n");
        return sb.toString();
    }

    @Transactional
    public List<KnowledgeChunk> ingestChunks(List<KnowledgeChunk> chunks) {
        log.info("Ingesting {} knowledge chunks...", chunks.size());
        return chunkRepo.saveAll(chunks);
    }

    @Transactional
    public void seedTriageKnowledgeBase() {
        if (chunkRepo.count() > 0) {
            log.info("Knowledge base already populated ({} chunks) - skipping seed.", chunkRepo.count());
            return;
        }
        log.info("Seeding triage knowledge base...");
        chunkRepo.saveAll(buildSeedChunks());
        log.info("Knowledge base seeded successfully.");
    }

    private List<KnowledgeChunk> buildSeedChunks() {
        LocalDateTime rd = LocalDateTime.of(2024, 10, 1, 0, 0);
        List<KnowledgeChunk> c = new ArrayList<>();

        c.add(chunk("Urgency Classification", null, "CRITICAL",
            "critical,emergency,immediate,life-threatening,vet now",
            "CRITICAL (Emergency - go to vet immediately):\n" +
            "- Difficulty breathing, gasping, or choking\n" +
            "- Collapse or inability to stand\n" +
            "- Seizures or uncontrolled convulsions\n" +
            "- Suspected poisoning or toxin ingestion\n" +
            "- Severe trauma (hit by car, fall from height, animal attack)\n" +
            "- Uncontrolled or severe bleeding\n" +
            "- Pale, white, blue, or grey gums\n" +
            "- Swollen abdomen with distress (possible bloat in dogs)\n" +
            "- Urinary blockage (especially in male cats - fatal within hours)", rd));

        c.add(chunk("Urgency Classification", null, "URGENT",
            "urgent,hours,same-day,vet today",
            "URGENT (See a vet within a few hours):\n" +
            "- Vomiting more than 3 times in 24 hours\n" +
            "- Diarrhea with blood or lasting more than 24 hours\n" +
            "- Not eating for more than 24 hours (cats) or 48 hours (dogs)\n" +
            "- Limping or inability to bear weight on a limb\n" +
            "- Sudden behavioral changes (aggression, withdrawal, confusion)\n" +
            "- Straining to urinate with small amounts produced", rd));

        c.add(chunk("Urgency Classification", null, "MODERATE",
            "moderate,monitor,24-48 hours,watch",
            "MODERATE (Monitor and schedule vet visit within 24-48 hours):\n" +
            "- Mild vomiting (1-2 times) without other symptoms\n" +
            "- Mild diarrhea (soft stool, no blood) for less than 24 hours\n" +
            "- Slight decrease in appetite\n" +
            "- Minor cuts or scrapes that are not deep\n" +
            "- Mild limping with full weight bearing", rd));

        c.add(chunk("Emergency Red Flags", null, "CRITICAL",
            "red flags,emergency,danger signs,gums,breathing,collapse",
            "GUM COLOR CHANGES:\n" +
            "- Pale/white gums = shock, anemia, internal bleeding\n" +
            "- Blue/purple gums (cyanosis) = lack of oxygen\n" +
            "- Yellow gums (jaundice) = liver disease\n\n" +
            "BREATHING EMERGENCIES:\n" +
            "- Open-mouth breathing in cats (always emergency)\n" +
            "- Labored breathing, belly breathing\n\n" +
            "NEUROLOGICAL SIGNS:\n" +
            "- Seizures lasting more than 2 minutes\n" +
            "- Sudden paralysis or dragging hind limbs\n\n" +
            "GASTROINTESTINAL EMERGENCIES:\n" +
            "- Bloated hard abdomen with retching but no vomiting (GDV in dogs)\n" +
            "- Bloody diarrhea with weakness", rd));

        c.add(chunk("Dog Emergency Signs", "dog", "CRITICAL",
            "dog,canine,bloat,GDV,parvovirus,heat stroke",
            "BLOAT/GDV (Gastric Dilatation-Volvulus):\n" +
            "- Signs: distended abdomen, unproductive retching, restlessness\n" +
            "- FATAL within hours - emergency surgery required immediately\n\n" +
            "PARVOVIRUS:\n" +
            "- Signs: bloody diarrhea, severe vomiting, lethargy, fever\n" +
            "- Highly contagious - isolate the dog and seek vet care immediately\n\n" +
            "HEAT STROKE:\n" +
            "- Signs: excessive panting, drooling, collapse, high body temp (>104F/40C)\n" +
            "- Cool dog with wet towels, seek vet care immediately", rd));

        c.add(chunk("Cat Emergency Signs", "cat", "CRITICAL",
            "cat,feline,urinary blockage,FLUTD,open-mouth breathing",
            "URINARY BLOCKAGE (male cats especially):\n" +
            "- Signs: straining to urinate, crying in litter box, distended bladder\n" +
            "- FATAL within 24-48 hours if untreated - go to vet immediately\n\n" +
            "OPEN-MOUTH BREATHING:\n" +
            "- Always an emergency in cats (they never pant like dogs)\n" +
            "- May indicate heart failure, chest fluid, or respiratory disease\n\n" +
            "FELINE HEPATIC LIPIDOSIS:\n" +
            "- Cats not eating for 2-3 days develop fatty liver disease\n" +
            "- Signs: jaundice, vomiting, drooling, lethargy", rd));

        c.add(chunk("First Aid - Bleeding Control", null, "CRITICAL",
            "first aid,bleeding,wound,pressure,bandage",
            "1. Apply firm direct pressure with a clean cloth or gauze pad\n" +
            "2. Do NOT remove the cloth if it soaks through - add more on top\n" +
            "3. Keep pressure for at least 5-10 minutes\n" +
            "4. Seek emergency vet care for spurting blood or wounds that won't stop bleeding\n" +
            "5. Do NOT probe or clean deep wounds - cover and go to vet", rd));

        c.add(chunk("First Aid - Seizures", null, "CRITICAL",
            "first aid,seizure,convulsion,epilepsy",
            "1. Do NOT put anything in the pet's mouth\n" +
            "2. Clear the area of furniture and sharp objects\n" +
            "3. Time the seizure - call vet if it lasts more than 2 minutes\n" +
            "4. Do NOT restrain the pet\n" +
            "5. CALL VET IMMEDIATELY if: seizure > 2 min, multiple in 24h, first-ever seizure", rd));

        c.add(chunk("First Aid - Suspected Poisoning", null, "CRITICAL",
            "first aid,poisoning,toxic,ingestion,xylitol,chocolate,grapes,antifreeze",
            "Common toxins: xylitol (dogs), grapes/raisins (dogs), chocolate, lilies (cats - FATAL),\n" +
            "ibuprofen/acetaminophen, antifreeze, permethrin flea products (cats - FATAL).\n\n" +
            "What to do:\n" +
            "1. Do NOT induce vomiting unless instructed by a vet\n" +
            "2. Identify the toxin and save the packaging\n" +
            "3. Note time of ingestion and quantity\n" +
            "4. Call vet or animal poison control immediately\n" +
            "5. Go to emergency vet - do not wait for symptoms", rd));

        c.add(chunk("Medication Safety", null, "CRITICAL",
            "medication,drugs,safe,unsafe,toxic,human medicine,OTC",
            "NEVER give dogs: Ibuprofen, Naproxen, Acetaminophen, Pseudoephedrine.\n" +
            "NEVER give cats (even more sensitive):\n" +
            "- Acetaminophen - FATAL even in small doses\n" +
            "- Aspirin - very narrow therapeutic window\n" +
            "- Ibuprofen/NSAIDs - kidney failure\n" +
            "- Permethrin (dog flea products) - FATAL neurological toxicity in cats\n" +
            "If accidental ingestion: call vet immediately with product name and dosage.", rd));

        c.add(chunk("Intake Questions for Triage", null, null,
            "intake,questions,history,assessment,triage,symptoms",
            "PATIENT: species, breed, age, sex, weight, vaccination history.\n" +
            "SYMPTOM ASSESSMENT:\n" +
            "- When did symptoms start? Getting better or worse?\n" +
            "- Vomiting/diarrhea? Frequency, color, blood?\n" +
            "- Eating and drinking normally?\n" +
            "- Potential exposure to toxins or foreign objects?\n" +
            "- Any trauma?\n\n" +
            "VITAL SIGNS: gum color (pink=normal, pale/blue=emergency),\n" +
            "breathing rate (15-30/min), temperature (100-102.5F normal).", rd));

        c.add(chunk("AI Triage Limitations and Disclaimer", null, null,
            "disclaimer,limitations,AI,not a vet,consult",
            "This AI triage assistant CAN: assess urgency, provide first aid guidance.\n" +
            "This AI CANNOT: replace physical exam, diagnose conditions, prescribe medications.\n" +
            "ALWAYS consult a licensed veterinarian for serious symptoms and treatment decisions.\n" +
            "When in doubt: err on the side of caution and seek veterinary care.", rd));

        return c;
    }

    private KnowledgeChunk chunk(String topic, String species, String urgency,
                                  String keywords, String content, LocalDateTime reviewDate) {
        KnowledgeChunk c = new KnowledgeChunk();
        c.setTopic(topic);
        c.setSpecies(species);
        c.setUrgencyLevel(urgency);
        c.setKeywords(keywords);
        c.setContentText(content.trim());
        c.setSource("Pet Emergency Triage Knowledge Base");
        c.setReviewedDate(reviewDate);
        return c;
    }
}