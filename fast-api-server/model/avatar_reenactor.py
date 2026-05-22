from __future__ import annotations

import importlib
import random
import sys
from collections.abc import Mapping, Sequence
from dataclasses import dataclass
from pathlib import Path
from typing import Any


@dataclass(slots=True)
class _AvatarModules:
    np: Any
    bbox_area: Any
    build_single_face_plan: Any
    build_warped_keyframe: Any
    composite_face: Any
    discover_avatar_bank_entries: Any
    load_avatar_profile_by_id: Any


class LiveAvatarReenactor:
    """Frame-by-frame adapter around the vendored focus-avatar reenact runtime."""

    def __init__(
        self,
        *,
        avatar_project_dir: str | Path | None = None,
        avatar_bank_dir: str | Path | Sequence[str | Path] | None = None,
        random_seed: int = 0,
    ) -> None:
        repo_root = Path(__file__).resolve().parents[1]
        self._project_dir = self._resolve_path(
            avatar_project_dir,
            default=repo_root / "focus-avatar" / "project",
            base=repo_root,
        )
        self._avatar_bank_dirs = self._resolve_bank_dirs(avatar_bank_dir, repo_root)
        self._random_seed = int(random_seed)

        self._modules: _AvatarModules | None = None
        self._avatar_profile_paths_by_id: dict[str, str] = {}
        self._avatar_ids: list[str] = []
        self._avatar_profile_cache: dict[str, dict[str, Any]] = {}
        self._avatar_view_cache: dict[str, dict[str, Any]] = {}
        self._avatar_assignment_by_face: dict[str, str] = {}
        self._avatar_rng = random.Random(self._random_seed)
        self._active_avatar_id: str | None = None

        self._smooth_state: dict[int, Any] = {}
        self._untracked_face_centers: dict[str, Any] = {}
        self._frame_index = 0

        self._mean_face: Any = None
        self._shape_basis: Any = None
        self._blendshape_basis: Any = None

    def _resolve_path(
        self,
        raw_path: str | Path | None,
        *,
        default: Path,
        base: Path,
    ) -> Path:
        path = Path(raw_path) if raw_path else default
        if not path.is_absolute():
            path = base / path
        return path.resolve()

    def _resolve_bank_dirs(
        self,
        raw_paths: str | Path | Sequence[str | Path] | None,
        repo_root: Path,
    ) -> list[Path]:
        if raw_paths is None:
            raw_values: list[str | Path | None] = [self._project_dir / "avatar_bank"]
        elif isinstance(raw_paths, (str, Path)):
            raw_values = [raw_paths]
        else:
            raw_values = list(raw_paths)

        resolved_dirs: list[Path] = []
        for raw_value in raw_values:
            path = self._resolve_path(
                raw_value,
                default=self._project_dir / "avatar_bank",
                base=repo_root,
            )
            if path not in resolved_dirs:
                resolved_dirs.append(path)
        return resolved_dirs

    def render_frame(
        self,
        frame_bgr: Any,
        face_metadata: Mapping[str, Any] | None,
        avatar_id: str | None,
    ) -> Any:
        try:
            selected_faces = self._normalize_faces(
                face_metadata,
                require_face_avatar=avatar_id is None,
            )
            if not selected_faces:
                return frame_bgr

            modules = self._ensure_ready()
            requested_avatar_ids = sorted(
                {
                    str(selected["avatar_id"])
                    for selected in selected_faces
                    if selected.get("avatar_id") is not None
                }
            )
            avatar_ids = self._resolve_avatar_ids(avatar_id, requested_avatar_ids)
            selected_faces = sorted(selected_faces, key=modules.bbox_area, reverse=True)
            used_untracked_face_keys: set[str] = set()
            rendered_bgr = frame_bgr

            for slot_index, selected in enumerate(selected_faces):
                plan = modules.build_single_face_plan(
                    frame_index=self._frame_index,
                    selected=selected,
                    slot_index=slot_index,
                    smooth_state=self._smooth_state,
                    untracked_face_centers=self._untracked_face_centers,
                    used_untracked_face_keys=used_untracked_face_keys,
                    avatar_ids=avatar_ids,
                    load_avatar_profile_for_id=self._load_avatar_profile_for_id,
                    avatar_assignment_by_face=self._avatar_assignment_by_face,
                    avatar_rng=self._avatar_rng,
                )
                if plan is None:
                    continue

                warped = modules.build_warped_keyframe(
                    plan=plan,
                    mean_face=self._mean_face,
                    shape_basis=self._shape_basis,
                    blendshape_basis=self._blendshape_basis,
                    load_avatar_profile_for_id=self._load_avatar_profile_for_id,
                    avatar_view_cache=self._avatar_view_cache,
                    gpen_keyframe_restorer=None,
                    should_restore_keyframe=False,
                    key_restorer_mask_expand_px=-1,
                    key_restorer_feather_px=8,
                )
                rendered_bgr = modules.composite_face(
                    rendered_bgr,
                    warped.face_bgr,
                    plan.bbox_xyxy,
                    warped.crop_points,
                    face_mask_override=warped.mask_uint8,
                )
            return rendered_bgr
        finally:
            self._frame_index += 1

    def _ensure_ready(self) -> _AvatarModules:
        modules = self._ensure_modules()
        if not self._avatar_profile_paths_by_id:
            self._refresh_avatar_profile_paths()
            if not self._avatar_ids:
                raise RuntimeError(
                    f"No avatar profiles were found under avatar banks: {self._avatar_bank_dirs}"
                )

        if self._mean_face is None:
            asset_dir = self._project_dir / "shared" / "facemap_assets"
            self._mean_face = modules.np.load(asset_dir / "meanFace.npy", allow_pickle=False)
            self._shape_basis = modules.np.load(asset_dir / "shapeBasis.npy", allow_pickle=False)
            self._blendshape_basis = modules.np.load(asset_dir / "blendShape.npy", allow_pickle=False)
        return modules

    def _ensure_modules(self) -> _AvatarModules:
        if self._modules is not None:
            return self._modules
        if not self._project_dir.exists():
            raise RuntimeError(f"Avatar project directory was not found: {self._project_dir}")

        project_path = str(self._project_dir)
        if project_path not in sys.path:
            sys.path.insert(0, project_path)

        np = importlib.import_module("numpy")
        assets_runtime = importlib.import_module("reenactment.reenact_assets_runtime")
        composite = importlib.import_module("reenactment.reenact_composite")
        face_planner = importlib.import_module("reenactment.reenact_face_planner")
        keyframe_cache = importlib.import_module("reenactment.reenact_keyframe_cache")

        self._modules = _AvatarModules(
            np=np,
            bbox_area=face_planner.bbox_area,
            build_single_face_plan=keyframe_cache.build_single_face_plan,
            build_warped_keyframe=keyframe_cache.build_warped_keyframe,
            composite_face=composite.composite_face,
            discover_avatar_bank_entries=assets_runtime.discover_avatar_bank_entries,
            load_avatar_profile_by_id=assets_runtime.load_avatar_profile_by_id,
        )
        return self._modules

    def _refresh_avatar_profile_paths(self) -> None:
        modules = self._ensure_modules()
        self._avatar_profile_paths_by_id = modules.discover_avatar_bank_entries(self._avatar_bank_dirs)
        self._avatar_ids = sorted(self._avatar_profile_paths_by_id.keys())

    def _resolve_avatar_ids(self, avatar_id: str | None, requested_avatar_ids: list[str]) -> list[str]:
        if not avatar_id:
            for requested_avatar_id in requested_avatar_ids:
                if requested_avatar_id not in self._avatar_profile_paths_by_id:
                    self._refresh_avatar_profile_paths()
                    break
            return requested_avatar_ids or list(self._avatar_ids)

        requested = str(avatar_id)
        if requested not in self._avatar_profile_paths_by_id:
            self._refresh_avatar_profile_paths()
        if requested not in self._avatar_profile_paths_by_id:
            raise RuntimeError(f"Unknown avatar_id '{requested}' in avatar bank.")

        if requested != self._active_avatar_id:
            self._active_avatar_id = requested
            self._avatar_assignment_by_face.clear()
            self._avatar_rng = random.Random(self._random_seed)
        return [requested]

    def _load_avatar_profile_for_id(self, avatar_id: str) -> dict[str, Any]:
        cached_profile = self._avatar_profile_cache.get(avatar_id)
        if cached_profile is not None:
            return cached_profile
        modules = self._ensure_modules()
        profile = modules.load_avatar_profile_by_id(self._avatar_profile_paths_by_id, avatar_id)
        self._avatar_profile_cache[avatar_id] = profile
        return profile

    def _normalize_faces(
        self,
        face_metadata: Mapping[str, Any] | None,
        require_face_avatar: bool,
    ) -> list[dict[str, Any]]:
        if not isinstance(face_metadata, Mapping):
            return []

        raw_faces = face_metadata.get("faces")
        if raw_faces is None and face_metadata.get("bbox") is not None:
            raw_faces = [face_metadata]
        if not isinstance(raw_faces, Sequence) or isinstance(raw_faces, (str, bytes)):
            return []

        normalized_faces: list[dict[str, Any]] = []
        for raw_face in raw_faces:
            if not isinstance(raw_face, Mapping):
                continue
            bbox = self._normalize_bbox(raw_face.get("bbox"))
            coeffs = self._extract_coeffs(raw_face)
            if bbox is None or coeffs is None:
                continue

            tracking_id = raw_face.get("tracking_id", raw_face.get("trackingId"))
            face_avatar_id = raw_face.get("avatar_id", raw_face.get("avatarId"))
            if require_face_avatar and not face_avatar_id:
                continue

            normalized_face = {
                "tracking_id": tracking_id,
                "bbox": bbox,
                "tdmm_raw": {"coeffs": coeffs},
            }
            if face_avatar_id:
                normalized_face["avatar_id"] = str(face_avatar_id)
            normalized_faces.append(normalized_face)
        return normalized_faces

    def _normalize_bbox(self, raw_bbox: Any) -> dict[str, float] | None:
        if isinstance(raw_bbox, Mapping):
            if {"x", "y", "width", "height"}.issubset(raw_bbox.keys()):
                return {
                    "x": float(raw_bbox["x"]),
                    "y": float(raw_bbox["y"]),
                    "width": float(raw_bbox["width"]),
                    "height": float(raw_bbox["height"]),
                }
            if {"left", "top", "right", "bottom"}.issubset(raw_bbox.keys()):
                left = float(raw_bbox["left"])
                top = float(raw_bbox["top"])
                return {
                    "x": left,
                    "y": top,
                    "width": float(raw_bbox["right"]) - left,
                    "height": float(raw_bbox["bottom"]) - top,
                }

        if isinstance(raw_bbox, Sequence) and not isinstance(raw_bbox, (str, bytes)):
            values = list(raw_bbox)
            if len(values) >= 4:
                return {
                    "x": float(values[0]),
                    "y": float(values[1]),
                    "width": float(values[2]),
                    "height": float(values[3]),
                }
        return None

    def _extract_coeffs(self, raw_face: Mapping[str, Any]) -> Sequence[float] | None:
        tdmm = raw_face.get("tdmm_raw")
        if not isinstance(tdmm, Mapping):
            tdmm = raw_face.get("tdmmRaw")
        if isinstance(tdmm, Mapping):
            coeffs = tdmm.get("coeffs")
            if coeffs is not None:
                return coeffs

        coeffs = raw_face.get("coeffs")
        if coeffs is not None:
            return coeffs
        return None
