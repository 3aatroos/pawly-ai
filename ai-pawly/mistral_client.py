import requests
import logging
from config import MISTRAL_API_KEY, MISTRAL_MODEL, MISTRAL_API_URL, MISTRAL_TEMP, MISTRAL_TIMEOUT

logger = logging.getLogger(__name__)


def call_mistral(prompt: str, temperature: float = None) -> str:
    """Call Mistral via the official Mistral API."""
    if not MISTRAL_API_KEY:
        logger.error('MISTRAL_API_KEY is not set in .env')
        return 'AI service unavailable: MISTRAL_API_KEY is missing. Add it to your .env file.'

    temp = temperature if temperature is not None else MISTRAL_TEMP
    headers = {
        'Authorization': f'Bearer {MISTRAL_API_KEY}',
        'Content-Type': 'application/json',
    }
    payload = {
        'model': MISTRAL_MODEL,
        'temperature': temp,
        'messages': [
            {'role': 'user', 'content': prompt}
        ]
    }

    try:
        logger.info(f'Calling Mistral API model={MISTRAL_MODEL}')
        resp = requests.post(MISTRAL_API_URL, headers=headers, json=payload, timeout=MISTRAL_TIMEOUT)
        logger.info(f'Mistral API status: {resp.status_code}')

        if resp.status_code == 401:
            logger.error('Mistral API: invalid API key')
            return 'AI service error: invalid API key. Check MISTRAL_API_KEY in your .env file.'
        elif resp.status_code == 429:
            logger.error('Mistral API: rate limit exceeded')
            return 'AI service error: rate limit exceeded. Please wait a moment and try again.'
        elif resp.status_code != 200:
            logger.error(f'Mistral API error {resp.status_code}: {resp.text[:300]}')
            return f'AI service error (HTTP {resp.status_code}): {resp.text[:200]}'

        data = resp.json()
        text = data['choices'][0]['message']['content'].strip()
        logger.info(f'Mistral API responded with {len(text)} chars')
        return text if text else 'No response generated.'

    except requests.exceptions.ConnectionError:
        logger.error('Cannot connect to Mistral API.')
        return 'AI service unavailable: cannot reach api.mistral.ai. Check your internet connection.'
    except requests.exceptions.Timeout:
        logger.error('Mistral API request timed out')
        return 'AI service timed out. Please try again.'
    except (KeyError, IndexError) as e:
        logger.error(f'Unexpected Mistral API response format: {e}')
        return 'AI service returned an unexpected response format.'
    except Exception as e:
        logger.error(f'Mistral API unexpected error: {e}')
        return f'Error generating triage response: {str(e)}'