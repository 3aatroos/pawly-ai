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

# Gemini API (adoption feed). The key is read here and sent only as a header.
GEMINI_API_KEY = os.getenv('GEMINI_API_KEY', '')
GEMINI_MODEL   = os.getenv('GEMINI_MODEL', 'gemini-3.5-flash-lite') or 'gemini-3.5-flash-lite'
try:
    GEMINI_TIMEOUT = int(os.getenv('GEMINI_TIMEOUT', '60'))
except ValueError:
    GEMINI_TIMEOUT = 60
if GEMINI_TIMEOUT <= 0:
    GEMINI_TIMEOUT = 60
