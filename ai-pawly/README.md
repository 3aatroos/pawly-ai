# ai-pawly - Python AI Microservice for Pawly

A lightweight FastAPI service that handles RAG retrieval, Mistral inference via Ollama, PDF ingestion, and triage generation.

## Setup

pip install -r requirements.txt

## Run

uvicorn main:app --reload

## Architecture

Angular -> Spring Boot (8081) -> ai-pawly FastAPI (8000) -> Ollama/Mistral (11434)
                                                        -> MySQL pawly_db
