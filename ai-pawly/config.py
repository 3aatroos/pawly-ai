import os
from dotenv import load_dotenv

load_dotenv()

DB_HOST     = os.getenv('DB_HOST', 'localhost')
DB_PORT     = os.getenv('DB_PORT', '3306')
DB_NAME     = os.getenv('DB_NAME', 'pawly_db')
DB_USER     = os.getenv('DB_USER', 'root')
DB_PASSWORD = os.getenv('DB_PASSWORD', '')

# Mistral API (cloud)
MISTRAL_API_KEY   = os.getenv('MISTRAL_API_KEY', '')
MISTRAL_MODEL     = os.getenv('MISTRAL_MODEL', 'mistral-small-latest')
MISTRAL_API_URL   = 'https://api.mistral.ai/v1/chat/completions'
MISTRAL_TEMP      = float(os.getenv('MISTRAL_TEMPERATURE', '0.3'))
MISTRAL_TIMEOUT   = int(os.getenv('MISTRAL_TIMEOUT', '60'))