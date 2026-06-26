# pytest 공용 경로 설정. pricing-engine 을 sys.path 에 올려 `app.*` import 가능하게 한다.
import sys
from pathlib import Path

PRICING_ENGINE_DIR = Path(__file__).resolve().parents[1]   # pricing-engine/
REPO_ROOT = PRICING_ENGINE_DIR.parent                       # 저장소 루트

if str(PRICING_ENGINE_DIR) not in sys.path:
    sys.path.insert(0, str(PRICING_ENGINE_DIR))
