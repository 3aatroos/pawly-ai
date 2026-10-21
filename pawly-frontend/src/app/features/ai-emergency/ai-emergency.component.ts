import { Component, OnInit, OnDestroy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { TriageService } from '../../core/services/triage.service';
import { PetService } from '../../core/services/pet.service';
import { Pet } from '../../core/models/pet.model';
import { TriageResponse, TriageStep } from '../../core/models/triage.model';

interface ParsedResult {
  urgency: string;
  assessment: string;
  action: string[];
  raw: string;
}

@Component({
  selector: 'app-ai-emergency',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink],
  template: `
  <div class="fade-up">
    <div class="page-header">
      <div>
        <div class="page-title">AI Emergency</div>
        <div class="page-sub">Describe your pet symptoms - AI will assess urgency and guide you</div>
      </div>
    </div>

    <div class="emergency-hero">
      <div class="hero-icon">{{ icons.siren }}</div>
      <div class="hero-text">
        <div class="hero-title">AI-Powered Pet Triage</div>
        <div class="hero-sub">Powered by Mistral AI + Veterinary Knowledge Base</div>
      </div>
    </div>

    <div class="step-tracker">
      <div class="step-item" [class.active]="stepIndex >= 0" [class.done]="stepIndex > 0">
        <div class="step-num">1</div>
        <div class="step-icon">{{ icons.paw }}</div>
        <div class="step-title">Select Pet</div>
        <div class="step-desc">Choose the pet in distress</div>
      </div>
      <div class="step-item" [class.active]="stepIndex >= 1" [class.done]="stepIndex > 1">
        <div class="step-num">2</div>
        <div class="step-icon">{{ icons.clipboard }}</div>
        <div class="step-title">Describe Symptoms</div>
        <div class="step-desc">Text or voice input analyzed by AI</div>
      </div>
      <div class="step-item" [class.active]="stepIndex >= 2" [class.done]="stepIndex > 2">
        <div class="step-num">3</div>
        <div class="step-icon">{{ icons.brain }}</div>
        <div class="step-title">AI Analysis</div>
        <div class="step-desc">Severity classification in seconds</div>
      </div>
      <div class="step-item" [class.active]="stepIndex >= 3" [class.done]="stepIndex > 3">
        <div class="step-num">4</div>
        <div class="step-icon">{{ icons.check }}</div>
        <div class="step-title">Result</div>
        <div class="step-desc">Assessment and guidance</div>
      </div>
    </div>

    <!-- STEP 1: Pet Selection -->
    @if (step === 'pet-select') {
      <div class="card" style="margin-top:20px">
        <div class="card-header"><span class="card-title">Which pet needs help?</span></div>
        <p style="color:var(--text-soft); font-size:14px; margin-bottom:16px">
          Select the pet you are concerned about. This helps the AI give species-specific advice.
        </p>
        @if (loadingPets) {
          <div class="loading-overlay"><div class="spinner"></div></div>
        }
        @if (!loadingPets && pets.length === 0) {
          <div class="empty-state">
            <div class="empty-icon">{{ icons.paw }}</div>
            <div class="empty-title">No pets registered</div>
            <div class="empty-sub">Add a pet first so we can provide personalized triage</div>
          </div>
        }
        <div class="pet-pick-grid">
          @for (pet of pets; track pet.id) {
            <div class="pet-pick-card" [class.selected]="selectedPet?.id === pet.id" (click)="selectPet(pet)">
              <div class="pet-pick-emoji">{{ speciesEmoji(pet.species) }}</div>
              <div class="pet-pick-name">{{ pet.name }}</div>
              <div class="pet-pick-species">{{ pet.species }}</div>
            </div>
          }
        </div>
        <div style="display:flex; gap:10px; margin-top:20px">
          <button class="btn btn-primary" [disabled]="!selectedPet" (click)="goToSymptoms()">
            Continue {{ icons.arrow }}
          </button>
          <button class="btn btn-ghost" (click)="goToSymptoms()">Skip (no pet)</button>
        </div>
      </div>
    }

    <!-- STEP 2: Symptoms -->
    @if (step === 'symptoms') {
      <div class="card" style="margin-top:20px">
        <div class="card-header"><span class="card-title">Describe the symptoms</span></div>
        @if (selectedPet) {
          <div class="pet-context-chip">
            {{ speciesEmoji(selectedPet.species) }} {{ selectedPet.name }} ({{ selectedPet.species }})
            <button class="chip-remove" (click)="selectedPet = null; selectedSpecies = null">x</button>
          </div>
        }
        <label style="font-weight:700; font-size:15px; display:block; margin:14px 0 10px">
          {{ icons.memo }} What symptoms is your pet showing?
        </label>
        <textarea
          class="symptom-textarea"
          [(ngModel)]="symptomText"
          (ngModelChange)="updateCharCount()"
          placeholder="Example: My dog suddenly collapsed, is breathing very fast and his gums look pale..."
          rows="5"
        ></textarea>
        <div class="input-actions">
          <span class="char-count" [class.over]="charCount > 2000">{{ charCount }} / 2000</span>
          <div style="flex:1"></div>
          @if (!isListening) {
            <button class="voice-btn" (click)="startVoice()" [disabled]="!voiceSupported">{{ icons.mic }} Voice Input</button>
          } @else {
            <button class="voice-btn voice-active" (click)="stopVoice()">{{ icons.stop }} Listening...</button>
          }
          <button class="btn btn-primary" [disabled]="symptomText.length < 10 || analyzing" (click)="runTriage()">
            {{ icons.search }} Analyze with AI
          </button>
        </div>
        @if (symptomText.length > 0 && symptomText.length < 10) {
          <div class="char-warning">Please enter at least 10 characters</div>
        }
      </div>
    }

    <!-- STEP 3: Analyzing - Professional Loading -->
    @if (step === 'analyzing') {
      <div class="analyzing-wrapper" style="margin-top:20px">
        <div class="analyzing-pulse-ring"></div>
        <div class="analyzing-content">
          <div class="analyzing-brain">{{ icons.brain }}</div>
          <div class="analyzing-title">Analyzing symptoms...</div>
          <div class="analyzing-sub">Mistral AI is reviewing your pet case</div>
          <div class="analyzing-progress">
            <div class="progress-bar"><div class="progress-fill" [style.width]="progressPct + '%'"></div></div>
            <div class="progress-label">{{ progressLabel }}</div>
          </div>
          <div class="analyzing-steps">
            <div class="astep" [class.done]="analyzeStep > 0" [class.active]="analyzeStep === 0">
              <div class="astep-dot"></div>
              <div class="astep-text">Searching veterinary knowledge base</div>
            </div>
            <div class="astep" [class.done]="analyzeStep > 1" [class.active]="analyzeStep === 1">
              <div class="astep-dot"></div>
              <div class="astep-text">Building clinical context</div>
            </div>
            <div class="astep" [class.done]="analyzeStep > 2" [class.active]="analyzeStep === 2">
              <div class="astep-dot"></div>
              <div class="astep-text">Consulting Mistral AI</div>
            </div>
            <div class="astep" [class.done]="analyzeStep > 3" [class.active]="analyzeStep === 3">
              <div class="astep-dot"></div>
              <div class="astep-text">Generating triage report</div>
            </div>
          </div>
        </div>
      </div>
    }

    <!-- STEP 4: Result - Professional Layout -->
    @if (step === 'result' && triageResult && parsed) {
      <div class="result-wrapper" style="margin-top:20px">

        <!-- Urgency Banner -->
        <div class="urgency-banner" [ngClass]="urgencyClass">
          <div class="urgency-left">
            <div class="urgency-icon-big">{{ urgencyIcon }}</div>
            <div>
              <div class="urgency-level-text">{{ triageResult.urgencyLevel }}</div>
              <div class="urgency-title-text">{{ urgencyTitle }}</div>
            </div>
          </div>
          <div class="urgency-badge">
            @if (triageResult.aiGenerated) {
              <span class="ai-badge">{{ icons.robot }} AI Analysis</span>
            }
          </div>
        </div>

        <!-- Assessment Card -->
        @if (parsed.assessment) {
          <div class="result-card">
            <div class="result-card-header">
              <div class="result-card-icon assess-icon">{{ icons.stethoscope }}</div>
              <div class="result-card-title">Clinical Assessment</div>
            </div>
            <div class="result-card-body">{{ parsed.assessment }}</div>
          </div>
        }

        <!-- Action Steps Card -->
        @if (parsed.action.length > 0) {
          <div class="result-card">
            <div class="result-card-header">
              <div class="result-card-icon action-icon">{{ icons.lightning }}</div>
              <div class="result-card-title">Recommended Actions</div>
            </div>
            <div class="action-list">
              @for (item of parsed.action; track $index) {
                <div class="action-item">
                  <div class="action-num">{{ $index + 1 }}</div>
                  <div class="action-text">{{ item }}</div>
                </div>
              }
            </div>
          </div>
        }

        <!-- Knowledge Sources -->
        @if ((triageResult.knowledgeSources?.length ?? 0) > 0) {
          <div class="result-card sources-card">
            <div class="result-card-header">
              <div class="result-card-icon source-icon">{{ icons.books }}</div>
              <div class="result-card-title">Knowledge Sources</div>
            </div>
            <div class="sources-chips">
              @for (src of triageResult.knowledgeSources; track src) {
                <span class="source-chip">{{ src }}</span>
              }
            </div>
          </div>
        }

        <!-- Critical CTA -->
        @if (triageResult.urgencyLevel === 'CRITICAL') {
          <div class="cta-critical">
            <div class="cta-critical-inner">
              <div class="cta-icon">{{ icons.hospital }}</div>
              <div>
                <div class="cta-title">Go to Emergency Vet Immediately</div>
                <div class="cta-sub">Do not wait. This is a life-threatening situation.</div>
              </div>
            </div>
            <div class="cta-actions">
              <a routerLink="/vets" class="btn btn-danger">{{ icons.hospital }} Find Emergency Vet</a>
              <a href="tel:emergency" class="btn btn-ghost-danger">{{ icons.phone }} Call Now</a>
            </div>
          </div>
        }

        @if (triageResult.urgencyLevel === 'URGENT') {
          <div class="cta-urgent">
            <div class="cta-urgent-text">{{ icons.warning }} See a veterinarian within the next few hours</div>
            <a routerLink="/vets" class="btn btn-primary" style="margin-top:12px">Find a Veterinarian {{ icons.arrow }}</a>
          </div>
        }

        <!-- Disclaimer -->
        <div class="disclaimer">
          {{ icons.info }} This AI assessment is not a substitute for professional veterinary diagnosis. Always consult a licensed veterinarian.
        </div>

        <div style="margin-top:16px">
          <button class="btn btn-ghost" (click)="reset()">{{ icons.refresh }} Start New Triage</button>
        </div>
      </div>
    }

    <!-- Error -->
    @if (error) {
      <div class="card error-card" style="margin-top:20px">
        <div class="error-icon-big">{{ icons.warning }}</div>
        <div class="error-title">Triage Failed</div>
        <div class="error-msg">{{ error }}</div>
        <div class="error-hint">Make sure the ai-pawly service is running on port 8000.</div>
        <button class="btn btn-primary" style="margin-top:16px" (click)="retry()">Try Again</button>
      </div>
    }
  </div>
  `,
  styles: [`
    /* Hero */
    .emergency-hero {
      background: linear-gradient(135deg, #FF4757 0%, #c0392b 100%);
      border-radius: 16px;
      padding: 28px 24px;
      display: flex;
      align-items: center;
      gap: 18px;
      margin-bottom: 20px;
      box-shadow: 0 8px 32px rgba(255,71,87,.25);
    }
    .hero-icon { font-size: 44px; }
    .hero-title { color: #fff; font-weight: 800; font-size: 22px; letter-spacing: -0.5px; }
    .hero-sub { color: rgba(255,255,255,.8); font-size: 13px; margin-top: 4px; }

    /* Step Tracker */
    .step-tracker {
      display: grid;
      grid-template-columns: repeat(4, 1fr);
      gap: 10px;
      margin-bottom: 4px;
    }
    .step-item {
      background: var(--white);
      border-radius: 12px;
      padding: 14px 10px;
      text-align: center;
      border: 2px solid var(--border);
      transition: all .2s;
    }
    .step-item.active { border-color: var(--primary); background: var(--primary-light); }
    .step-item.done { border-color: #2ED573; background: #f0fdf4; }
    .step-num {
      width: 24px; height: 24px; border-radius: 50%;
      background: var(--border); color: var(--text-soft);
      display: flex; align-items: center; justify-content: center;
      font-size: 12px; font-weight: 700; margin: 0 auto 6px;
    }
    .step-item.active .step-num { background: var(--primary); color: #fff; }
    .step-item.done .step-num { background: #2ED573; color: #fff; }
    .step-icon { font-size: 22px; margin-bottom: 4px; }
    .step-title { font-size: 12px; font-weight: 700; }
    .step-desc { font-size: 11px; color: var(--text-soft); margin-top: 2px; }

    /* Pet Selection */
    .pet-pick-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(130px, 1fr)); gap: 12px; }
    .pet-pick-card {
      background: var(--white); border: 2px solid var(--border);
      border-radius: 12px; padding: 16px; text-align: center;
      cursor: pointer; transition: all .15s;
    }
    .pet-pick-card:hover { transform: translateY(-2px); border-color: var(--primary); }
    .pet-pick-card.selected { border-color: var(--primary); background: var(--primary-light); }
    .pet-pick-emoji { font-size: 36px; }
    .pet-pick-name { font-weight: 700; font-size: 14px; margin-top: 6px; }
    .pet-pick-species { font-size: 12px; color: var(--text-soft); }
    .pet-context-chip {
      display: inline-flex; align-items: center; gap: 6px;
      padding: 6px 12px; background: var(--primary-light);
      color: var(--primary); border-radius: 20px; font-size: 13px; font-weight: 600;
    }
    .chip-remove { background: none; border: none; cursor: pointer; color: var(--primary); }

    /* Symptom */
    .symptom-textarea {
      width: 100%; min-height: 110px; padding: 14px;
      border-radius: 10px; border: 2px solid var(--border);
      font-family: inherit; font-size: 14px; resize: vertical; transition: border-color .15s;
    }
    .symptom-textarea:focus { outline: none; border-color: var(--primary); }
    .input-actions { display: flex; gap: 10px; margin-top: 12px; align-items: center; }
    .char-count { font-size: 12px; color: var(--text-soft); }
    .char-count.over { color: var(--danger); font-weight: 600; }
    .char-warning { font-size: 12px; color: #f39c12; margin-top: 6px; }
    .voice-btn {
      background: var(--neutral); border: 1px solid var(--border);
      border-radius: 8px; padding: 8px 14px; font-size: 13px; cursor: pointer;
    }
    .voice-btn:hover:not(:disabled) { background: var(--primary-light); }
    .voice-btn:disabled { opacity: .5; cursor: not-allowed; }
    .voice-btn.voice-active { background: #ffeaea; border-color: var(--danger); animation: pulse 1s infinite; }
    @keyframes pulse { 0%,100%{opacity:1} 50%{opacity:.6} }

    /* ── ANALYZING - Professional ── */
    .analyzing-wrapper {
      position: relative;
      background: #fff;
      border-radius: 20px;
      padding: 48px 32px;
      text-align: center;
      border: 1px solid var(--border);
      box-shadow: 0 4px 32px rgba(0,0,0,.06);
      overflow: hidden;
    }
    .analyzing-pulse-ring {
      position: absolute;
      top: 50%; left: 50%;
      transform: translate(-50%, -50%);
      width: 300px; height: 300px;
      border-radius: 50%;
      background: radial-gradient(circle, rgba(99,102,241,.06) 0%, transparent 70%);
      animation: pulseRing 2s ease-in-out infinite;
      pointer-events: none;
    }
    @keyframes pulseRing { 0%,100%{transform:translate(-50%,-50%) scale(1);opacity:1} 50%{transform:translate(-50%,-50%) scale(1.15);opacity:.5} }
    .analyzing-content { position: relative; z-index: 1; }
    .analyzing-brain {
      font-size: 56px;
      display: inline-block;
      animation: brainFloat 3s ease-in-out infinite;
      margin-bottom: 16px;
    }
    @keyframes brainFloat { 0%,100%{transform:translateY(0)} 50%{transform:translateY(-8px)} }
    .analyzing-title { font-size: 22px; font-weight: 800; color: var(--text); margin-bottom: 6px; }
    .analyzing-sub { font-size: 14px; color: var(--text-soft); margin-bottom: 28px; }

    .analyzing-progress { margin-bottom: 28px; }
    .progress-bar {
      height: 6px; background: var(--border); border-radius: 10px;
      overflow: hidden; margin-bottom: 8px;
    }
    .progress-fill {
      height: 100%;
      background: linear-gradient(90deg, var(--primary), #818cf8);
      border-radius: 10px;
      transition: width .6s ease;
    }
    .progress-label { font-size: 12px; color: var(--text-soft); font-weight: 500; }

    .analyzing-steps { display: flex; flex-direction: column; gap: 10px; max-width: 320px; margin: 0 auto; text-align: left; }
    .astep { display: flex; align-items: center; gap: 12px; padding: 10px 14px; border-radius: 10px; transition: all .3s; }
    .astep.active { background: var(--primary-light); }
    .astep.done { opacity: .6; }
    .astep-dot {
      width: 10px; height: 10px; border-radius: 50%;
      background: var(--border); flex-shrink: 0; transition: background .3s;
    }
    .astep.active .astep-dot { background: var(--primary); animation: dotPulse .8s ease-in-out infinite; }
    .astep.done .astep-dot { background: #2ED573; }
    @keyframes dotPulse { 0%,100%{transform:scale(1)} 50%{transform:scale(1.4)} }
    .astep-text { font-size: 13px; font-weight: 500; color: var(--text); }
    .astep.active .astep-text { color: var(--primary); font-weight: 600; }

    /* ── RESULT ── */
    .result-wrapper { display: flex; flex-direction: column; gap: 14px; }

    .urgency-banner {
      border-radius: 16px; padding: 22px 24px;
      display: flex; align-items: center; justify-content: space-between;
      box-shadow: 0 4px 20px rgba(0,0,0,.1);
    }
    .urgency-banner.critical { background: linear-gradient(135deg, #FF4757, #c0392b); }
    .urgency-banner.urgent   { background: linear-gradient(135deg, #FF6B35, #e55a25); }
    .urgency-banner.moderate { background: linear-gradient(135deg, #f39c12, #d68910); }
    .urgency-banner.mild     { background: linear-gradient(135deg, #2ED573, #27ae60); }
    .urgency-banner.unknown  { background: #f8f9fa; border: 2px solid var(--border); }
    .urgency-left { display: flex; align-items: center; gap: 16px; }
    .urgency-icon-big { font-size: 42px; }
    .urgency-level-text { color: #fff; font-size: 11px; font-weight: 700; letter-spacing: 2px; text-transform: uppercase; opacity: .85; }
    .urgency-banner.unknown .urgency-level-text { color: var(--text-soft); }
    .urgency-title-text { color: #fff; font-size: 20px; font-weight: 800; margin-top: 2px; }
    .urgency-banner.unknown .urgency-title-text { color: var(--text); }
    .ai-badge {
      background: rgba(255,255,255,.2); color: #fff;
      padding: 6px 12px; border-radius: 20px; font-size: 12px; font-weight: 600;
      backdrop-filter: blur(4px);
    }
    .urgency-banner.unknown .ai-badge { background: var(--primary-light); color: var(--primary); }

    .result-card {
      background: #fff; border-radius: 14px;
      border: 1px solid var(--border); overflow: hidden;
      box-shadow: 0 2px 12px rgba(0,0,0,.04);
    }
    .result-card-header {
      display: flex; align-items: center; gap: 12px;
      padding: 16px 20px; border-bottom: 1px solid var(--border);
      background: #fafafa;
    }
    .result-card-icon {
      width: 36px; height: 36px; border-radius: 10px;
      display: flex; align-items: center; justify-content: center; font-size: 18px;
    }
    .assess-icon { background: #eef2ff; }
    .action-icon { background: #fff7ed; }
    .source-icon { background: #f0fdf4; }
    .result-card-title { font-size: 15px; font-weight: 700; color: var(--text); }
    .result-card-body { padding: 18px 20px; font-size: 14px; line-height: 1.7; color: var(--text); }

    .action-list { padding: 16px 20px; display: flex; flex-direction: column; gap: 10px; }
    .action-item { display: flex; align-items: flex-start; gap: 12px; }
    .action-num {
      min-width: 26px; height: 26px; border-radius: 50%;
      background: var(--primary); color: #fff;
      display: flex; align-items: center; justify-content: center;
      font-size: 12px; font-weight: 700; flex-shrink: 0; margin-top: 1px;
    }
    .action-text { font-size: 14px; line-height: 1.6; color: var(--text); }

    .sources-card { }
    .sources-chips { padding: 14px 20px; display: flex; flex-wrap: wrap; gap: 8px; }
    .source-chip {
      background: #f0fdf4; color: #166534;
      border: 1px solid #bbf7d0;
      padding: 4px 12px; border-radius: 20px; font-size: 12px; font-weight: 600;
    }

    .cta-critical {
      background: linear-gradient(135deg, #fff5f5, #ffe4e6);
      border: 2px solid #fca5a5; border-radius: 14px; padding: 20px;
    }
    .cta-critical-inner { display: flex; align-items: center; gap: 14px; margin-bottom: 16px; }
    .cta-icon { font-size: 36px; }
    .cta-title { font-weight: 800; font-size: 16px; color: #991b1b; }
    .cta-sub { font-size: 13px; color: #b91c1c; margin-top: 3px; }
    .cta-actions { display: flex; gap: 10px; }
    .btn-danger { background: #dc2626; color: #fff; border: none; padding: 10px 20px; border-radius: 8px; font-weight: 600; cursor: pointer; text-decoration: none; display: inline-flex; align-items: center; gap: 6px; }
    .btn-ghost-danger { background: transparent; color: #dc2626; border: 2px solid #dc2626; padding: 10px 20px; border-radius: 8px; font-weight: 600; cursor: pointer; text-decoration: none; display: inline-flex; align-items: center; gap: 6px; }

    .cta-urgent {
      background: #fff7ed; border: 2px solid #fed7aa;
      border-radius: 14px; padding: 20px;
    }
    .cta-urgent-text { font-weight: 700; color: #c2410c; font-size: 15px; }

    .disclaimer {
      font-size: 12px; color: var(--text-soft); font-style: italic;
      padding: 12px 16px; background: #fafafa; border-radius: 10px;
      border: 1px solid var(--border); line-height: 1.6;
    }

    /* Error */
    .error-card { text-align: center; padding: 40px 24px; }
    .error-icon-big { font-size: 48px; margin-bottom: 12px; }
    .error-title { font-size: 18px; font-weight: 700; color: var(--danger); margin-bottom: 8px; }
    .error-msg { font-size: 14px; color: var(--text-soft); }
    .error-hint { font-size: 12px; color: var(--text-soft); margin-top: 8px; font-style: italic; }
  `]
})
export class AiEmergencyComponent implements OnInit, OnDestroy {
  step: TriageStep = 'pet-select';
  stepIndex = 0;

  pets: Pet[] = [];
  loadingPets = true;
  selectedPet: Pet | null = null;
  selectedSpecies: string | null = null;

  symptomText = '';
  charCount = 0;
  analyzing = false;
  triageResult: TriageResponse | null = null;
  parsed: ParsedResult | null = null;
  error: string | null = null;

  // Loading animation state
  analyzeStep = 0;
  progressPct = 0;
  progressLabel = 'Initializing...';
  private analyzeTimer: any = null;

  // Voice
  isListening = false;
  voiceSupported = false;
  private recognition: any = null;

  icons = {
    siren:      String.fromCodePoint(0x1F6A8),
    paw:        String.fromCodePoint(0x1F43E),
    clipboard:  String.fromCodePoint(0x1F4CB),
    brain:      String.fromCodePoint(0x1F9E0),
    check:      String.fromCodePoint(0x2705),
    arrow:      String.fromCodePoint(0x27A1),
    memo:       String.fromCodePoint(0x1F4DD),
    mic:        String.fromCodePoint(0x1F3A4),
    stop:       String.fromCodePoint(0x23F9),
    search:     String.fromCodePoint(0x1F50D),
    refresh:    String.fromCodePoint(0x1F504),
    robot:      String.fromCodePoint(0x1F916),
    warning:    String.fromCodePoint(0x26A0),
    books:      String.fromCodePoint(0x1F4DA),
    hospital:   String.fromCodePoint(0x1F3E5),
    phone:      String.fromCodePoint(0x1F4DE),
    stethoscope:String.fromCodePoint(0x1FA7A),
    lightning:  String.fromCodePoint(0x26A1),
    info:       String.fromCodePoint(0x2139),
    question:   String.fromCodePoint(0x2753)
  };

  constructor(private triageSvc: TriageService, private petSvc: PetService) {}

  ngOnInit(): void { this.loadPets(); this.initVoice(); }
  ngOnDestroy(): void { if (this.analyzeTimer) clearInterval(this.analyzeTimer); }

  loadPets(): void {
    this.petSvc.getMyPets().subscribe({
      next: (pets: Pet[]) => { this.pets = pets; this.loadingPets = false; },
      error: () => this.loadingPets = false
    });
  }

  selectPet(pet: Pet): void { this.selectedPet = pet; this.selectedSpecies = pet.species; }
  goToSymptoms(): void { this.step = 'symptoms'; this.stepIndex = 1; }
  updateCharCount(): void { this.charCount = this.symptomText.length; }

  startAnalyzeAnimation(): void {
    const steps = [
      { pct: 15, label: 'Searching knowledge base...' },
      { pct: 40, label: 'Building clinical context...' },
      { pct: 70, label: 'Consulting Mistral AI...' },
      { pct: 90, label: 'Generating triage report...' },
    ];
    let i = 0;
    this.analyzeStep = 0;
    this.progressPct = 5;
    this.progressLabel = 'Initializing...';
    this.analyzeTimer = setInterval(() => {
      if (i < steps.length) {
        this.analyzeStep = i;
        this.progressPct = steps[i].pct;
        this.progressLabel = steps[i].label;
        i++;
      }
    }, 1800);
  }

  stopAnalyzeAnimation(): void {
    if (this.analyzeTimer) { clearInterval(this.analyzeTimer); this.analyzeTimer = null; }
    this.progressPct = 100;
    this.progressLabel = 'Complete';
    this.analyzeStep = 4;
  }

  runTriage(): void {
    if (this.symptomText.length < 10) return;
    this.step = 'analyzing';
    this.stepIndex = 2;
    this.analyzing = true;
    this.error = null;
    this.startAnalyzeAnimation();

    this.triageSvc.performTriage({
      symptoms: this.symptomText,
      species: this.selectedSpecies ?? undefined,
      petName: this.selectedPet?.name ?? undefined,
      petId: this.selectedPet?.id ?? undefined
    }).subscribe({
      next: (res: TriageResponse) => {
        this.stopAnalyzeAnimation();
        setTimeout(() => {
          this.triageResult = res;
          this.parsed = this.parseAssessment(res.assessment);
          this.step = 'result';
          this.stepIndex = 3;
          this.analyzing = false;
        }, 600);
      },
      error: (err: any) => {
        this.stopAnalyzeAnimation();
        this.analyzing = false;
        this.error = err?.error?.message || err?.message || 'Triage service is unavailable.';
        this.step = 'symptoms';
        this.stepIndex = 1;
      }
    });
  }

  parseAssessment(raw: string): ParsedResult {
    // Strip markdown bold markers **text** -> text
    const clean = raw.replace(/\*\*([^*]+)\*\*/g, '$1').replace(/\*([^*]+)\*/g, '$1').trim();

    let urgency = '';
    let assessment = '';
    let action: string[] = [];

    // Extract URGENCY line
    const urgencyMatch = clean.match(/URGENCY\s*:\s*([^\n]+)/i);
    if (urgencyMatch) urgency = urgencyMatch[1].trim();

    // Extract ASSESSMENT block
    const assessMatch = clean.match(/ASSESSMENT\s*:\s*([\s\S]*?)(?=ACTION\s*:|$)/i);
    if (assessMatch) assessment = assessMatch[1].trim();

    // Extract ACTION block and split into bullet points
    const actionMatch = clean.match(/ACTION\s*:\s*([\s\S]*?)$/i);
    if (actionMatch) {
      action = actionMatch[1]
        .split(/\n|(?<=\.)\s+(?=-)|(?=^-)/m)
        .map(l => l.replace(/^[-*\d.)\s]+/, '').trim())
        .filter(l => l.length > 4);
    }

    // If parsing failed (different format), show the whole thing cleaned up
    if (!assessment && !action.length) {
      const lines = clean.split('\n').map(l => l.trim()).filter(Boolean);
      assessment = lines.join(' ');
    }

    return { urgency, assessment, action, raw: clean };
  }

  retry(): void { this.error = null; this.runTriage(); }

  reset(): void {
    this.step = 'pet-select'; this.stepIndex = 0;
    this.symptomText = ''; this.charCount = 0;
    this.triageResult = null; this.parsed = null;
    this.error = null; this.selectedPet = null; this.selectedSpecies = null;
  }

  initVoice(): void {
    const SR = (window as any).SpeechRecognition || (window as any).webkitSpeechRecognition;
    if (SR) {
      this.voiceSupported = true;
      this.recognition = new SR();
      this.recognition.continuous = true;
      this.recognition.interimResults = true;
      this.recognition.onresult = (event: any) => {
        let t = '';
        for (let i = 0; i < event.results.length; i++) t += event.results[i][0].transcript;
        this.symptomText = t; this.updateCharCount();
      };
      this.recognition.onerror = () => { this.isListening = false; };
      this.recognition.onend = () => { this.isListening = false; };
    }
  }
  startVoice(): void { if (this.recognition) { this.symptomText = ''; this.recognition.start(); this.isListening = true; } }
  stopVoice(): void { if (this.recognition) { this.recognition.stop(); this.isListening = false; } }

  get urgencyClass(): string { return (this.triageResult?.urgencyLevel || 'unknown').toLowerCase(); }

  get urgencyIcon(): string {
    const m: Record<string,string> = {
      CRITICAL: String.fromCodePoint(0x1F6A8),
      URGENT:   String.fromCodePoint(0x26A0),
      MODERATE: String.fromCodePoint(0x1F4CB),
      MILD:     String.fromCodePoint(0x2705),
      UNKNOWN:  String.fromCodePoint(0x2753)
    };
    return m[this.triageResult?.urgencyLevel ?? 'UNKNOWN'] ?? String.fromCodePoint(0x2753);
  }

  get urgencyTitle(): string {
    const m: Record<string,string> = {
      CRITICAL: 'Immediate Emergency',
      URGENT:   'See a Vet Soon',
      MODERATE: 'Monitor & Schedule Visit',
      MILD:     'Home Monitoring',
      UNKNOWN:  'Assessment Inconclusive'
    };
    return m[this.triageResult?.urgencyLevel ?? 'UNKNOWN'] ?? 'Assessment Complete';
  }

  speciesEmoji(species: string): string {
    const map: Record<string,string> = {
      dog: String.fromCodePoint(0x1F415), cat: String.fromCodePoint(0x1F408),
      rabbit: String.fromCodePoint(0x1F407), bird: String.fromCodePoint(0x1F99C),
      fish: String.fromCodePoint(0x1F420), hamster: String.fromCodePoint(0x1F439),
      turtle: String.fromCodePoint(0x1F422), horse: String.fromCodePoint(0x1F40E)
    };
    return map[species?.toLowerCase()] ?? String.fromCodePoint(0x1F43E);
  }
}