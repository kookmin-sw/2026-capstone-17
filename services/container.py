from functools import lru_cache

from core.config import get_settings
from services.stream_manager import StreamManager


@lru_cache
def get_stream_manager() -> StreamManager:
    return StreamManager(settings=get_settings())
