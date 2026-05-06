import asyncio
import json
import logging
import os
from pathlib import Path

from core.config import Settings

logger = logging.getLogger(__name__)


class AnalysisArchiveService:
    def __init__(self, settings: Settings) -> None:
        self._settings = settings

    def build_analysis_path(self, broadcast_id: str) -> str:
        return str(
            Path(self._settings.hls_output_root)
            / broadcast_id
            / "archive"
            / self._settings.analysis_output_filename
        )

    async def ensure_analysis_mp4(self, broadcast_id: str, hls_path: str, analysis_path: str) -> str:
        if self._has_non_empty_file(analysis_path):
            logger.info("analysis_mp4_found broadcast_id=%s path=%s", broadcast_id, analysis_path)
            return analysis_path

        if not self._has_non_empty_file(hls_path):
            raise RuntimeError(f"HLS playlist not found for analysis. path={hls_path}")

        logger.info("analysis_mp4_remux_started broadcast_id=%s hls=%s", broadcast_id, hls_path)
        Path(analysis_path).parent.mkdir(parents=True, exist_ok=True)
        cmd = [
            "ffmpeg",
            "-y",
            "-loglevel",
            self._settings.ffmpeg_log_level,
            "-i",
            hls_path,
            "-map",
            "0:v:0",
            "-c:v",
            "copy",
            "-movflags",
            "+faststart",
            analysis_path,
        ]
        await self._run_checked(cmd, timeout=self._settings.analysis_ffmpeg_timeout_sec)
        if not self._has_non_empty_file(analysis_path):
            raise RuntimeError(f"analysis.mp4 was not created. path={analysis_path}")
        return analysis_path

    async def probe_duration_sec(self, video_path: str) -> int:
        cmd = [
            "ffprobe",
            "-v",
            "error",
            "-show_entries",
            "format=duration",
            "-of",
            "json",
            video_path,
        ]
        output = await self._run_checked(cmd, timeout=30)
        try:
            payload = json.loads(output.decode("utf-8"))
            return max(int(float(payload["format"]["duration"])), 0)
        except Exception as exc:
            raise RuntimeError(f"failed to probe video duration. path={video_path}") from exc

    async def _run_checked(self, cmd: list[str], timeout: float) -> bytes:
        proc = await asyncio.create_subprocess_exec(
            *cmd,
            stdout=asyncio.subprocess.PIPE,
            stderr=asyncio.subprocess.PIPE,
        )
        try:
            stdout, stderr = await asyncio.wait_for(proc.communicate(), timeout=timeout)
        except asyncio.TimeoutError as exc:
            proc.kill()
            await proc.wait()
            raise RuntimeError(f"command timed out: {cmd[0]}") from exc

        if proc.returncode != 0:
            message = stderr.decode(errors="replace")[-1000:]
            raise RuntimeError(f"{cmd[0]} failed with code {proc.returncode}: {message}")
        return stdout

    @staticmethod
    def _has_non_empty_file(path: str) -> bool:
        return os.path.isfile(path) and os.path.getsize(path) > 0
