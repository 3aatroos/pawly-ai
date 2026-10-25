# ai-pawly - Python AI Microservice for Pawly

A lightweight FastAPI service that handles RAG retrieval, Mistral inference via Ollama, PDF ingestion, and triage generation.

## Setup

pip install -r requirements.txt

## Run

uvicorn main:app --reload

## Architecture

Angular -> Spring Boot (8081) -> ai-pawly FastAPI (8000) -> Ollama/Mistral (11434)
                                                        -> MySQL pawly_db

## QLoRA recommendations

`qlora/` fine-tunes `Qwen/Qwen2.5-0.5B-Instruct` with PEFT LoRA and 4-bit bitsandbytes. See `qlora/README.md`.

`recommendations/` scores catalogue candidates for Spring Boot: `POST /recommendations/score`. QLoRA is used when an adapter is present; otherwise the content-based fallback answers. Contract, timeouts, and the internal token are documented in `recommendations/API.md`. The FastAPI requirements in this folder stay separate from the QLoRA training stack.

