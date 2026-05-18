import asyncio
import logging
import time
from typing import Protocol

from workers.types import VideoFrame

try:
    import av
except ImportError:  # pragma: no cover
    av = None

logger = logging.getLogger(__name__)


class MediaSource(Protocol):
    async def read_frame(self) -> VideoFrame | None:
        ...

    async def close(self) -> None:
        ...


class DummyMediaSource:
    """Synthetic source for local testing."""

    def __init__(self, fps: int = 30) -> None:
        self._interval_s = 1.0 / fps
        self._interval_us = int(1_000_000 / fps)
        self._current_pts = 0

    async def read_frame(self) -> VideoFrame:
        await asyncio.sleep(self._interval_s)
        self._current_pts += self._interval_us
        return VideoFrame(pts_us=self._current_pts, payload=b"raw_frame")

    async def close(self) -> None:
        return None


class PyAVMediaSource:
    """Live media source backed by PyAV/FFmpeg."""

    def __init__(
        self,
        input_url: str,
        fallback_fps: int = 30,
        max_frame_width: int | None = None,
        max_frame_height: int | None = None,
    ) -> None:
        if av is None:
            raise RuntimeError(
                "PyAV is not installed. Install optional deps with "
                "`pip install -r requirements.media.txt` to use live inputs."
            )

        self._input_url = input_url
        self._fallback_interval_us = int(1_000_000 / max(fallback_fps, 1))
        self._max_frame_width = int(max_frame_width or 0)
        self._max_frame_height = int(max_frame_height or 0)
        self._container = None
        self._video_stream = None
        self._frame_iterator = None
        self._last_pts_us: int | None = None
        self._base_pts_us: int | None = None
        self._pts_debug_sample_count = 0
        self._closed = False

    async def read_frame(self) -> VideoFrame | None:
        if self._closed:
            return None
        return await asyncio.to_thread(self._read_frame_blocking)

    async def close(self) -> None:
        self._closed = True
        await asyncio.to_thread(self._close_blocking)

    def _read_frame_blocking(self) -> VideoFrame:
        self._ensure_opened()

        while True:
            try:
                frame = next(self._frame_iterator)
            except StopIteration as exc:
                self._close_blocking()
                raise RuntimeError(f"input stream ended: {self._input_url}") from exc
            except Exception as exc:  # pragma: no cover
                self._close_blocking()
                raise RuntimeError(f"failed to decode frame: {self._input_url}") from exc

            if frame is None:
                continue
            if frame.pts is None and frame.time is None:
                continue

            pts_us = self._resolve_pts_us(frame)
            frame = self._resize_frame_if_needed(frame)
            rgb_frame = frame.to_ndarray(format="rgb24")
            return VideoFrame(
                pts_us=pts_us,
                payload=rgb_frame.tobytes(),
                width=int(rgb_frame.shape[1]),
                height=int(rgb_frame.shape[0]),
                pixel_format="rgb24",
            )

    def _ensure_opened(self) -> None:
        if self._container is not None and self._frame_iterator is not None:
            return

        self._container = av.open(
            self._input_url,
            mode="r",
            options=self._build_open_options(),
        )
        self._video_stream = next(
            (stream for stream in self._container.streams if stream.type == "video"),
            None,
        )
        if self._video_stream is None:
            self._close_blocking()
            raise RuntimeError(f"no video stream found: {self._input_url}")

        try:
            self._video_stream.thread_type = "AUTO"
        except Exception:
            logger.debug("pyav_source_thread_type_unsupported input_url=%s", self._input_url)
        self._frame_iterator = self._container.decode(video=0)
        logger.info("pyav_source_opened input_url=%s", self._input_url)

    def _build_open_options(self) -> dict[str, str]:
        if not self._input_url.startswith("rtsp://"):
            return {}
        return {
            "rtsp_transport": "tcp",
            "fflags": "nobuffer",
            "flags": "low_delay",
            "analyzeduration": "0",
            "probesize": "32768",
            "max_delay": "0",
        }

    def _resize_frame_if_needed(self, frame):
        target_width, target_height = self._resolve_target_size(
            int(frame.width),
            int(frame.height),
        )
        if target_width == int(frame.width) and target_height == int(frame.height):
            return frame.reformat(format="rgb24")
        return frame.reformat(width=target_width, height=target_height, format="rgb24")

    def _resolve_target_size(self, width: int, height: int) -> tuple[int, int]:
        if self._max_frame_width <= 0 and self._max_frame_height <= 0:
            return width, height
        width_ratio = self._max_frame_width / width if self._max_frame_width > 0 else 1.0
        height_ratio = self._max_frame_height / height if self._max_frame_height > 0 else 1.0
        scale = min(width_ratio, height_ratio, 1.0)
        target_width = self._to_even_dimension(width * scale)
        target_height = self._to_even_dimension(height * scale)
        return max(target_width, 2), max(target_height, 2)

    def _to_even_dimension(self, value: float) -> int:
        dimension = int(value)
        if dimension % 2 == 1:
            dimension -= 1
        return max(dimension, 2)

    def _resolve_pts_us(self, frame) -> int:
        raw_pts_us: int
        pts_source: str
        time_base = None
        if frame.pts is not None:
            time_base = frame.time_base or getattr(self._video_stream, "time_base", None)
            if time_base:
                raw_pts_us = int(frame.pts * float(time_base) * 1_000_000)
                pts_source = "frame_pts_time_base"
            else:
                raw_pts_us = int(frame.pts * self._fallback_interval_us)
                pts_source = "frame_pts_fallback_interval"
        elif frame.time is not None:
            raw_pts_us = int(frame.time * 1_000_000)
            pts_source = "frame_time"
        else:
            raw_pts_us = int(time.monotonic() * 1_000_000)
            pts_source = "monotonic"

        if self._base_pts_us is None:
            self._base_pts_us = raw_pts_us

        pts_us = max(0, raw_pts_us - self._base_pts_us)

        if self._last_pts_us is not None and pts_us <= self._last_pts_us:
            pts_us = self._last_pts_us + max(self._fallback_interval_us, 1)

        if self._pts_debug_sample_count < 5:
            logger.info(
                "pyav_pts_sample input_url=%s source=%s frame_pts=%s frame_time=%s "
                "time_base=%s stream_time_base=%s raw_us=%s base_us=%s resolved_us=%s",
                self._input_url,
                pts_source,
                frame.pts,
                frame.time,
                time_base,
                getattr(self._video_stream, "time_base", None),
                raw_pts_us,
                self._base_pts_us,
                pts_us,
            )
            self._pts_debug_sample_count += 1

        self._last_pts_us = pts_us
        return pts_us

    def _close_blocking(self) -> None:
        if self._container is not None:
            try:
                self._container.close()
            except Exception:  # pragma: no cover
                logger.warning("pyav_source_close_failed input_url=%s", self._input_url)
        self._container = None
        self._video_stream = None
        self._frame_iterator = None


def create_media_source(
    input_url: str,
    fps: int,
    max_frame_width: int | None = None,
    max_frame_height: int | None = None,
) -> MediaSource:
    if input_url.startswith("dummy://"):
        return DummyMediaSource(fps=fps)
    return PyAVMediaSource(
        input_url=input_url,
        fallback_fps=fps,
        max_frame_width=max_frame_width,
        max_frame_height=max_frame_height,
    )
