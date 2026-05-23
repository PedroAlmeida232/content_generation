import os
from pathlib import Path
import sys

# JWT must be set before app modules load settings.
os.environ.setdefault("JWT_SECRET", "12345678901234567890123456789012")

PROJECT_ROOT = Path(__file__).resolve().parents[1]

if str(PROJECT_ROOT) not in sys.path:
    sys.path.insert(0, str(PROJECT_ROOT))
