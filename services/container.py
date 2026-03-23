from core.config import get_settings
from services.stream_manager import StreamManager

_stream_manager = None


async def get_stream_manager() -> StreamManager:
    global _stream_manager
    if _stream_manager is None:
        _stream_manager = StreamManager(settings=get_settings())
    return _stream_manager
