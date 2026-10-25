# QLoRA — recommandations Pet Store

Fine-tuning QLoRA du modèle public `Qwen/Qwen2.5-0.5B-Instruct` pour scorer un produit candidat (0.0 à 1.0) et rédiger une courte justification. Le jeu d’entraînement est le JSONL synthétique de `ai-pawly/dataset/` (aucune donnée personnelle, aucune clé d’API).

Les poids complets du modèle de base ne sont pas réécrits. L’entraînement ne sauvegarde que l’adaptateur LoRA.

## Vérification faite avant l’implémentation

Sources consultées le 25 octobre 2026 :

- carte et fichiers bruts : [Qwen/Qwen2.5-0.5B-Instruct](https://huggingface.co/Qwen/Qwen2.5-0.5B-Instruct)
- `config.json` : `model_type = qwen2`, `architectures = [Qwen2ForCausalLM]`, `transformers_version = 4.43.1`, 24 couches, hidden size 896, 14 têtes Q et 2 têtes KV
- `tokenizer_config.json` : `tokenizer_class = Qwen2Tokenizer`, vocabulaire 151 936, pad `<|endoftext|>` (id 151643), eos `<|im_end|>` (id 151645)
- `LICENSE` : Apache 2.0
- blog Qwen2.5 : les modèles ouverts sont en Apache 2.0, sauf les variantes 3B et 72B, qui ne sont pas utilisées ici
- la carte modèle indique qu’avec `transformers < 4.37.0` le chargement échoue par `KeyError: 'qwen2'`

| Critère | Constat |
|---|---|
| Licence | Apache 2.0, dépôt public, pas de gate Hugging Face, pas de token |
| Transformers | Architecture native `Qwen2ForCausalLM`. Minimum carte : 4.37.0. Ce projet exige `>= 4.46.0,< 5` pour `TrainingArguments.eval_strategy` |
| Tokenizer | `Qwen2Tokenizer` / `Qwen2TokenizerFast` (BPE). Le pad officiel est `<|endoftext|>`, distinct de l’eos. `trust_remote_code` reste à `False` |
| Paramètres | 0,49 milliard (carte modèle) |
| VRAM des poids 4-bit | environ 0,25 Go : 0,49e9 paramètres × 0,5 octet (NF4 + double quantification, QLoRA) |
| VRAM d’entraînement | seuil du code : **3 Go libres**. À séquence 512, batch 1 et gradient checkpointing, des mesures publiques de QLoRA sur ce modèle tournent autour de 1,7–2,4 Gio. L’optimiseur 8-bit ne porte que sur les adaptateurs |
| GPU de cette machine | RTX 3050 Laptop, 4096 Mio au total, 3703 Mio libres au moment du contrôle (`nvidia-smi`). `device.py` a répondu CUDA indisponible parce que PyTorch n’est pas encore installé. Une fois le wheel CUDA installé, 3,6 Go libres passent le seuil de 3 Go |
| RAM hôte | 31,7 Go au total, 18,1 Go libres. Le fallback fp32 est refusé sous 6 Go libres. Le chargement GPU est refusé sous 3 Go de RAM hôte |

Le pad n’est pas remplacé par l’eos : les deux tokens ont des rôles différents dans ce tokenizer.

### Pourquoi ce modèle

La tâche est un format court et stable : profil, historique, produit, score, justification. Le split d’entraînement contient 341 exemples synthétiques. Un modèle instruction de 0,49B apprend ce format. En QLoRA 4-bit, séquence 512, il vise un GPU de 4 Go comme la RTX 3050 de cette machine, et reste chargeable en fp32 sur CPU si CUDA est absent ou si la VRAM libre passe sous 3 Go.

| Modèle | Licence | Tokenizer | Ordre de grandeur QLoRA | Décision |
|---|---|---|---|---|
| **Qwen2.5-0.5B-Instruct** | Apache 2.0, non gated | Qwen2Tokenizer | ~3 Go libres | retenu |
| Qwen2.5-1.5B-Instruct | Apache 2.0 | Qwen2Tokenizer | plus de marge GPU | écarté : le 0,5B suffit pour 341 exemples |
| TinyLlama-1.1B-Chat-v1.0 | Apache 2.0 | LlamaTokenizer | ~6 Go | écarté : plus gros, moins adapté au format instruction récent |
| Phi-3-mini-4k-instruct | MIT | LlamaTokenizer | ~8 Go | écarté : 3,8B inutile pour ce format |
| Mistral-7B-Instruct-v0.3 | Apache 2.0 | LlamaTokenizer | ~10–16 Go | écarté : VRAM trop haute pour le volume de données. Le service FastAPI continue d’appeler Mistral via Ollama, sans partager ces poids |
| gemma-2-2b-it | licence Gemma, dépôt gated | GemmaTokenizer | ~8 Go | écarté : acceptation de licence et token Hugging Face |

`r = 8` et `alpha = 16` sont journalisés. Sur 341 lignes, un rang faible limite le nombre de poids entraînables. Les modules cibles sont ceux de Qwen2 : `q_proj`, `k_proj`, `v_proj`, `o_proj`, `gate_proj`, `up_proj`, `down_proj`.

## Fichiers

| Fichier | Rôle |
|---|---|
| `requirements.txt` | PyTorch, Transformers, PEFT, bitsandbytes, Accelerate |
| `config.py` | Seed 42, modèle, hyperparamètres, journal JSON |
| `device.py` | Sonde mémoire, QLoRA 4-bit CUDA, fallback CPU explicite |
| `prepare_data.py` | Splits, fuites, prompt, masque des labels |
| `train.py` | `Trainer`, adaptateur seul |
| `evaluate.py` | Génération sur le test et `metrics.json` |
| `infer.py` | Score d’un ou plusieurs produits |
| `metrics.py` | MAE, RMSE, Pearson, accuracy, macro-F1 |
| `modeling.py` | `BitsAndBytesConfig`, `LoraConfig`, chargement |
| `adapters.py` | Contrôle « adaptateur seulement » |
| `tests/` | Tests sans téléchargement du modèle |

Le `requirements.txt` du service FastAPI n’est pas modifié : la pile QLoRA est lourde et reste dans ce dossier.

## Installation

Python 3.10, 3.11 ou 3.12 est la plage fiable pour les wheels PyTorch et bitsandbytes. Les tests de logique tournent aussi sur un interpréteur plus récent, sans GPU.

```bash
cd ai-pawly/qlora
python -m venv .venv
.venv\Scripts\activate
pip install -r requirements.txt
python -m pytest tests -q
```

Contrôle mémoire, sans télécharger le modèle :

```bash
python device.py
```

## Données

Les splits sont ceux du pipeline dataset, seed **42**, stratification `relevance_class × species`.

| Split | Fichier | Rôle |
|---|---|---|
| train | `../dataset/splits/train.jsonl` | entraînement |
| validation | `../dataset/splits/validation.jsonl` | early stopping sur `eval_loss` |
| test | `../dataset/splits/test.jsonl` | évaluation finale, jamais lu par `train.py` |

`prepare_data.py` vérifie le seed de `split_summary.json`, les effectifs, l’unicité des ids et l’absence de fuite `(profil, product_id)` entre splits. Le prompt reprend le schéma du dataset. La justification gold n’entre que dans le tour assistant. Les tokens du prompt sont masqués à `-100`.

Le corps du prompt est enveloppé dans le chat template Qwen2 (`<|im_start|>` / `<|im_end|>`) pour rester aligné avec le modèle instruction.

```bash
python prepare_data.py
```

Cela écrit `outputs/prepared/prepare_report.json` et un aperçu des prompts.

## Entraînement

```bash
python train.py
python train.py --cpu
python train.py --max-steps 2 --overwrite
```

`Trainer` (Transformers) est utilisé, avec masquage du prompt. `SFTTrainer` n’est pas retenu : son constructeur change d’une version de TRL à l’autre, alors que l’objectif (loss sur la complétion seulement) est le même avec `Trainer`.

Avant le chargement, le script écrit :

- `outputs/qwen25-0.5b-qlora/hyperparameters.json` — seed, LoRA, quantification, learning rate, epochs, batch, accumulation, longueur, versions de bibliothèques
- `outputs/qwen25-0.5b-qlora/device_report.json` — mémoire avant et après chargement

Hyperparamètres par défaut, tous journalisés : AdamW (paged 8-bit sur GPU, `adamw_torch` sur CPU), lr `2e-4`, 2 epochs, batch 1, accumulation 8, warmup 0.05, scheduler cosine, max grad norm 0.3, séquence 512, seed 42. `report_to` vaut `none` : aucun service externe, aucune clé.

Quantification GPU :

```python
BitsAndBytesConfig(
    load_in_4bit=True,
    bnb_4bit_quant_type="nf4",
    bnb_4bit_use_double_quant=True,
    bnb_4bit_compute_dtype=torch.bfloat16,  # ou float16 si le GPU n'a pas bf16
)
```

Le chemin 4-bit n’est pris que si CUDA répond, que la VRAM libre atteint 3 Go, et que `bitsandbytes` s’importe. Sinon le journal contient une raison qui commence par `Explicit CPU fallback` et l’entraînement continue en LoRA fp32, sans quantification. Si la RAM libre est sous le plancher, le processus s’arrête avant `from_pretrained`. Les prompts font au plus environ 1 800 caractères : à la longueur 512, les historiques les plus longs peuvent être ignorés, et `metrics_train.json` indique combien.

`--cpu` force ce fallback même sur une machine GPU.

Après l’entraînement, `assert_only_lora_trainable` refuse un modèle dont un poids de base est entraînable. La sauvegarde passe par `save_pretrained` de PEFT vers `outputs/qwen25-0.5b-qlora/adapter/` :

- `adapter_config.json`
- `adapter_model.safetensors`
- les fichiers du tokenizer, pour recharger le même vocabulaire

`model.safetensors`, `pytorch_model.bin` et les shards `model-*-of-*.safetensors` font échouer la sauvegarde. Les adaptateurs ne sont pas fusionnés dans les poids de base. `metrics_train.json` contient la loss de validation et l’historique du `Trainer`.

Le modèle public est téléchargé par Hugging Face au premier lancement (environ 1 Go en bf16, puis quantifié en mémoire). Aucun token n’est écrit dans le code. Un `HF_TOKEN` déjà présent dans l’environnement n’est pas requis pour ce dépôt public.

## Évaluation et inférence

```bash
python evaluate.py --adapter outputs/qwen25-0.5b-qlora/adapter
python evaluate.py --adapter outputs/qwen25-0.5b-qlora/adapter --limit 20
python infer.py --adapter outputs/qwen25-0.5b-qlora/adapter --input examples/example.json
```

La génération est gloutonne (`do_sample=False`). `evaluate.py` lit uniquement le split test et écrit `predictions.jsonl` et `metrics.json`.

| Métrique | Définition |
|---|---|
| `parse_success_rate` | part des sorties qui contiennent `Relevance score:` |
| `mae`, `rmse` | erreur sur le score, exemples parsés |
| `pearson_r` | corrélation score gold / score prédit |
| `class_accuracy`, `macro_f1` | classes `not_relevant` ≤ 0.25, `somewhat` ≤ 0.50, `relevant` ≤ 0.75, sinon `highly_relevant` |
| `strict_class_accuracy` | même accuracy, une sortie non parsée compte comme une erreur |
| `mean_justification_words` | longueur moyenne de la justification parsée |

Les bandes de classes sont celles de `dataset/generate_synthetic.py`.

## Tests

```bash
python -m pytest tests -q
```

Ils couvrent le prompt, le masque de loss, les splits réels et leur seed, les métriques, le choix GPU / fallback CPU, le refus d’un checkpoint complet, et l’absence de secret en dur. Ils ne téléchargent pas le modèle et ne lancent pas d’entraînement.

L’entraînement GPU n’a pas été exécuté dans l’environnement de développement courant : l’interpréteur par défaut n’a pas PyTorch. Le passage réel se fait après `pip install -r requirements.txt` sur un Python 3.10–3.12, avec CUDA pour le chemin 4-bit.

## Service Pawly

Une fois l'adaptateur écrit dans `outputs/qwen25-0.5b-qlora/adapter`, FastAPI le charge à la demande pour `POST /recommendations/score`. Le contrat HTTP, le repli content-based et le jeton interne sont dans `../recommendations/API.md`. `QLORA_ENABLED=off` répond sans charger le modèle.

